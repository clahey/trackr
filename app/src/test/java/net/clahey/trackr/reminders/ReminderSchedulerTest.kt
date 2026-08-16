package net.clahey.trackr.reminders

import net.clahey.trackr.FakeTrackrRepository
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.Event
import net.clahey.trackr.domain.Reminder
import net.clahey.trackr.domain.ReminderMode
import net.clahey.trackr.domain.ValueType
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

class ReminderSchedulerTest {

    private val zone = ZoneOffset.UTC
    private val allDays = DayOfWeek.entries.toSet()

    private fun scheduler(
        repository: FakeTrackrRepository = FakeTrackrRepository(),
        alarms: FakeAlarmScheduler = FakeAlarmScheduler(),
        notifier: FakeReminderNotifier = FakeReminderNotifier(),
        dataStore: FakePreferencesDataStore = FakePreferencesDataStore(),
    ) = ReminderScheduler(repository, alarms, notifier, dataStore)

    private val lastKnownExactAlarmAvailableKey = booleanPreferencesKey("last_known_exact_alarm_available")

    // Seeds the stored exact-alarm-availability flag to match the fake AlarmManager's current
    // state, so a test can isolate the staleness-buffer logic from the separate REM-SCHED-019
    // upgrade-walk that fires on a false->true transition.
    private suspend fun FakePreferencesDataStore.seedExactAlarmFlag(value: Boolean) {
        edit { it[lastKnownExactAlarmAvailableKey] = value }
    }

    private fun fixedReminder(categoryId: String = "cat1", enabled: Boolean = true, nextFireAt: Instant? = null) = Reminder(
        categoryId = categoryId,
        enabled = enabled,
        mode = ReminderMode.FIXED,
        times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
        windowStart = LocalTime.MIDNIGHT,
        windowEnd = LocalTime.MIDNIGHT,
        occurrencesPerDay = 1,
        daysActive = allDays,
        showCategoryInNotification = false,
        nextFireAt = nextFireAt,
    )

    private fun loggedEvent(id: String, categoryId: String, timestamp: Instant) = Event(
        id = id, categoryId = categoryId, timestamp = timestamp,
        value = null, notes = null, imagePaths = emptyList(), createdAt = timestamp,
    )

    private fun randomReminder(categoryId: String = "cat1", enabled: Boolean = true, nextFireAt: Instant? = null) = Reminder(
        categoryId = categoryId,
        enabled = enabled,
        mode = ReminderMode.RANDOM,
        times = emptyList(),
        windowStart = LocalTime.of(8, 0),
        windowEnd = LocalTime.of(20, 0),
        occurrencesPerDay = 2,
        daysActive = allDays,
        showCategoryInNotification = false,
        nextFireAt = nextFireAt,
    )

    // ---- enableReminder / REM-SCHED-013, REM-SCHED-018 ----

    // The armed instant (20:00) is the first fixed time after `now` (09:00) — it would differ if
    // `after` were anything else, so this pins REM-SCHED-002's `after` = now.
    // @spec REM-SCHED-013, REM-SCHED-002, REM-DATA-005
    @Test fun `enableReminder computes and arms on first enable`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val sched = scheduler(repo, alarms)
        val reminder = fixedReminder(nextFireAt = null)
        repo.setReminders(reminder)

        sched.enableReminder(reminder, now = Instant.parse("2024-01-01T09:00:00Z"), zone = zone)

        val saved = repo.getReminderForCategory("cat1").first()!!
        assertEquals(Instant.parse("2024-01-01T20:00:00Z"), saved.nextFireAt)
        assertEquals(Instant.parse("2024-01-01T20:00:00Z"), alarms.armed["cat1"])
    }

    // A valid stored nextFireAt means "don't recompute", not "an alarm is already pending" — the OS
    // drops pending alarms on force-stop and app update while the row survives, and re-arming is the
    // only thing that heals that.
    // @spec REM-SCHED-013, REM-SCHED-018
    @Test fun `enableReminder re-arms without recomputing when the current RANDOM nextFireAt is still valid`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val sched = scheduler(repo, alarms)
        // sub-windows: [08:00,14:00), [14:00,20:00) — nextFireAt sits in the box containing now
        val armedAt = Instant.parse("2024-01-01T10:00:00Z")
        repo.setReminders(randomReminder(nextFireAt = armedAt))

        sched.enableReminder(randomReminder(), now = Instant.parse("2024-01-01T09:00:00Z"), zone = zone)

        assertEquals(armedAt, repo.getReminderForCategory("cat1").first()!!.nextFireAt)
        assertEquals(armedAt, alarms.armed["cat1"])
    }

    // @spec REM-SCHED-013
    @Test fun `enableReminder re-reads nextFireAt from the repository, not the passed reminder argument`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val sched = scheduler(repo, alarms)
        val armedAt = Instant.parse("2024-01-01T10:00:00Z") // valid: inside now's box
        repo.setReminders(randomReminder(nextFireAt = armedAt))
        // The in-memory argument carries a stale/different nextFireAt — must be ignored.
        val staleArgument = randomReminder(nextFireAt = Instant.parse("2024-01-01T00:00:00Z"))

        sched.enableReminder(staleArgument, now = Instant.parse("2024-01-01T09:00:00Z"), zone = zone)

        // Validating against the stale argument would have failed the box check and recomputed a
        // fresh random instant; arming the repository's own value is what proves it was re-read.
        assertEquals(armedAt, alarms.armed["cat1"])
        assertEquals(armedAt, repo.getReminderForCategory("cat1").first()!!.nextFireAt)
    }

    // @spec REM-SCHED-013, REM-SCHED-015, REM-SCHED-002
    @Test fun `enableReminder recomputes when the stored nextFireAt is no longer valid under an edited config`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val sched = scheduler(repo, alarms)
        // Originally valid under 2 occurrences/day; edited to 4, invalidating the old box.
        val staleNextFireAt = Instant.parse("2024-01-01T16:00:00Z")
        repo.setReminders(randomReminder(nextFireAt = staleNextFireAt))
        val edited = randomReminder().copy(occurrencesPerDay = 4)

        sched.enableReminder(edited, now = Instant.parse("2024-01-01T09:00:00Z"), zone = zone)

        assertTrue("a recompute should have armed a new instant", alarms.armCalls.isNotEmpty())
    }

    // ---- disableReminder / cancel — REM-SCHED-012, REM-SCHED-014 ----

    // @spec REM-SCHED-014, REM-DATA-005
    @Test fun `disableReminder clears nextFireAt, persists enabled false, and cancels the alarm`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val sched = scheduler(repo, alarms)
        repo.setReminders(fixedReminder(nextFireAt = Instant.parse("2024-01-01T08:00:00Z")))
        alarms.arm("cat1", Instant.parse("2024-01-01T08:00:00Z"))

        sched.disableReminder("cat1")

        val saved = repo.getReminderForCategory("cat1").first()!!
        assertFalse(saved.enabled)
        assertNull(saved.nextFireAt)
        assertEquals(listOf("cat1"), alarms.cancelCalls)
    }

    // @spec REM-SCHED-012
    @Test fun `cancel only touches the alarm, not the repository`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val sched = scheduler(repo, alarms)

        sched.cancel("cat1")

        assertEquals(listOf("cat1"), alarms.cancelCalls)
    }

    // ---- onAlarmFired — REM-SCHED-003, REM-SCHED-008, REM-SCHED-011 ----

    // @spec REM-SCHED-003, REM-SCHED-011
    @Test fun `onAlarmFired posts a notification and reschedules from firedAt`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val notifier = FakeReminderNotifier()
        val sched = scheduler(repo, alarms, notifier)
        repo.setReminders(fixedReminder(nextFireAt = Instant.parse("2024-01-01T08:00:00Z")))

        sched.onAlarmFired("cat1", firedAt = Instant.parse("2024-01-01T08:00:00Z"), zone = zone)

        assertEquals(1, notifier.posted.size)
        assertEquals("cat1", notifier.posted[0].categoryId)
        assertEquals(Instant.parse("2024-01-01T20:00:00Z"), repo.getReminderForCategory("cat1").first()!!.nextFireAt)
        assertEquals(Instant.parse("2024-01-01T20:00:00Z"), alarms.armed["cat1"])
    }

    // @spec REM-SCHED-011
    @Test fun `onAlarmFired is a no-op when the reminder no longer exists`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val notifier = FakeReminderNotifier()
        val sched = scheduler(repo, alarms, notifier)

        sched.onAlarmFired("gone", firedAt = Instant.parse("2024-01-01T08:00:00Z"), zone = zone)

        assertTrue(notifier.posted.isEmpty())
        assertTrue(alarms.armCalls.isEmpty())
    }

    // @spec REM-SCHED-020
    @Test fun `onAlarmFired suppresses the notification but still reschedules when logged shortly before firing`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val notifier = FakeReminderNotifier()
        val sched = scheduler(repo, alarms, notifier)
        repo.setReminders(fixedReminder(nextFireAt = Instant.parse("2024-01-01T20:00:00Z")))
        repo.setEvents(loggedEvent("e1", "cat1", Instant.parse("2024-01-01T19:55:00Z")))

        sched.onAlarmFired("cat1", firedAt = Instant.parse("2024-01-01T20:00:00Z"), zone = zone)

        assertTrue("notification should have been suppressed", notifier.posted.isEmpty())
        assertEquals(Instant.parse("2024-01-02T08:00:00Z"), repo.getReminderForCategory("cat1").first()!!.nextFireAt)
        assertEquals(Instant.parse("2024-01-02T08:00:00Z"), alarms.armed["cat1"])
    }

    // @spec REM-SCHED-020
    @Test fun `onAlarmFired sizes suppression from the stored nextFireAt, not the delivery instant`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val notifier = FakeReminderNotifier()
        val sched = scheduler(repo, alarms, notifier)
        repo.setReminders(fixedReminder(nextFireAt = Instant.parse("2024-01-01T20:00:00Z")))
        repo.setEvents(loggedEvent("e1", "cat1", Instant.parse("2024-01-01T19:55:00Z")))

        // Delivered 400ms late, as every real alarm is. The scheduler must pivot the backward walk on the
        // armed instant it reads off the row, not on when the broadcast happened to arrive.
        sched.onAlarmFired("cat1", firedAt = Instant.parse("2024-01-01T20:00:00.400Z"), zone = zone)

        assertTrue("notification should have been suppressed", notifier.posted.isEmpty())
    }

    // @spec REM-SCHED-020
    @Test fun `onAlarmFired still posts the notification when the log is outside the lookback window`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val notifier = FakeReminderNotifier()
        val sched = scheduler(repo, alarms, notifier)
        repo.setReminders(fixedReminder(nextFireAt = Instant.parse("2024-01-01T20:00:00Z")))
        repo.setEvents(loggedEvent("e1", "cat1", Instant.parse("2024-01-01T08:05:00Z")))

        sched.onAlarmFired("cat1", firedAt = Instant.parse("2024-01-01T20:00:00Z"), zone = zone)

        assertEquals(1, notifier.posted.size)
    }

    // @spec REM-SCHED-020
    @Test fun `onAlarmFired never suppresses a RANDOM reminder regardless of a recent log`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val notifier = FakeReminderNotifier()
        val sched = scheduler(repo, alarms, notifier)
        repo.setReminders(randomReminder(nextFireAt = Instant.parse("2024-01-01T14:00:00Z")))
        repo.setEvents(loggedEvent("e1", "cat1", Instant.parse("2024-01-01T13:59:00Z")))

        sched.onAlarmFired("cat1", firedAt = Instant.parse("2024-01-01T14:00:00Z"), zone = zone)

        assertEquals(1, notifier.posted.size)
    }

    // A reminder on a MetaCategory whose logging all happens in its SubCategories would otherwise
    // never suppress — the parent row has no events of its own. The child carries its own
    // valueType, which also pins the all-children query rather than the inheriting-children one.
    // @spec REM-SCHED-020, LS-BE-014
    @Test fun `onAlarmFired suppresses on an event logged under a SubCategory`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val notifier = FakeReminderNotifier()
        val sched = scheduler(repo, alarms, notifier)
        val parent = Category.MetaCategory(
            id = "cat1", name = "Exercise", emoji = "🏃", color = 0xFF0000FFL,
            valueType = ValueType.None, defaultValue = null, allowEmptyText = true, sortOrder = 0,
        )
        repo.setCategories(
            parent,
            Category.SubCategory(
                id = "child", name = "Run", emoji = null, color = null, valueType = ValueType.None,
                defaultValue = null, allowEmptyText = true, sortOrder = 0, parent = parent,
            ),
        )
        repo.setReminders(fixedReminder(nextFireAt = Instant.parse("2024-01-01T20:00:00Z")))
        repo.setEvents(loggedEvent("e1", "child", Instant.parse("2024-01-01T19:55:00Z")))

        sched.onAlarmFired("cat1", firedAt = Instant.parse("2024-01-01T20:00:00Z"), zone = zone)

        assertTrue("logging under a child should suppress the parent's reminder", notifier.posted.isEmpty())
    }

    // @spec REM-SCHED-011
    @Test fun `onAlarmFired is a no-op when the reminder has since been disabled`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val notifier = FakeReminderNotifier()
        val sched = scheduler(repo, alarms, notifier)
        repo.setReminders(fixedReminder(enabled = false, nextFireAt = null))

        sched.onAlarmFired("cat1", firedAt = Instant.parse("2024-01-01T08:00:00Z"), zone = zone)

        assertTrue(notifier.posted.isEmpty())
        assertTrue(alarms.armCalls.isEmpty())
    }

    // ---- rearmAll — REM-SCHED-004, REM-SCHED-016 ----

    // @spec REM-SCHED-004, REM-SCHED-016, REM-SCHED-018
    @Test fun `rearmAll re-arms without recomputing when the stored nextFireAt is still valid`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val sched = scheduler(repo, alarms)
        // sub-windows: [08:00,14:00), [14:00,20:00) — nextFireAt sits in the box containing now
        val validNextFireAt = Instant.parse("2024-01-01T10:00:00Z")
        repo.setReminders(randomReminder(nextFireAt = validNextFireAt))

        sched.rearmAll(now = Instant.parse("2024-01-01T09:00:00Z"), zone = zone)

        assertEquals(1, alarms.armCalls.size)
        assertEquals(validNextFireAt, alarms.armed["cat1"])
        assertEquals(validNextFireAt, repo.getReminderForCategory("cat1").first()!!.nextFireAt)
    }

    // @spec REM-SCHED-004, REM-SCHED-018
    @Test fun `rearmAll does not skip a still-pending occurrence on a routine daytime reboot`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val sched = scheduler(repo, alarms)
        // sub-windows: [08:00,14:00), [14:00,20:00) — today's draw (10:00) is still pending, in
        // box0, which also contains now (09:00). Recomputing unconditionally would look for the
        // earliest box starting *after* 09:00 — box0 starts at 08:00, so it wouldn't qualify, and
        // this would wrongly jump to box1 and skip the still-pending 10:00 occurrence entirely.
        val pendingNextFireAt = Instant.parse("2024-01-01T10:00:00Z")
        repo.setReminders(randomReminder(nextFireAt = pendingNextFireAt))

        sched.rearmAll(now = Instant.parse("2024-01-01T09:00:00Z"), zone = zone)

        assertEquals(pendingNextFireAt, alarms.armed["cat1"])
    }

    // @spec REM-SCHED-004, REM-SCHED-018
    @Test fun `rearmAll recomputes when the stored nextFireAt is no longer valid`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val sched = scheduler(repo, alarms)
        // A stale nextFireAt from a prior day is outside both today's current and next box.
        val staleNextFireAt = Instant.parse("2023-12-31T10:00:00Z")
        repo.setReminders(randomReminder(nextFireAt = staleNextFireAt))
        val now = Instant.parse("2024-01-01T09:00:00Z")

        sched.rearmAll(now = now, zone = zone)

        val rearmed = alarms.armed["cat1"]!!
        assertTrue("should have recomputed to a fresh, future instant", rearmed.isAfter(now))
    }

    // @spec REM-SCHED-016
    @Test fun `rearmAll does not touch disabled reminders`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val sched = scheduler(repo, alarms)
        repo.setReminders(fixedReminder(enabled = false, nextFireAt = null))

        sched.rearmAll(now = Instant.parse("2024-01-01T09:00:00Z"), zone = zone)

        assertTrue(alarms.armCalls.isEmpty())
    }

    // ---- reconcileOnStartup — REM-SCHED-005, REM-SCHED-017, REM-SCHED-019 ----

    // @spec REM-SCHED-017
    @Test fun `reconcileOnStartup leaves a reminder untouched when nextFireAt is still in the future`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val dataStore = FakePreferencesDataStore().apply { seedExactAlarmFlag(alarms.canScheduleExact()) }
        val sched = scheduler(repo, alarms, dataStore = dataStore)
        val future = Instant.parse("2024-01-01T20:00:00Z")
        repo.setReminders(fixedReminder(nextFireAt = future))

        sched.reconcileOnStartup(now = Instant.parse("2024-01-01T09:00:00Z"), zone = zone)

        assertTrue(alarms.armCalls.isEmpty())
    }

    // @spec REM-SCHED-017
    @Test fun `reconcileOnStartup re-arms a reminder whose nextFireAt is null`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler()
        val sched = scheduler(repo, alarms)
        repo.setReminders(fixedReminder(nextFireAt = null))

        sched.reconcileOnStartup(now = Instant.parse("2024-01-01T09:00:00Z"), zone = zone)

        assertEquals(1, alarms.armCalls.size)
    }

    // @spec REM-SCHED-017
    @Test fun `reconcileOnStartup leaves a reminder past nextFireAt untouched when still within the exact-mode buffer`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler(exactAvailable = true)
        val dataStore = FakePreferencesDataStore().apply { seedExactAlarmFlag(true) }
        val sched = scheduler(repo, alarms, dataStore = dataStore)
        val now = Instant.parse("2024-01-01T09:00:00Z")
        repo.setReminders(fixedReminder(nextFireAt = now.minusSeconds(5 * 60))) // 5 min past, buffer is 10 min

        sched.reconcileOnStartup(now = now, zone = zone)

        assertTrue(alarms.armCalls.isEmpty())
    }

    // @spec REM-SCHED-017
    @Test fun `reconcileOnStartup re-arms a reminder past the exact-mode 10-minute buffer`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler(exactAvailable = true)
        val dataStore = FakePreferencesDataStore().apply { seedExactAlarmFlag(true) }
        val sched = scheduler(repo, alarms, dataStore = dataStore)
        val now = Instant.parse("2024-01-01T09:00:00Z")
        repo.setReminders(fixedReminder(nextFireAt = now.minusSeconds(11 * 60))) // 11 min past, buffer is 10 min

        sched.reconcileOnStartup(now = now, zone = zone)

        assertEquals(1, alarms.armCalls.size)
    }

    // @spec REM-SCHED-017
    @Test fun `reconcileOnStartup uses the wider 30-minute buffer when exact alarms are unavailable`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler(exactAvailable = false)
        val sched = scheduler(repo, alarms)
        val now = Instant.parse("2024-01-01T09:00:00Z")
        // 20 minutes past — would be stale under the 10-minute exact buffer, not under 30.
        repo.setReminders(fixedReminder(nextFireAt = now.minusSeconds(20 * 60)))

        sched.reconcileOnStartup(now = now, zone = zone)

        assertTrue(alarms.armCalls.isEmpty())
    }

    // @spec REM-SCHED-019
    @Test fun `reconcileOnStartup upgrades an already-armed reminder to exact on a false-to-true transition`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler(exactAvailable = true) // exact is available now...
        val dataStore = FakePreferencesDataStore() // ...but the stored flag still says false (default)
        val sched = scheduler(repo, alarms, dataStore = dataStore)
        val existingNextFireAt = Instant.parse("2024-01-01T20:00:00Z") // still valid/future
        repo.setReminders(fixedReminder(nextFireAt = existingNextFireAt))

        sched.reconcileOnStartup(now = Instant.parse("2024-01-01T09:00:00Z"), zone = zone)

        // Re-armed at its *existing* instant (no recompute), purely to upgrade to the exact API.
        assertEquals(existingNextFireAt, alarms.armed["cat1"])
    }

    // @spec REM-SCHED-017, REM-SCHED-019
    @Test fun `reconcileOnStartup does not double-arm a stale reminder during an exact-alarm upgrade`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler(exactAvailable = true) // exact just became available...
        val dataStore = FakePreferencesDataStore() // ...but the stored flag still says false (default)
        val sched = scheduler(repo, alarms, dataStore = dataStore)
        val now = Instant.parse("2024-01-01T09:00:00Z")
        // The buffer during this transition is 30 min (wasExactAvailable is false, matching the
        // inexact mode this reminder was actually armed under) — 31 min past is stale under that.
        // The upgrade pass must not also arm this at its stale instant before the staleness pass
        // corrects it.
        repo.setReminders(fixedReminder(nextFireAt = now.minusSeconds(31 * 60)))

        sched.reconcileOnStartup(now = now, zone = zone)

        val callsForCat1 = alarms.armCalls.filter { it.first == "cat1" }
        assertEquals("should arm exactly once, not once at the stale instant then again at the recomputed one", 1, callsForCat1.size)
        assertTrue("the single arm call should use the recomputed instant, not the stale one", callsForCat1[0].second.isAfter(now))
    }

    // @spec REM-SCHED-017
    @Test fun `reconcileOnStartup buffers by the mode alarms were actually armed under, not the just-upgraded one`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler(exactAvailable = true) // exact just became available...
        val dataStore = FakePreferencesDataStore() // ...but the stored flag still says false (default) — armed inexactly
        val sched = scheduler(repo, alarms, dataStore = dataStore)
        val now = Instant.parse("2024-01-01T09:00:00Z")
        // 15 min past: stale under the wrong 10-min (exact) buffer, but not under the correct
        // 30-min (inexact) buffer this reminder was actually armed under.
        val pendingNextFireAt = now.minusSeconds(15 * 60)
        repo.setReminders(fixedReminder(nextFireAt = pendingNextFireAt))

        sched.reconcileOnStartup(now = now, zone = zone)

        // Still fresh under the correct buffer -> re-issued unchanged via the upgrade path, not
        // wrongly discarded and recomputed just because the device can now schedule exactly.
        assertEquals(pendingNextFireAt, alarms.armed["cat1"])
    }

    // @spec REM-SCHED-019
    @Test fun `reconcileOnStartup does not re-walk reminders when the exact-alarm flag hasn't changed`() = runTest {
        val repo = FakeTrackrRepository()
        val alarms = FakeAlarmScheduler(exactAvailable = false)
        val dataStore = FakePreferencesDataStore()
        val sched = scheduler(repo, alarms, dataStore = dataStore)
        val future = Instant.parse("2024-01-01T20:00:00Z")
        repo.setReminders(fixedReminder(nextFireAt = future))

        sched.reconcileOnStartup(now = Instant.parse("2024-01-01T09:00:00Z"), zone = zone)
        sched.reconcileOnStartup(now = Instant.parse("2024-01-01T09:05:00Z"), zone = zone)

        assertTrue(alarms.armCalls.isEmpty())
    }
}

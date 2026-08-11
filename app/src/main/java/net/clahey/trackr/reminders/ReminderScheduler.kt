package net.clahey.trackr.reminders

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import net.clahey.trackr.data.AlarmScheduler
import net.clahey.trackr.data.ReminderNotifier
import net.clahey.trackr.data.TrackrRepository
import net.clahey.trackr.domain.Reminder
import net.clahey.trackr.domain.computeNextFireTime
import net.clahey.trackr.domain.isNextFireAtValid
import net.clahey.trackr.domain.shouldSuppressFixedNotification
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

// @spec REM-SCHED-002, REM-SCHED-003, REM-SCHED-004, REM-SCHED-005, REM-SCHED-008,
// REM-SCHED-011, REM-SCHED-012, REM-SCHED-013, REM-SCHED-014, REM-SCHED-015,
// REM-SCHED-016, REM-SCHED-017, REM-SCHED-018, REM-SCHED-019
@Singleton
class ReminderScheduler @Inject constructor(
    private val repository: TrackrRepository,
    private val alarmScheduler: AlarmScheduler,
    private val notifier: ReminderNotifier,
    private val dataStore: DataStore<Preferences>,
) {
    private val lastKnownExactAlarmAvailableKey = booleanPreferencesKey("last_known_exact_alarm_available")

    // @spec REM-SCHED-002, REM-SCHED-013, REM-SCHED-015, REM-SCHED-018
    suspend fun enableReminder(reminder: Reminder, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        val currentNextFireAt = repository.getReminderForCategory(reminder.categoryId).first()?.nextFireAt
        if (isNextFireAtValid(reminder, currentNextFireAt, now, zone)) return
        val nextFireAt = computeNextFireTime(reminder, now, zone)
        repository.saveReminder(reminder.copy(nextFireAt = nextFireAt))
        alarmScheduler.arm(reminder.categoryId, nextFireAt)
    }

    // @spec REM-SCHED-014
    suspend fun disableReminder(categoryId: String) {
        val reminder = repository.getReminderForCategory(categoryId).first() ?: return
        repository.saveReminder(reminder.copy(enabled = false, nextFireAt = null))
        cancel(categoryId)
    }

    // @spec REM-SCHED-012
    fun cancel(categoryId: String) {
        alarmScheduler.cancel(categoryId)
    }

    // @spec REM-SCHED-003, REM-SCHED-008, REM-SCHED-011, REM-SCHED-020
    suspend fun onAlarmFired(categoryId: String, firedAt: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        val reminder = repository.getReminderForCategory(categoryId).first() ?: return
        if (!reminder.enabled) return
        val latestEventLoggedAt = repository.getEventsByCategory(categoryId).first().maxOfOrNull { it.timestamp }
        if (!shouldSuppressFixedNotification(reminder, firedAt, zone, latestEventLoggedAt)) {
            notifier.postReminderNotification(reminder)
        }
        val nextFireAt = computeNextFireTime(reminder, firedAt, zone)
        repository.saveReminder(reminder.copy(nextFireAt = nextFireAt))
        alarmScheduler.arm(categoryId, nextFireAt)
    }

    // @spec REM-SCHED-004, REM-SCHED-016, REM-SCHED-018
    suspend fun rearmAll(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        repository.getAllEnabledRemindersOnce().forEach { reminder ->
            val nextFireAt = if (isNextFireAtValid(reminder, reminder.nextFireAt, now, zone)) {
                reminder.nextFireAt!!
            } else {
                val recomputed = computeNextFireTime(reminder, now, zone)
                repository.saveReminder(reminder.copy(nextFireAt = recomputed))
                recomputed
            }
            alarmScheduler.arm(reminder.categoryId, nextFireAt)
        }
    }

    // @spec REM-SCHED-005, REM-SCHED-017, REM-SCHED-019
    suspend fun reconcileOnStartup(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        val wasExactAvailable = dataStore.data.map { it[lastKnownExactAlarmAvailableKey] ?: false }.first()
        val isExactAvailable = alarmScheduler.canScheduleExact()
        val reminders = repository.getAllEnabledRemindersOnce()

        // Staleness buffer matches the scheduling mode every currently-armed alarm is actually in
        // (this live read, not a second live check per reminder) so this pass doesn't race an
        // alarm that's still legitimately in flight — see docs/llds/reminders.md § Decisions.
        val bufferMinutes = if (isExactAvailable) 10L else 30L
        val staleBefore = now.minus(Duration.ofMinutes(bufferMinutes))
        val shouldReissueExisting = !wasExactAvailable && isExactAvailable

        reminders.forEach { reminder ->
            val nextFireAt = reminder.nextFireAt
            if (nextFireAt == null || nextFireAt.isBefore(staleBefore)) {
                val recomputed = computeNextFireTime(reminder, now, zone)
                repository.saveReminder(reminder.copy(nextFireAt = recomputed))
                alarmScheduler.arm(reminder.categoryId, recomputed)
            } else if (shouldReissueExisting) {
                // Re-issue via the now-available exact API (no recompute) so a reminder armed
                // inexactly before permission was granted doesn't stay degraded forever.
                alarmScheduler.arm(reminder.categoryId, nextFireAt)
            }
        }
        if (wasExactAvailable != isExactAvailable) {
            dataStore.edit { it[lastKnownExactAlarmAvailableKey] = isExactAvailable }
        }
    }
}

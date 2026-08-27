package net.clahey.trackr.reminders

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import net.clahey.trackr.data.AlarmScheduler
import net.clahey.trackr.data.ReminderNotifier
import net.clahey.trackr.data.TrackrRepository
import net.clahey.trackr.domain.Reminder
import net.clahey.trackr.domain.ReminderMode
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
        val storedNextFireAt = repository.getReminderForCategory(reminder.categoryId).first()?.nextFireAt
        // A valid stored nextFireAt means the occurrence doesn't need recomputing, not that an alarm
        // is still pending for it. Force-stop, app update, and OEM task-kill all clear pending alarms
        // while leaving the row intact, so this arms either way; re-arming an alarm that is already
        // pending replaces it rather than stacking.
        val nextFireAt = if (storedNextFireAt != null && isNextFireAtValid(reminder, storedNextFireAt, now, zone)) {
            storedNextFireAt
        } else {
            computeNextFireTime(reminder, now, zone)
        }
        if (nextFireAt != storedNextFireAt) repository.saveReminder(reminder.copy(nextFireAt = nextFireAt))
        alarmScheduler.arm(reminder.categoryId, nextFireAt)
    }

    // @spec REM-SCHED-014
    suspend fun disableReminder(categoryId: String) {
        val reminder = repository.getReminderForCategory(categoryId).first() ?: return
        repository.saveReminder(reminder.copy(enabled = false, nextFireAt = null))
        cancel(categoryId)
    }

    // @spec REM-SCHED-012
    // A notification already posted is not dismissed by anything else, so disabling a reminder or
    // deleting its category would otherwise leave the shade advertising a reminder that no longer
    // exists — and the timeline listing it as outstanding.
    // @spec REM-NOTIF-010
    fun cancel(categoryId: String) {
        alarmScheduler.cancel(categoryId)
        notifier.cancelReminderNotification(categoryId)
    }

    // @spec REM-SCHED-003, REM-SCHED-008, REM-SCHED-011, REM-SCHED-020
    suspend fun onAlarmFired(categoryId: String, firedAt: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        val reminder = repository.getReminderForCategory(categoryId).first() ?: return
        if (!reminder.enabled) return
        // Only FIXED can suppress, and this runs on a Doze wakeup inside the receiver's goAsync()
        // budget — so the read is skipped outright for the mode that would discard it.
        val latestEventLoggedAt = if (reminder.mode == ReminderMode.FIXED) {
            repository.getLatestEventTimestampIncludingChildren(categoryId)
        } else {
            null
        }
        val scheduledAt = reminder.nextFireAt ?: firedAt
        if (!shouldSuppressFixedNotification(reminder, scheduledAt, firedAt, zone, latestEventLoggedAt)) {
            notifier.postReminderNotification(reminder)
        }
        val nextFireAt = computeNextFireTime(reminder, firedAt, zone)
        repository.saveReminder(reminder.copy(nextFireAt = nextFireAt))
        alarmScheduler.arm(categoryId, nextFireAt)
    }

    // @spec REM-SCHED-004, REM-SCHED-016
    suspend fun rearmAll(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        repository.getAllEnabledRemindersOnce().forEach { enableReminder(it, now, zone) }
    }

    // @spec REM-SCHED-021
    fun canScheduleExact(): Boolean = alarmScheduler.canScheduleExact()

    // @spec REM-SCHED-005, REM-SCHED-017, REM-SCHED-019
    suspend fun reconcileOnStartup(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        val wasExactAvailable = dataStore.data.map { it[lastKnownExactAlarmAvailableKey] ?: false }.first()
        val isExactAvailable = alarmScheduler.canScheduleExact()
        val reminders = repository.getAllEnabledRemindersOnce()

        // Staleness buffer matches the scheduling mode every currently-armed alarm was actually
        // armed under, so this pass doesn't judge a reminder from the old regime against the new
        // one's threshold mid-transition.
        val bufferMinutes = if (wasExactAvailable) 10L else 30L
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

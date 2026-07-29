package net.clahey.trackr.data

import net.clahey.trackr.domain.Reminder

// @spec REM-NOTIF-001, REM-NOTIF-002, REM-NOTIF-003, REM-NOTIF-004, REM-NOTIF-005
interface ReminderNotifier {
    suspend fun postReminderNotification(reminder: Reminder)
}

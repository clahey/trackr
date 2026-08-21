package net.clahey.trackr.data

import kotlinx.coroutines.flow.StateFlow
import net.clahey.trackr.domain.OutstandingReminder
import net.clahey.trackr.domain.Reminder

// @spec REM-NOTIF-001, REM-NOTIF-002, REM-NOTIF-003, REM-NOTIF-004, REM-NOTIF-005
interface ReminderNotifier {
    suspend fun postReminderNotification(reminder: Reminder)

    /**
     * Cancels a category's reminder notification, and the group summary with it when no reminder
     * notification is left showing.
     *
     * Every cancellation goes through here so the summary's lifetime is one rule in one place
     * rather than something each call site remembers (REM-NOTIF-011).
     */
    // @spec REM-NOTIF-011
    fun cancelReminderNotification(categoryId: String)

    /**
     * The reminders currently outstanding, updated on every change the app makes and on every
     * dismissal reported by a delete intent (REM-NOTIF-012).
     *
     * Surfaces observe this; none of them decides when to re-read.
     */
    // @spec REM-NOTIF-008, REM-NOTIF-009
    val outstanding: StateFlow<List<OutstandingReminder>>

    /**
     * Re-reads the shade into [outstanding], for changes the app did not make.
     *
     * Called from the delete-intent receiver. It re-reads rather than removing the notification the
     * intent named, so dismissing the group summary settles correctly whether Android fires the
     * children's delete intents or only the summary's.
     */
    // @spec REM-NOTIF-012
    fun refreshOutstanding()
}

package net.clahey.trackr.reminders

import net.clahey.trackr.data.ReminderNotifier
import net.clahey.trackr.domain.Reminder

class FakeReminderNotifier : ReminderNotifier {
    val posted = mutableListOf<Reminder>()

    override suspend fun postReminderNotification(reminder: Reminder) {
        posted += reminder
    }
}

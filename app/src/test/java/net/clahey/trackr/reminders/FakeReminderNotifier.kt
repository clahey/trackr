package net.clahey.trackr.reminders

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.clahey.trackr.data.ReminderNotifier
import net.clahey.trackr.domain.OutstandingReminder
import net.clahey.trackr.domain.Reminder
import net.clahey.trackr.domain.ShowingNotification
import net.clahey.trackr.domain.outstandingReminders
import java.time.Instant

class FakeReminderNotifier : ReminderNotifier {
    val posted = mutableListOf<Reminder>()

    // Modelled, not stubbed: a notification stays showing until something cancels it, so a test
    // can tell "posted then cancelled" from "never posted".
    private val showing = linkedMapOf<String, Instant>()

    private val base: Instant = Instant.parse("2024-01-15T12:00:00Z")
    private var postCount = 0L

    private val _outstanding = MutableStateFlow<List<OutstandingReminder>>(emptyList())
    override val outstanding: StateFlow<List<OutstandingReminder>> = _outstanding.asStateFlow()

    override suspend fun postReminderNotification(reminder: Reminder) {
        posted += reminder
        showing[reminder.categoryId] = base.plusSeconds(postCount++)
        refreshOutstanding()
    }

    override fun cancelReminderNotification(categoryId: String) {
        showing -= categoryId
        refreshOutstanding()
    }

    /** Stands in for a user dismissal arriving via a delete intent. */
    fun dismissFromShade(categoryId: String) {
        showing -= categoryId
        refreshOutstanding()
    }

    override fun refreshOutstanding() {
        // Through the same pure function the real notifier uses, so the double cannot disagree with
        // production about what a set of showing notifications means.
        _outstanding.value = outstandingReminders(
            showing.map { (categoryId, postedAt) ->
                ShowingNotification(tag = categoryId, isGroupSummary = false, postedAt = postedAt)
            },
        )
    }
}

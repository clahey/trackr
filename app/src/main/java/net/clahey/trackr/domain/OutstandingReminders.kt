package net.clahey.trackr.domain

import java.time.Instant

/**
 * One of the app's currently-showing notifications, reduced to what deciding outstanding reminders
 * needs. The shell that reads the notification shade fills these in; everything below is pure.
 */
data class ShowingNotification(
    val tag: String?,
    val isGroupSummary: Boolean,
    val postedAt: Instant,
)

/** A reminder that has fired and not yet been dealt with. */
data class OutstandingReminder(
    val categoryId: String,
    val postedAt: Instant,
)

/**
 * The outstanding reminders implied by what the app currently has showing, most recently posted
 * first.
 *
 * The group summary comes back from the same read as its children and is not itself a reminder.
 * An untagged notification cannot be attributed to a category — nothing this app posts is untagged
 * — so it is dropped rather than rendered as a row naming nothing.
 */
// @spec REM-NOTIF-008
fun outstandingReminders(showing: List<ShowingNotification>): List<OutstandingReminder> =
    showing
        .filterNot { it.isGroupSummary }
        .mapNotNull { notification ->
            notification.tag?.let { OutstandingReminder(categoryId = it, postedAt = notification.postedAt) }
        }
        .sortedByDescending { it.postedAt }

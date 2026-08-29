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
 * Every reminder is posted with a tag, but the shade reports it as nullable, so an untagged
 * notification is dropped rather than rendered as a row naming nothing.
 */
// @spec REM-NOTIF-008
fun outstandingReminders(showing: List<ShowingNotification>): List<OutstandingReminder> =
    showing
        .filterNot { it.isGroupSummary }
        .mapNotNull { notification ->
            notification.tag?.let { OutstandingReminder(categoryId = it, postedAt = notification.postedAt) }
        }
        .sortedByDescending { it.postedAt }

/**
 * The outstanding reminders once the app's own cancellation of [categoryId] is accounted for, given
 * a [showing] that may have been read before the cancellation took effect.
 */
// @spec REM-NOTIF-014
fun outstandingRemindersAfterCancel(
    showing: List<ShowingNotification>,
    categoryId: String,
): List<OutstandingReminder> = outstandingReminders(showing.filterNot { it.tag == categoryId })

/**
 * The outstanding reminders once the app's own post for [categoryId] at [postedAt] is accounted for,
 * given a [showing] that may have been read before the post took effect. A [showing] that already
 * carries the category keeps its own entry, since posting replaces rather than adds.
 */
// @spec REM-NOTIF-014
fun outstandingRemindersAfterPost(
    showing: List<ShowingNotification>,
    categoryId: String,
    postedAt: Instant,
): List<OutstandingReminder> = outstandingReminders(
    if (showing.none { it.tag == categoryId }) {
        showing + ShowingNotification(tag = categoryId, isGroupSummary = false, postedAt = postedAt)
    } else {
        showing
    },
)

package net.clahey.trackr.data.local

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import net.clahey.trackr.MainActivity
import net.clahey.trackr.R
import net.clahey.trackr.data.ReminderNotifier
import net.clahey.trackr.data.TrackrRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.clahey.trackr.domain.OutstandingReminder
import net.clahey.trackr.domain.Reminder
import net.clahey.trackr.domain.ShowingNotification
import net.clahey.trackr.reminders.ReminderDismissReceiver
import net.clahey.trackr.domain.outstandingReminders
import net.clahey.trackr.reminders.ReminderReceiver
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

const val REMINDER_NOTIFICATION_CHANNEL_ID = "reminders"
private const val REMINDER_NOTIFICATION_GROUP = "reminders"

// Identity comes from the tag, so every reminder notification shares one id; the summary takes a
// different one because it is posted under a null tag (REM-NOTIF-007).
private const val REMINDER_NOTIFICATION_ID = 1
private const val SUMMARY_NOTIFICATION_ID = 2

// Stands in for a categoryId in the summary's dismiss Uri; no category can collide with it, since
// category ids are UUIDs.
private const val SUMMARY_DISMISS_KEY = "summary"

// @spec REM-NOTIF-001
fun createReminderNotificationChannel(context: Context, notificationManager: NotificationManager) {
    notificationManager.createNotificationChannel(
        NotificationChannel(
            REMINDER_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ),
    )
}

// @spec REM-NOTIF-002, REM-NOTIF-003, REM-NOTIF-004, REM-NOTIF-005, REM-NOTIF-006
class AndroidReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
    private val repository: TrackrRepository,
) : ReminderNotifier {

    private val _outstanding = MutableStateFlow<List<OutstandingReminder>>(emptyList())

    // @spec REM-NOTIF-008, REM-NOTIF-009
    override val outstanding: StateFlow<List<OutstandingReminder>> = _outstanding.asStateFlow()

    // @spec REM-NOTIF-012
    override fun refreshOutstanding() {
        _outstanding.value = outstandingReminders(showingNotifications())
    }

    override suspend fun postReminderNotification(reminder: Reminder) {
        val category = repository.getCategoryById(reminder.categoryId).first()
        val body = if (reminder.showCategoryInNotification && category != null) {
            "${category.resolvedEmoji} ${category.name}"
        } else {
            context.getString(R.string.reminder_notification_body_generic)
        }

        // Same `data` Uri technique as AndroidAlarmScheduler.buildPendingIntent: PendingIntent
        // matching considers `data` but ignores `extras`, so a per-category Uri (not a hashCode
        // request code) is what keeps one category's tap target from clobbering another's.
        val contentIntent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("trackr://reminder/${reminder.categoryId}"))
            .apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(ReminderReceiver.EXTRA_CATEGORY_ID, reminder.categoryId)
            }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, REMINDER_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(body)
            .setGroup(REMINDER_NOTIFICATION_GROUP)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(dismissPendingIntent(reminder.categoryId))
            .setAutoCancel(true)
            .build()

        // The tag carries per-category identity, so the id can be constant: posting replaces this
        // category's own previous notification and no other's, with none of the collision risk a
        // hashed int id would leave (REM-NOTIF-007).
        notificationManager.notify(reminder.categoryId, REMINDER_NOTIFICATION_ID, notification)
        notificationManager.notify(null, SUMMARY_NOTIFICATION_ID, buildSummary())
        refreshOutstanding()
    }

    // @spec REM-NOTIF-011
    override fun cancelReminderNotification(categoryId: String) {
        notificationManager.cancel(categoryId, REMINDER_NOTIFICATION_ID)
        if (outstandingReminders(showingNotifications()).isEmpty()) {
            notificationManager.cancel(null, SUMMARY_NOTIFICATION_ID)
        }
        refreshOutstanding()
    }

    // @spec REM-NOTIF-008
    private fun showingNotifications(): List<ShowingNotification> =
        notificationManager.activeNotifications.map { active ->
            ShowingNotification(
                tag = active.tag,
                isGroupSummary = active.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                postedAt = Instant.ofEpochMilli(active.postTime),
            )
        }

    // Auto-cancel is off: tapping this opens the timeline's outstanding list, which is built from
    // the very notifications a cancel would remove (REM-NOTIF-004).
    private fun buildSummary(): Notification =
        NotificationCompat.Builder(context, REMINDER_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setGroup(REMINDER_NOTIFICATION_GROUP)
            .setGroupSummary(true)
            .setContentIntent(summaryPendingIntent())
            .setDeleteIntent(dismissPendingIntent(SUMMARY_DISMISS_KEY))
            .setAutoCancel(false)
            .build()

    // Fires when the user dismisses this notification — by swipe or by clearing the shade — and not
    // when the app cancels it or an auto-cancel follows a tap (REM-NOTIF-012). The per-key `data`
    // Uri keeps each notification's dismissal distinct, the same reason the tap intents carry one.
    private fun dismissPendingIntent(key: String): PendingIntent {
        val intent = Intent(context, ReminderDismissReceiver::class.java)
            .setData(Uri.parse("trackr://dismissed/$key"))
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun summaryPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("trackr://reminders"))
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

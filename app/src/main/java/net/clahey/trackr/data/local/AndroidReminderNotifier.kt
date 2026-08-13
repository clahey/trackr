package net.clahey.trackr.data.local

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
import net.clahey.trackr.domain.Reminder
import net.clahey.trackr.reminders.ReminderReceiver
import kotlinx.coroutines.flow.first
import javax.inject.Inject

const val REMINDER_NOTIFICATION_CHANNEL_ID = "reminders"
private const val REMINDER_NOTIFICATION_GROUP = "reminders"

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
            .setAutoCancel(true)
            .build()

        // NotificationManager.notify's id has no Uri-based equivalent to the PendingIntent fix
        // above — it's a plain int, so a hashCode collision between two categories' ids remains a
        // (very unlikely) residual risk: one notification's tray entry could replace the other's.
        notificationManager.notify(reminder.categoryId.hashCode(), notification)
    }
}

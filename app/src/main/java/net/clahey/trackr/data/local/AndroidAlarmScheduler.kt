package net.clahey.trackr.data.local

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import net.clahey.trackr.data.AlarmScheduler
import net.clahey.trackr.reminders.ReminderReceiver
import java.time.Instant
import javax.inject.Inject

// @spec REM-SCHED-009, REM-SCHED-010, REM-SCHED-012
class AndroidAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
) : AlarmScheduler {

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun arm(categoryId: String, fireAt: Instant) {
        val pendingIntent = buildPendingIntent(categoryId)
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt.toEpochMilli(), pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt.toEpochMilli(), pendingIntent)
        }
    }

    override fun cancel(categoryId: String) {
        alarmManager.cancel(buildPendingIntent(categoryId))
    }

    // One shared helper for every arm/cancel call site (REM-SCHED-009): the `data` Uri is what
    // makes AlarmManager's Intent.filterEquals-based matching replace, not stack alongside, any
    // alarm already pending for this category.
    private fun buildPendingIntent(categoryId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setData(Uri.parse("trackr://reminder/$categoryId"))
            .putExtra(ReminderReceiver.EXTRA_CATEGORY_ID, categoryId)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

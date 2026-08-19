package net.clahey.trackr.ui.components

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import net.clahey.trackr.R
import net.clahey.trackr.data.local.REMINDER_NOTIFICATION_CHANNEL_ID

/**
 * The one thing worth telling the user about reminder permissions, or absent when nothing is wrong.
 *
 * Only ever one at a time: fixing the reported problem re-evaluates and reports whatever is next.
 */
enum class ReminderPermissionProblem {
    /** Notifications are off for the whole app, so the reminder is never seen. */
    NotificationsDisabled,

    /** The app can notify, but reminders specifically are muted, so the reminder is never seen. */
    ReminderChannelDisabled,

    /** The reminder still arrives, just not necessarily on time. */
    ExactAlarmsUnavailable,
}

/**
 * The two notification problems outrank exact alarms, which affect only when a reminder lands rather
 * than whether it is seen. App-level outranks the channel because unblocking the channel while the
 * app is muted changes nothing.
 */
// @spec REM-PERM-006
fun reminderPermissionProblem(
    notificationsEnabled: Boolean,
    reminderChannelEnabled: Boolean,
    exactAlarmAvailable: Boolean,
): ReminderPermissionProblem? = when {
    !notificationsEnabled -> ReminderPermissionProblem.NotificationsDisabled
    !reminderChannelEnabled -> ReminderPermissionProblem.ReminderChannelDisabled
    !exactAlarmAvailable -> ReminderPermissionProblem.ExactAlarmsUnavailable
    else -> null
}

/** [reminderPermissionProblem] over live permission state, for surfaces inside composition. */
@Composable
fun rememberReminderPermissionProblem(): ReminderPermissionProblem? =
    reminderPermissionProblem(
        notificationsEnabled = rememberNotificationsEnabled(),
        reminderChannelEnabled = rememberReminderChannelEnabled(),
        exactAlarmAvailable = rememberExactAlarmAvailable(),
    )

/** What the list banner and the Reminder section's inline prompt say. */
@StringRes
fun ReminderPermissionProblem.messageRes(): Int = when (this) {
    ReminderPermissionProblem.NotificationsDisabled -> R.string.reminder_problem_notifications
    ReminderPermissionProblem.ReminderChannelDisabled -> R.string.reminder_problem_channel
    ReminderPermissionProblem.ExactAlarmsUnavailable -> R.string.reminder_problem_exact_alarms
}

/** What the save-time confirmation dialog is headed. */
@StringRes
fun ReminderPermissionProblem.dialogTitleRes(): Int = when (this) {
    ReminderPermissionProblem.NotificationsDisabled -> R.string.reminder_problem_notifications_title
    ReminderPermissionProblem.ReminderChannelDisabled -> R.string.reminder_problem_channel_title
    ReminderPermissionProblem.ExactAlarmsUnavailable -> R.string.reminder_problem_exact_alarms_title
}

/** What the save-time confirmation dialog says this reminder will do. */
@StringRes
fun ReminderPermissionProblem.dialogMessageRes(): Int = when (this) {
    // A muted channel and a muted app have the same consequence; only the fix differs, and the
    // dialog's title is what names that.
    ReminderPermissionProblem.NotificationsDisabled,
    ReminderPermissionProblem.ReminderChannelDisabled -> R.string.reminder_problem_notifications_save

    ReminderPermissionProblem.ExactAlarmsUnavailable -> R.string.reminder_problem_exact_alarms_save
}

/** The system settings screen that fixes this problem. */
fun ReminderPermissionProblem.settingsIntent(context: Context): Intent = when (this) {
    ReminderPermissionProblem.NotificationsDisabled ->
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    ReminderPermissionProblem.ReminderChannelDisabled ->
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, REMINDER_NOTIFICATION_CHANNEL_ID)

    ReminderPermissionProblem.ExactAlarmsUnavailable ->
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.parse("package:${context.packageName}"))
}

/**
 * Exact-alarm availability as observable UI state, for surfaces that *display* it.
 *
 * Android broadcasts [AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED] whenever
 * `canScheduleExactAlarms()` flips in either direction, so this updates without the app being
 * resumed or refocused. A lifecycle observer would miss the case that matters most: since Android
 * 10 every visible activity in split-screen or freeform windowing stays RESUMED, so no resume
 * arrives when the user grants the permission in an adjacent Settings pane.
 *
 * Code reading availability at the moment of an action, rather than displaying it, should call
 * `canScheduleExactAlarms()` directly.
 */
// @spec REM-PERM-005
@Composable
fun rememberExactAlarmAvailable(): Boolean {
    // The permission — and the broadcast announcing it — exist only on S and above, so there is
    // nothing to observe below it.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true

    val context = LocalContext.current
    val alarmManager = remember(context) { context.getSystemService(AlarmManager::class.java) }
    var available by remember(alarmManager) { mutableStateOf(alarmManager.canScheduleExactAlarms()) }

    DisposableEffect(context, alarmManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                available = alarmManager.canScheduleExactAlarms()
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Re-read on attach: the permission can change between the initial read above and the
        // receiver being registered.
        available = alarmManager.canScheduleExactAlarms()
        onDispose { context.unregisterReceiver(receiver) }
    }
    return available
}

/**
 * Notification-permission state as observable UI state, for surfaces that *display* it.
 *
 * `POST_NOTIFICATIONS` has no state-change broadcast, so this re-reads whenever the window regains
 * focus — which, unlike an activity resume, does arrive when the user returns from a settings pane
 * beside this one. It is therefore best-effort in a way [rememberExactAlarmAvailable] is not: a
 * change made while this window keeps focus is not observed until focus moves.
 */
// @spec REM-PERM-005
@Composable
fun rememberNotificationsEnabled(): Boolean {
    val context = LocalContext.current
    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    return remember(context, isWindowFocused) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

/**
 * Whether the reminders channel itself is unmuted, as observable UI state.
 *
 * [rememberNotificationsEnabled] cannot see this: blocking a single channel leaves the app enabled,
 * and long-pressing a notification to turn it off is exactly that gesture. Re-read on window focus
 * for the same reason and with the same best-effort caveat.
 */
// @spec REM-PERM-005
@Composable
fun rememberReminderChannelEnabled(): Boolean {
    val context = LocalContext.current
    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    return remember(context, isWindowFocused) { reminderChannelEnabled(context) }
}

/**
 * Point-in-time read of the reminders channel, for code acting on the state rather than showing it.
 *
 * A channel absent from the system counts as enabled: `TrackrApplication` creates it before any of
 * this runs, so its absence would mean something ordered surprisingly, not that a user muted it —
 * and reporting a problem no settings screen can fix would leave the notice up permanently.
 */
fun reminderChannelEnabled(context: Context): Boolean {
    val channel = NotificationManagerCompat.from(context)
        .getNotificationChannel(REMINDER_NOTIFICATION_CHANNEL_ID)
    return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
}

package net.clahey.trackr.ui.components

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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

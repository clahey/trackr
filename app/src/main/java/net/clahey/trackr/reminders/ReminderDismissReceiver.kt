package net.clahey.trackr.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import net.clahey.trackr.data.ReminderNotifier
import javax.inject.Inject

/**
 * Receives a reminder notification's delete intent — the app's only notice that the user dismissed
 * one, rather than acting on it or the app cancelling it.
 *
 * It re-reads the shade rather than removing the notification named by the intent, which is what
 * makes dismissing the collapsed group settle correctly whether Android fires the children's delete
 * intents or only the summary's.
 */
// @spec REM-NOTIF-012
@AndroidEntryPoint
class ReminderDismissReceiver : BroadcastReceiver() {

    @Inject lateinit var notifier: ReminderNotifier

    override fun onReceive(context: Context, intent: Intent) {
        notifier.refreshOutstanding()
    }
}

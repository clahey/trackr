package net.clahey.trackr.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

// @spec REM-SCHED-011
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderScheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val categoryId = intent.getStringExtra(EXTRA_CATEGORY_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                reminderScheduler.onAlarmFired(categoryId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_CATEGORY_ID = "categoryId"
    }
}

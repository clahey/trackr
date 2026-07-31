package net.clahey.trackr

import android.app.Application
import android.app.NotificationManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import net.clahey.trackr.data.TrackrRepository
import net.clahey.trackr.data.local.createReminderNotificationChannel
import net.clahey.trackr.reminders.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

val android.content.Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "trackr_prefs")

// @spec APP-DI-001, APP-PROC-001, APP-PROC-002, REM-NOTIF-001
@HiltAndroidApp
class TrackrApplication : Application() {

    @Inject lateinit var repository: TrackrRepository
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var notificationManager: NotificationManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createReminderNotificationChannel(this, notificationManager)
        // @spec LS-BE-041
        appScope.launch { repository.onStartup() }
        // @spec APP-PROC-002
        appScope.launch { reminderScheduler.reconcileOnStartup() }
    }
}

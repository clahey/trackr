package net.clahey.trackr

import android.app.Application
import android.app.NotificationManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import net.clahey.trackr.data.local.createReminderNotificationChannel
import net.clahey.trackr.di.ApplicationScope
import net.clahey.trackr.reminders.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

val android.content.Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "trackr_prefs")

// @spec APP-DI-001, APP-PROC-002, REM-NOTIF-001
@HiltAndroidApp
class TrackrApplication : Application() {

    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var notificationManager: NotificationManager
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        createReminderNotificationChannel(this, notificationManager)
        // @spec APP-PROC-002
        appScope.launch { reminderScheduler.reconcileOnStartup() }
    }
}

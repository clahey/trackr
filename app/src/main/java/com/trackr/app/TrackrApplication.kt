package com.trackr.app

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.trackr.app.data.TrackrRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

val android.content.Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "trackr_prefs")

// @spec APP-DI-001, APP-PROC-001
@HiltAndroidApp
class TrackrApplication : Application() {

    @Inject lateinit var repository: TrackrRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { repository.onStartup() }
    }
}

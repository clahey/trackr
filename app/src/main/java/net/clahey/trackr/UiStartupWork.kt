package net.clahey.trackr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.clahey.trackr.data.TrackrRepository
import net.clahey.trackr.di.ApplicationScope
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

// @spec APP-PROC-001, APP-PROC-003, LS-BE-041
@Singleton
class UiStartupWork @Inject constructor(
    private val repository: TrackrRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    fun runOnce() {
        if (started.compareAndSet(false, true)) {
            scope.launch { repository.onStartup() }
        }
    }
}

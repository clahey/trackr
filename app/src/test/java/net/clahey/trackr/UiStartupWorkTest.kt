package net.clahey.trackr

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UiStartupWorkTest {

    @Test
    // @spec APP-PROC-001, APP-PROC-003
    fun theFirstCallLaunchesTheScan() = runTest {
        val repository = FakeTrackrRepository()
        val work = UiStartupWork(repository, backgroundScope)

        work.runOnce()
        testScheduler.runCurrent()

        assertEquals(1, repository.onStartupCallCount)
    }

    @Test
    // @spec APP-PROC-003
    fun aLaterCallDoesNotLaunchTheScanAgain() = runTest {
        val repository = FakeTrackrRepository()
        val work = UiStartupWork(repository, backgroundScope)

        work.runOnce()
        testScheduler.runCurrent()
        work.runOnce()
        testScheduler.runCurrent()

        assertEquals(1, repository.onStartupCallCount)
    }

    @Test
    // @spec APP-PROC-003
    fun aCallWhileTheFirstScanIsStillRunningDoesNotLaunchASecond() = runTest {
        val repository = FakeTrackrRepository()
        repository.onStartupGate = CompletableDeferred()
        val work = UiStartupWork(repository, backgroundScope)

        work.runOnce()
        testScheduler.runCurrent()
        work.runOnce()
        testScheduler.runCurrent()

        assertEquals(1, repository.onStartupCallCount)
        repository.onStartupGate?.complete(Unit)
    }
}

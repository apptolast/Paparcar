package com.rndeveloper.paparcar.detection

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.rndeveloper.paparcar.detection.worker.ClearActiveParkingSessionWorker
import com.rndeveloper.paparcar.detection.worker.SaveNewParkingSessionWorker
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * [PARK-RELEASE-THE-SCREEN-NEVER-SAW-001] The remote clear of a parking session runs BEHIND the save
 * that creates its document. As two unrelated unique works, a clear released together with a pending
 * save could run first — NOT_FOUND, swallowed as success — and the save then wrote `isActive=true`,
 * which the next inbound reconcile turned back into an active parking.
 *
 * No worker runs here: the test driver never meets the network constraint, so what is asserted is
 * the ORDER WorkManager was given, which is the whole fix.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkManagerParkingSyncSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerParkingSyncScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerParkingSyncScheduler(context)
    }

    @Test
    fun should_blockTheClearBehindThePendingSave_when_bothTargetTheSameSession() {
        scheduler.enqueueSaveNewParkingSession(session("s-1"), previousSessionId = null)
        scheduler.enqueueClearActiveParkingSession("s-1")

        val chain = workManager
            .getWorkInfosForUniqueWork(WorkManagerParkingSyncScheduler.parkingChainName("s-1"))
            .get()

        assertEquals(WorkInfo.State.ENQUEUED, chain.stateOf(SaveNewParkingSessionWorker.TAG))
        assertEquals(WorkInfo.State.BLOCKED, chain.stateOf(ClearActiveParkingSessionWorker.TAG))
    }

    @Test
    fun should_runTheClearAlone_when_theSessionHasNoPendingSave() {
        scheduler.enqueueClearActiveParkingSession("s-2")

        val chain = workManager
            .getWorkInfosForUniqueWork(WorkManagerParkingSyncScheduler.parkingChainName("s-2"))
            .get()

        assertEquals(1, chain.size)
        assertEquals(WorkInfo.State.ENQUEUED, chain.stateOf(ClearActiveParkingSessionWorker.TAG))
    }

    @Test
    fun should_notHoldTheClear_when_theSaveChainWasCancelled() {
        scheduler.enqueueSaveNewParkingSession(session("s-3"), previousSessionId = null)
        workManager.cancelUniqueWork(WorkManagerParkingSyncScheduler.parkingChainName("s-3")).result.get()

        scheduler.enqueueClearActiveParkingSession("s-3")

        val clear = workManager
            .getWorkInfosForUniqueWork(WorkManagerParkingSyncScheduler.parkingChainName("s-3"))
            .get()
            .single { ClearActiveParkingSessionWorker.TAG in it.tags }
        assertEquals(WorkInfo.State.ENQUEUED, clear.state)
    }

    private fun List<WorkInfo>.stateOf(tag: String): WorkInfo.State = single { tag in it.tags }.state

    private fun session(id: String) = UserParking(
        id = id,
        userId = "user-1",
        location = GpsPoint(40.416775, -3.703790, 8f, 1000L, 0f),
        isActive = true,
    )
}

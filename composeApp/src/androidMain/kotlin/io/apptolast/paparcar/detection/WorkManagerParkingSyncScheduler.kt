package io.apptolast.paparcar.detection

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import io.apptolast.paparcar.detection.worker.ClearActiveParkingSessionWorker
import io.apptolast.paparcar.detection.worker.SaveNewParkingSessionWorker
import io.apptolast.paparcar.detection.worker.UpdateParkingSessionAddressAndPlaceWorker
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.service.ParkingSyncScheduler

/**
 * Android implementation of [ParkingSyncScheduler] backed by WorkManager.
 *
 * All three methods are fully synchronous — no coroutines, no scope. The userId is
 * resolved inside each worker's [doWork] via Koin-injected [AuthRepository] so that
 * the enqueue itself is instantaneous. WorkManager guarantees the job runs even after
 * process death, so resolving the userId at execution time is correct and removes
 * the previous orphaned-scope pattern. [PIPE-001]
 *
 * `ExistingWorkPolicy.REPLACE` makes duplicate enqueues idempotent.
 */
class WorkManagerParkingSyncScheduler(
    private val context: Context,
) : ParkingSyncScheduler {

    override fun enqueueSaveNewParkingSession(session: UserParking, previousSessionId: String?) {
        WorkManager.getInstance(context)
            .beginUniqueWork(
                "parking_chain_${session.id}",
                ExistingWorkPolicy.REPLACE,
                SaveNewParkingSessionWorker.buildRequest(session, previousSessionId),
            )
            .enqueue()
    }

    override fun enqueueClearActiveParkingSession(sessionId: String) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "parking_clear_active_$sessionId",
            ExistingWorkPolicy.REPLACE,
            ClearActiveParkingSessionWorker.buildRequest(sessionId),
        )
    }

    override fun enqueueUpdateParkingSessionAddressAndPlace(sessionId: String, address: AddressInfo?, placeInfo: PlaceInfo?) {
        // Appended to the same chain as SaveNewParkingSessionWorker so it only runs after
        // set() completes — avoids NOT_FOUND on update(). [BUG-WORKER-001]
        // APPEND_OR_REPLACE: if SaveNewParkingSession is FAILED (all retries exhausted),
        // this still runs — partial data is better than none.
        WorkManager.getInstance(context)
            .beginUniqueWork(
                "parking_chain_$sessionId",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                UpdateParkingSessionAddressAndPlaceWorker.buildRequest(sessionId, address, placeInfo),
            )
            .enqueue()
    }
}

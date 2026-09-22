package com.rndeveloper.paparcar.detection

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.rndeveloper.paparcar.detection.worker.ClearActiveParkingSessionWorker
import com.rndeveloper.paparcar.detection.worker.DeleteVehicleRemoteWorker
import com.rndeveloper.paparcar.detection.worker.GeofenceJanitorWorker
import com.rndeveloper.paparcar.detection.worker.SaveNewParkingSessionWorker
import com.rndeveloper.paparcar.detection.worker.UpdateParkingSessionAddressAndPlaceWorker
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.service.ParkingSyncScheduler

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
                parkingChainName(session.id),
                ExistingWorkPolicy.REPLACE,
                SaveNewParkingSessionWorker.buildRequest(session, previousSessionId),
            )
            .enqueue()
    }

    override fun enqueueClearActiveParkingSession(sessionId: String) {
        // Same chain as the save, BEHIND it. It used to be a unique work of its own
        // (`parking_clear_active_<id>`) with no order against `parking_chain_<id>`: when both waited
        // for the network (parked in a garage, Doze) and were released together, the clear's
        // update() could hit a document that did not exist yet — NOT_FOUND is swallowed as success —
        // and the save's set() then wrote the `isActive=true` snapshot it was built with. Once the
        // outbox drainer had cleared `pendingSync`, the next inbound reconcile took that remote row
        // and the released parking came back as active. [PARK-RELEASE-THE-SCREEN-NEVER-SAW-001]
        // APPEND_OR_REPLACE, like the address update below: a finished, failed or cancelled chain
        // does not hold it back — it starts a new one and runs alone.
        WorkManager.getInstance(context)
            .beginUniqueWork(
                parkingChainName(sessionId),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                ClearActiveParkingSessionWorker.buildRequest(sessionId),
            )
            .enqueue()
    }

    override fun enqueueParkingSessionSnapshot(session: UserParking) {
        // El MISMO worker que el alta —su payload es el DTO entero serializado, así que sube el
        // documento completo— pero **APPEND_OR_REPLACE**, no REPLACE.
        //
        // ⛔ La diferencia no es cosmética: con REPLACE esto cancelaría la cadena de la sesión, y en
        // la cadena está el parche del flag que acaba de encolar el cierre (y la actualización de
        // dirección, si venía). O sea que el arreglo de la convergencia se habría llevado por
        // delante el orden que [PARK-RELEASE-THE-SCREEN-NEVER-SAW-001] peleó para conseguir.
        // [SYNC-A-CLOSED-SESSION-MUST-REACH-FIRESTORE-WHOLE-001]
        WorkManager.getInstance(context)
            .beginUniqueWork(
                parkingChainName(session.id),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                SaveNewParkingSessionWorker.buildRequest(session, previousSessionId = null),
            )
            .enqueue()
    }

    override fun enqueueUpdateParkingSessionAddressAndPlace(sessionId: String, address: AddressInfo?, placeInfo: PlaceInfo?) {
        // Appended to the same chain as SaveNewParkingSessionWorker so it only runs after
        // set() completes — avoids NOT_FOUND on update(). [BUG-WORKER-001]
        // APPEND_OR_REPLACE: if SaveNewParkingSession is FAILED (all retries exhausted),
        // this still runs — partial data is better than none.
        WorkManager.getInstance(context)
            .beginUniqueWork(
                parkingChainName(sessionId),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                UpdateParkingSessionAddressAndPlaceWorker.buildRequest(sessionId, address, placeInfo),
            )
            .enqueue()
    }

    override fun enqueueGeofenceRestore() {
        GeofenceJanitorWorker.enqueueOnce(
            WorkManager.getInstance(context),
            trigger = GeofenceJanitorWorker.TRIGGER_POST_SYNC,
        )
    }

    override fun enqueueDeleteVehicleRemote(vehicleId: String) {
        // [SYNC-A-REMOTE-DELETE-HAS-NO-OUTBOX-BEHIND-IT-001] Its own unique name, not the parking
        // chain: nothing may run before a delete, and REPLACE keeps a double-tap idempotent.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "vehicle_delete_$vehicleId",
            ExistingWorkPolicy.REPLACE,
            DeleteVehicleRemoteWorker.buildRequest(vehicleId),
        )
    }

    companion object {
        /** The one ordered lane for every remote write on a parking session document: create/re-save,
         *  address update and active-flag clear run in enqueue order. [PARK-RELEASE-THE-SCREEN-NEVER-SAW-001] */
        fun parkingChainName(sessionId: String): String = "parking_chain_$sessionId"
    }
}

package com.rndeveloper.paparcar.detection

import com.rndeveloper.paparcar.data.sync.SyncOutboxDrainer
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.service.ParkingSyncScheduler
import com.rndeveloper.paparcar.domain.sync.OutboxOp
import com.rndeveloper.paparcar.domain.sync.SyncOutbox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * iOS [ParkingSyncScheduler] over the durable outbox: every enqueue PERSISTS its op first
 * (crash-safe from that instant), then drains — the online happy path keeps today's latency,
 * and a process death mid-flight loses nothing because the retry cadence is the DRAIN cadence
 * (wakes, app-open, BGTask), not an in-memory backoff loop. The delete op is the tombstone that
 * closes the resurrection bug this file used to document as a TODO.
 * [IOS-SYNC-A-QUEUE-THAT-DIES-WITH-THE-PROCESS-001]
 */
class IosParkingSyncScheduler(
    private val outbox: SyncOutbox,
    private val drainer: SyncOutboxDrainer,
) : ParkingSyncScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun enqueueSaveNewParkingSession(session: UserParking, previousSessionId: String?) {
        // Identity, not data: the drainer re-reads the session from Room at SEND time, which also
        // stamps a fresh `updatedAt` for Last-Write-Wins. [SYNC-A-PARKING-MUST-TRAVEL-WHOLE-001]
        persistAndDrain(OutboxOp.SaveSession(session.id, previousSessionId))
    }

    override fun enqueueClearActiveParkingSession(sessionId: String) {
        persistAndDrain(OutboxOp.ClearActive(sessionId))
    }

    override fun enqueueUpdateParkingSessionAddressAndPlace(
        sessionId: String,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
    ) {
        // The payload is already in Room (the enrichment saved it before scheduling this) — the
        // drainer sends Room's truth, so the op needs no serialized address.
        persistAndDrain(OutboxOp.UpdateAddressAndPlace(sessionId))
    }

    override fun enqueueDeleteVehicleRemote(vehicleId: String) {
        // [SYNC-A-REMOTE-DELETE-HAS-NO-OUTBOX-BEHIND-IT-001] THE tombstone: persisted before the
        // first attempt, removed only on success — a kill mid-flight can no longer resurrect the
        // car, because the next drain (next process included) finishes the delete.
        persistAndDrain(OutboxOp.DeleteVehicle(vehicleId))
    }

    private fun persistAndDrain(op: OutboxOp) {
        outbox.persist(op)
        scope.launch { drainer.drain() }
    }
}

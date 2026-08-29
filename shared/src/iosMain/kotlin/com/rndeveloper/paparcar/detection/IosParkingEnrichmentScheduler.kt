package com.rndeveloper.paparcar.detection

import com.rndeveloper.paparcar.data.sync.SyncOutboxDrainer
import com.rndeveloper.paparcar.domain.service.ParkingEnrichmentScheduler
import com.rndeveloper.paparcar.domain.sync.OutboxOp
import com.rndeveloper.paparcar.domain.sync.SyncOutbox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * iOS [ParkingEnrichmentScheduler] over the durable outbox — geocoding that survives process
 * death: the op re-runs on the next drain until an exact (non-approximate) address lands in
 * Room. De-duplication is the outbox's REPLACE key. See [IosParkingSyncScheduler].
 * [IOS-SYNC-A-QUEUE-THAT-DIES-WITH-THE-PROCESS-001]
 */
class IosParkingEnrichmentScheduler(
    private val outbox: SyncOutbox,
    private val drainer: SyncOutboxDrainer,
) : ParkingEnrichmentScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun enqueueEnrichSession(sessionId: String, lat: Double, lon: Double) {
        outbox.persist(OutboxOp.EnrichSession(sessionId, lat, lon))
        scope.launch { drainer.drain() }
    }
}

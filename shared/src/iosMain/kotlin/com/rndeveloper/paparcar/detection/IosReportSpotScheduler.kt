@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.detection

import com.rndeveloper.paparcar.data.sync.SyncOutboxDrainer
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.SpotTtlPolicy
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import com.rndeveloper.paparcar.domain.service.ReportSpotScheduler
import com.rndeveloper.paparcar.domain.sync.OutboxOp
import com.rndeveloper.paparcar.domain.sync.SyncOutbox
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * iOS [ReportSpotScheduler] over the durable outbox. Two lanes on purpose:
 * the FIRST attempt runs with the full in-memory [Spot] (address and place included — the happy
 * online path publishes rich); the PERSISTED op is the fallback for process death and carries the
 * minimal payload (no address — `AddressInfo` stays free of serialization annotations; a spot
 * replayed later arrives without its street label, a declared degradation). Either way the
 * TTL-in-queue gate is anchored at ENQUEUE: a spot that expires while queued is never published.
 * [IOS-SYNC-A-QUEUE-THAT-DIES-WITH-THE-PROCESS-001]
 */
class IosReportSpotScheduler(
    private val spotRepository: SpotRepository,
    private val outbox: SyncOutbox,
    private val drainer: Lazy<SyncOutboxDrainer>,
) : ReportSpotScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun enqueueReportSpot(
        spotId: String,
        lat: Double,
        lon: Double,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
        spotType: SpotType,
        confidence: Float,
        sizeCategory: VehicleSize?,
        carbodyType: CarbodyType?,
        reportedBy: String?,
        provisional: Boolean,
        zoneRadiusMeters: Float?,
    ) {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val op = OutboxOp.ReportSpot(
            spotId = spotId,
            lat = lat,
            lon = lon,
            spotType = spotType,
            confidence = confidence,
            sizeCategory = sizeCategory,
            carbodyType = carbodyType,
            reportedBy = reportedBy ?: "",
            provisional = provisional,
            enqueuedAtMs = nowMs,
        )
        outbox.persist(op) // crash-safe from this instant
        // [DET-HANDOFF-NOT-MANUAL-001 §B] A deduced departure buys a short lifetime, same as Android.
        val richSpot = Spot(
            id = spotId,
            location = GpsPoint(latitude = lat, longitude = lon, accuracy = 0f, timestamp = nowMs, speed = 0f),
            reportedBy = reportedBy ?: "", // [AUDIT-RULES-001 C4] uid identity
            address = address,
            placeInfo = placeInfo,
            type = spotType,
            confidence = confidence,
            sizeCategory = sizeCategory,
            carbodyType = carbodyType,
            expiresAt = nowMs + SpotTtlPolicy.ttlMs(provisional), // [AUDIT-ARCH-001 M13]
            // [DET-HANDOFF-NOT-MANUAL-001 §B.3] How well this departure is proven travels with the spot.
            status = if (provisional) SpotStatus.PROVISIONAL else SpotStatus.CONFIRMED,
            zoneRadiusMeters = zoneRadiusMeters, // [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001]
        )
        scope.launch {
            val rich = spotRepository.reportSpotReleased(richSpot)
            if (rich.isSuccess) {
                outbox.remove(op.key)
            } else {
                PaparcarLogger.w(TAG, "rich publish failed for $spotId — the outbox op owns the retry")
                drainer.value.drain()
            }
        }
    }

    private companion object {
        const val TAG = "IosReportSpotScheduler"
    }
}

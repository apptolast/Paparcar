package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.service.ReportSpotScheduler

class FakeReportSpotScheduler : ReportSpotScheduler {

    var scheduleCallCount = 0
        private set
    var lastSpotId: String? = null
    var lastLat: Double = Double.NaN
    var lastLon: Double = Double.NaN
    var lastSpotType: SpotType? = null
    var lastConfidence: Float = 0f
    var lastSizeCategory: VehicleSize? = null
    var lastCarbodyType: CarbodyType? = null
    var lastAddress: AddressInfo? = null
    var lastPlaceInfo: PlaceInfo? = null
    var lastReportedBy: String? = null

    /** When true, [enqueueReportSpot] throws to simulate a downstream failure. */
    var shouldThrow: Boolean = false

    /** What the last publish CLAIMED about the space — confirmed / provisional (deduced
     *  departure) / releasing (nobody has left yet). [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] */
    var lastClaim: SpotStatus? = null
        private set

    /** [DET-HANDOFF-NOT-MANUAL-001 §B] Kept as a reading of [lastClaim] so the tests that only
     *  care about "short TTL or not" need no change. */
    val lastProvisional: Boolean? get() = lastClaim?.let { it == SpotStatus.PROVISIONAL }

    /** [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] Radius of the last publish when it was a zone
     *  spot; null = exact. */
    var lastZoneRadiusMeters: Float? = null
        private set

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
        claim: SpotStatus,
        zoneRadiusMeters: Float?,
    ) {
        scheduleCallCount++
        lastClaim = claim
        lastZoneRadiusMeters = zoneRadiusMeters
        lastSpotId = spotId
        lastLat = lat
        lastLon = lon
        lastAddress = address
        lastPlaceInfo = placeInfo
        lastSpotType = spotType
        lastConfidence = confidence
        lastSizeCategory = sizeCategory
        lastCarbodyType = carbodyType
        lastReportedBy = reportedBy
        if (shouldThrow) throw RuntimeException("simulated enqueue failure")
    }
}

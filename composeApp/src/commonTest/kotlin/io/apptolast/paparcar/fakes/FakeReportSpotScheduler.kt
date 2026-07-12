package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.service.ReportSpotScheduler

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
    ) {
        scheduleCallCount++
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

package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.model.AddressInfo
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
    var lastAddress: AddressInfo? = null
    var lastPlaceInfo: PlaceInfo? = null

    override fun schedule(
        spotId: String,
        lat: Double,
        lon: Double,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
        spotType: SpotType,
        confidence: Float,
        sizeCategory: VehicleSize?,
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
    }
}

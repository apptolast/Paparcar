package io.apptolast.paparcar.ios.stub

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.service.ReportSpotScheduler

// No-op stub until BGTaskScheduler + Firestore upload is implemented for iOS (Phase 6).
class StubReportSpotScheduler : ReportSpotScheduler {
    override fun schedule(
        spotId: String,
        lat: Double,
        lon: Double,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
    ) = Unit
}

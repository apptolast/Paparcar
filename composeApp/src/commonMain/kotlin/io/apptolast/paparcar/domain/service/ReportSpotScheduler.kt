package io.apptolast.paparcar.domain.service

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.VehicleSize

/**
 * Schedules guaranteed delivery of a "spot released" report to the remote backend.
 *
 * The implementation (WorkManager on Android) persists the job across process death
 * and retries automatically when the device is connected to the network.
 *
 * Phase 4 params ([spotType], [confidence], [sizeCategory]) are forwarded to the
 * [Spot] written to Firestore so clients can render reliability indicators.
 */
interface ReportSpotScheduler {
    fun schedule(
        spotId: String,
        lat: Double,
        lon: Double,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
        spotType: SpotType = SpotType.AUTO_DETECTED,
        confidence: Float = 1f,
        sizeCategory: VehicleSize? = null,
    )
}

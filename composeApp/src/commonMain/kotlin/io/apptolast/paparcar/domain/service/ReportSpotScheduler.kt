package io.apptolast.paparcar.domain.service

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo

/**
 * Schedules guaranteed delivery of a "spot released" report to the remote backend.
 *
 * The implementation (WorkManager on Android) persists the job across process death
 * and retries automatically when the device is connected to the network.
 */
interface ReportSpotScheduler {
    fun schedule(spotId: String, lat: Double, lon: Double, address: AddressInfo?, placeInfo: PlaceInfo?)
}

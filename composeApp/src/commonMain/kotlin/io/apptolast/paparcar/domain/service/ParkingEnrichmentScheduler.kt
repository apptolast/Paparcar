package io.apptolast.paparcar.domain.service

/**
 * Schedules background enrichment of a newly saved [UserParking] session
 * with geocoder address and POI data.
 *
 * The implementation (WorkManager on Android) runs the network calls off the
 * critical path, with automatic retry when the device is connected.
 */
interface ParkingEnrichmentScheduler {
    fun schedule(sessionId: String, lat: Double, lon: Double)
}

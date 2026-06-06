package io.apptolast.paparcar.domain.service

/**
 * Schedules background geocoding enrichment of a newly saved [UserParking] session.
 *
 * Fire-and-forget (non-suspending). The Android implementation uses WorkManager
 * ([EnrichParkingSessionWorker]) so the work survives process death and retries
 * when the device has network. iOS uses a coroutine scope with exponential backoff.
 *
 * Enrichment flow:
 * 1. Caller enqueues via [enqueueEnrichSession].
 * 2. Worker invokes [GetAddressAndPlaceUseCase] — reverse geocodes lat/lon into address + POI.
 * 3. Worker calls [UserParkingRepository.updateParkingSessionAddressAndPlace] — writes to Room.
 * 4. Repository calls [ParkingSyncScheduler.enqueueUpdateParkingSessionAddressAndPlace] — pushes to Firestore.
 *
 * This scheduler is intentionally separate from [ParkingSyncScheduler]: enrichment is a
 * domain concern (data quality), sync is an infrastructure concern (Firestore replication).
 */
interface ParkingEnrichmentScheduler {

    /**
     * Enqueues background geocoding for the parking session identified by [sessionId]
     * at the given coordinates. Any previous enrichment job for the same session is
     * replaced (idempotent on duplicate enqueue).
     */
    fun enqueueEnrichSession(sessionId: String, lat: Double, lon: Double)
}

@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger
import io.apptolast.paparcar.domain.model.AddressAndPlace
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock

/**
 * Executes all side-effects of a confirmed car departure:
 *
 * 1. Resolves the active [UserParking] session for the given geofence.
 * 2. Reports the freed spot to the community (public spots only; private zones are not published).
 * 3. Clears the session from the local store.
 * 4. Resets [DepartureEventBus] so the next trip starts clean.
 * 5. Removes the geofence from Play Services.
 *
 * Returns [Result.failure] if the session clear fails — the caller (WorkManager)
 * should retry so the session is never left open after a confirmed departure.
 *
 * Report is scheduled BEFORE the clear: the WorkManager job is durably enqueued
 * even if the process is killed mid-flight, and REPLACE policy prevents duplicates
 * on retry.
 */
class ProcessConfirmedDepartureUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val geofenceService: GeofenceManager,
    private val departureEventBus: DepartureEventBus,
    // Nullable so existing test doubles / call sites need no change. [DET-SOLID-001]
    private val detectionEventLogger: DetectionEventLogger? = null,
) {
    private companion object {
        const val TAG = "ProcessConfirmedDeparture"
    }

    /**
     * @param publishSpot false when the departure is confirmed but too STALE to advertise the
     *        freed spot (recovered hours late — the hole is long gone); the session/geofence
     *        cleanup still runs so the app's own state converges. [DET-RECONCILE-001]
     */
    suspend operator fun invoke(geofenceId: String, publishSpot: Boolean = true): Result<Unit> {
        val session = userParkingRepository.getActiveSessionByGeofence(geofenceId)
        val spotId = session?.id ?: "auto_${Clock.System.now().toEpochMilliseconds()}"
        val lat = session?.location?.latitude
        val lon = session?.location?.longitude

        // Public spots are published to the community; private zones are not reported.
        if (publishSpot && session != null && lat != null && lon != null && session.privateZoneId == null) {
            // Reuse the address/POI enriched on the session at park time instead of
            // re-geocoding the same coordinates. Null when not yet enriched → the use
            // case geocodes inline. [SPOT-PREFETCH-001]
            val prefetched = session.address?.let { AddressAndPlace(it, session.placeInfo) }
            reportSpotReleased(
                lat = lat,
                lon = lon,
                spotId = spotId,
                spotType = session.spotType,
                confidence = session.detectionReliability ?: 1f,
                sizeCategory = session.sizeCategory,
                carbodyType = session.carbodyType,
                prefetched = prefetched,
            )
        }

        if (session != null) {
            userParkingRepository.clearActiveParkingSession(session.id)
                .onFailure { e ->
                    PaparcarLogger.e(TAG, "clearActiveParkingSession failed for session=${session.id}", e)
                    return Result.failure(e)
                }
        }

        departureEventBus.reset()
        geofenceService.removeGeofence(geofenceId)

        // [DET-SOLID-001] Observability: the publish+clear outcome, traced by geofenceId.
        detectionEventLogger?.log(
            DetectionEvent.DepartureProcessed(
                sessionId = geofenceId,
                timestampMs = Clock.System.now().toEpochMilliseconds(),
                published = publishSpot && session != null && session.privateZoneId == null,
                sessionCleared = session != null,
                location = session?.location,
            )
        )
        return Result.success(Unit)
    }
}

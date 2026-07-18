@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger
import io.apptolast.paparcar.domain.model.AddressAndPlace
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock

/**
 * Releases the user's active parking session.
 *
 * When [publishSpot] is `true` (default), the freed parking is also published
 * to the community via a durable WorkManager job so it survives process death.
 * When `false`, only the local + remote session is cleared and no community
 * spot is reported — used by the "Just delete" path in the release dialog
 * when the user doesn't want to share. [PEEK-ACTIONS-001]
 *
 * Returns [Result.failure] if clearing the session fails. When publishing,
 * the spot report is enqueued first so the community spot is never lost even
 * if the clear fails.
 */
class ReleaseActiveParkingSessionUseCase(
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val userParkingRepository: UserParkingRepository,
    private val geofenceService: GeofenceManager,
    private val detectionEventLogger: DetectionEventLogger,
) {
    suspend operator fun invoke(
        lat: Double,
        lon: Double,
        currentSession: UserParking?,
        publishSpot: Boolean = true,
    ): Result<Unit> {
        if (publishSpot) {
            val spotId = currentSession?.id
                ?: "manual_${Clock.System.now().toEpochMilliseconds()}"
            val spotType = currentSession?.spotType ?: SpotType.AUTO_DETECTED
            val confidence = currentSession?.detectionReliability ?: 1f
            val sizeCategory = currentSession?.sizeCategory
            val carbodyType = currentSession?.carbodyType
            // The session was geocoded (enriched) at park time, so reuse its stored
            // address/POI instead of re-hitting the network — same coordinates, same
            // result. Null when not yet enriched → use case geocodes inline. [SPOT-PREFETCH-001]
            val prefetched = currentSession?.address?.let { AddressAndPlace(it, currentSession.placeInfo) }

            // Schedule WorkManager job BEFORE clearing so the spot report is durably
            // enqueued even if the app is killed immediately after.
            reportSpotReleased(
                lat = lat,
                lon = lon,
                spotId = spotId,
                spotType = spotType,
                confidence = confidence,
                sizeCategory = sizeCategory,
                carbodyType = carbodyType,
                prefetched = prefetched,
            )
        }

        // Under multi-parking we can only clear *this* specific session — clearing
        // by id leaves other vehicles' active sessions intact. If the caller did
        // not supply a session (legacy / manual delete), nothing to clear locally.
        val sessionId = currentSession?.id ?: return Result.success(Unit)

        // Release observability: who (implicit in the uid-namespaced diagnostics path), which
        // session, and from where. Fire-and-forget — never blocks or fails the release. [VEH-ACTIVE-FENCE-001]
        val nowMs = Clock.System.now().toEpochMilliseconds()
        detectionEventLogger.log(
            DetectionEvent.Released(
                sessionId = sessionId,
                timestampMs = nowMs,
                published = publishSpot,
                location = GpsPoint(latitude = lat, longitude = lon, accuracy = 0f, timestamp = nowMs, speed = 0f),
            ),
        )

        val cleared = userParkingRepository.clearActiveParkingSession(sessionId)

        // [DET-AUDIT-002 T5/M4] A manual release must also unregister the session's fences:
        // revert and departure already do, but this path left a NEVER_EXPIRE orphan behind on
        // every release — the next crossing cost an FGS start + notification flash before the
        // orphan-cleanup dismissed it. Best-effort: a failed removal is caught later by that
        // same orphan cleanup, so it must never fail the release. (P1-6 [Hallazgo A] converged
        // on the same fix from the 2026-07-12 field test — kept master's version.)
        if (cleared.isSuccess) {
            currentSession.geofenceId?.let { geofenceId ->
                geofenceService.removeGeofence(geofenceId)
                    .onFailure { PaparcarLogger.w(TAG, "release: removeGeofence($geofenceId) failed (${it.message}) — orphan cleanup will catch it") }
            }
        }
        return cleared
    }

    private companion object {
        const val TAG = "ReleaseActiveParkingSession"
    }
}

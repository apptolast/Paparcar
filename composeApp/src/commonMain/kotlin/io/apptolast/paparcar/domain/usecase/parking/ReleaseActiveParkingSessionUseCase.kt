@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.AddressAndPlace
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
        val clearResult = userParkingRepository.clearActiveParkingSession(sessionId)

        // [DET-SUPERSEDE-001 / Hallazgo A] Freeing the spot ends the session, so its geofence
        // (EXIT + enter_/witness_ twins, NEVER_EXPIRE) must go too — otherwise it lingers with no
        // active session and fires a spurious GEOFENCE_EXIT on the next drive-away (leg chino→casa,
        // field 2026-07-12). Same session-end ⇒ removeGeofence invariant Revert/Departure/Update
        // already follow. Best-effort: a failed removal is repaired by the orphan-cleanup net.
        val geofenceId = currentSession.geofenceId ?: sessionId
        geofenceService.removeGeofence(geofenceId)
            .onFailure { e -> PaparcarLogger.w(DIAG, "removeGeofence($geofenceId) failed (continuing)", e) }

        return clearResult
    }

    private companion object {
        const val DIAG = "PARKDIAG/Release"
    }
}

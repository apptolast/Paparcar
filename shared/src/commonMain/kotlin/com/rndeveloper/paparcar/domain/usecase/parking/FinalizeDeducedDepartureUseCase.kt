@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock

/**
 * [DET-COORDINATOR-NO-OPTIONAL-DEPS-001] The face of [FinalizeDeducedDepartureUseCase] that the
 * coordinator holds. The concrete class is final and drags the whole publish graph behind its
 * constructor ([com.rndeveloper.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase] and
 * everything IT needs), which is why the coordinator's tests used to inject `null` instead of a
 * double — and `null` is what left the §B lane with zero test coverage. A seam the tests can
 * implement in three lines removes the last excuse for the nullable.
 */
fun interface FinalizeDeducedDeparture {
    /** See [FinalizeDeducedDepartureUseCase.invoke]. */
    suspend operator fun invoke(vehicleId: String?): Boolean
}

/**
 * [DET-HANDOFF-NOT-MANUAL-001 §B] Completes a departure that was only DEDUCED, at the moment a
 * drive is finally MEASURED.
 *
 * A deduced departure publishes the community spot straight away (freshness is its whole value) but
 * gives up nothing that belongs to the user: the session and its geofence stay, marked with
 * `provisionalDepartureAtMs`, and the spot carries the short provisional TTL. Two things can happen
 * next, and this is the good one:
 *
 * - **The live session proves a drive** → the deduction was right. The spot is re-published under
 *   the same id (REPLACE, one document) with the full TTL, and only NOW is the car released and its
 *   geofence removed. That is the commit, moved from the guess to the proof.
 * - **The session dies without a drive** → nothing to undo, because nothing was taken. The
 *   provisional spot expires on its own within
 *   [com.rndeveloper.paparcar.domain.model.SpotTtlPolicy.PROVISIONAL_SPOT_TTL_MS]. (An explicit
 *   retraction — plus notifying whoever was already driving to it — is the follow-up in §B.3.)
 *
 * Idempotent and cheap: no marker on the vehicle's active session (or no session at all) → returns
 * false without touching anything, which is the normal case for every witnessed departure, since
 * those released the session at departure time.
 */
class FinalizeDeducedDepartureUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val geofenceService: GeofenceManager,
    private val detectionEventLogger: DetectionEventLogger? = null,
) : FinalizeDeducedDeparture {
    /**
     * @param vehicleId the vehicle whose live session just proved a drive. The pending departure is
     *   looked up on THAT vehicle's active session — a second car parked elsewhere is untouched,
     *   and a drive proven with no pending deduction (a bus ride, a witnessed departure already
     *   committed) finds nothing to finalize.
     * @return true when a pending deduced departure was completed.
     */
    override suspend operator fun invoke(vehicleId: String?): Boolean {
        val session = vehicleId?.let { userParkingRepository.getActiveSessionByVehicle(it) } ?: return false
        val deducedAtMs = session.provisionalDepartureAtMs ?: return false

        val now = Clock.System.now().toEpochMilliseconds()
        PaparcarLogger.d(
            TAG,
            "drive measured ${now - deducedAtMs}ms after a deduced departure — promoting the spot " +
                "and releasing session=${session.id} [DET-HANDOFF-NOT-MANUAL-001]",
        )

        // Same spot id → the worker's REPLACE policy rewrites the SAME document with the full TTL.
        // Private zones were never published, so there is nothing to promote for them.
        if (session.privateZoneId == null) {
            reportSpotReleased(
                lat = session.location.latitude,
                lon = session.location.longitude,
                spotId = session.id,
                spotType = session.spotType,
                confidence = session.detectionReliability ?: 1f,
                sizeCategory = session.sizeCategory,
                carbodyType = session.carbodyType,
                prefetched = session.address?.let { AddressAndPlace(it, session.placeInfo) },
                provisional = false,
            )
        }

        // The session ended when the deduced departure actually happened, not when the drive was
        // finally measured — and the provisional spot (now promoted) was its publication, unless
        // the zone was private and nothing ever went out. [VEH-STATS-SAY-SOMETHING-USEFUL-001]
        userParkingRepository.clearActiveParkingSession(
            session.id,
            endedAtMs = deducedAtMs,
            publishedSpot = session.privateZoneId == null,
        )
            .onFailure { e ->
                // The marker stays, so a later proof (or the safety net's next pass) retries. The
                // spot is already promoted, which is the half that matters to the community.
                PaparcarLogger.e(TAG, "clearActiveParkingSession failed for session=${session.id}", e)
                return false
            }
        session.geofenceId?.let { geofenceService.removeGeofence(it) }

        detectionEventLogger?.log(
            DetectionEvent.DepartureProcessed(
                sessionId = session.geofenceId ?: session.id,
                timestampMs = now,
                published = session.privateZoneId == null,
                sessionCleared = true,
                location = session.location,
            )
        )
        return true
    }

    private companion object {
        const val TAG = "PARKDIAG/FinalizeDeducedDeparture"
    }
}

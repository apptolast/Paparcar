@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.detection.DepartureProof
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
 * [DET-HANDOFF-NOT-MANUAL-001 §B] Steps 3-5 are the IRREVERSIBLE half and they now require a
 * [DepartureProof.Witnessed] departure. On [DepartureProof.Deduced] only step 2 runs (provisionally),
 * because publishing fast is worth minutes to a stranger while releasing the car early is worth
 * nothing to anyone and costs its owner everything when the deduction is wrong.
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
     * @param proof how well the departure is proven. [DepartureProof.Witnessed] behaves as it always
     *        did. [DepartureProof.Deduced] splits the two commitments: the spot still goes out
     *        IMMEDIATELY (freshness is its entire value) but provisionally — short TTL — while the
     *        user's session and its geofence are KEPT, because nothing measured says the car moved.
     *        The session is marked so the moment a drive IS proven can promote the spot and release
     *        the car, and so a short TTL running out is the worst case of a wrong deduction.
     *        [DET-HANDOFF-NOT-MANUAL-001 §B]
     */
    suspend operator fun invoke(
        geofenceId: String,
        publishSpot: Boolean = true,
        proof: DepartureProof = DepartureProof.Witnessed,
    ): Result<Unit> {
        val session = userParkingRepository.getActiveSessionByGeofence(geofenceId)
        val spotId = session?.id ?: "auto_${Clock.System.now().toEpochMilliseconds()}"
        val lat = session?.location?.latitude
        val lon = session?.location?.longitude

        // [DET-HANDOFF-NOT-MANUAL-001 §B] A pending deduction publishes ONCE. The session now
        // survives a deduced departure, so the safety net will keep re-deducing one every 15 min
        // while the user stays away from the car — and each pass would re-publish the same spot,
        // blinking a ghost on and off all afternoon. The first publish stands (its short TTL bounds
        // it); the next real publication is either the PROMOTION when a drive is measured
        // ([FinalizeDeducedDepartureUseCase]) or a Witnessed departure, both of which rewrite the
        // same document with the full TTL. Re-dispatching itself is still allowed — that is what
        // keeps handing the trip to live detection, which is the only thing that can prove a drive.
        val alreadyPublishedProvisionally =
            proof == DepartureProof.Deduced && session?.provisionalDepartureAtMs != null

        // Public spots are published to the community; private zones are not reported.
        if (publishSpot && !alreadyPublishedProvisionally &&
            session != null && lat != null && lon != null && session.privateZoneId == null
        ) {
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
                // [DET-HANDOFF-NOT-MANUAL-001 §B] Same instant, shorter life.
                provisional = proof == DepartureProof.Deduced,
            )
        }

        // [DET-HANDOFF-NOT-MANUAL-001 §B] A DEDUCED departure does not get to take the car. The
        // session and its geofence stay exactly as they were — the pin is still the best answer to
        // "where is my car" until something MEASURES a drive, and the geofence is the trigger that
        // catches the real departure when it comes. Field 2026-08-19: a bicycle ride released two
        // cars that never moved, and the geofence removal took their next EXIT trigger with them.
        if (proof == DepartureProof.Deduced) {
            if (session != null) {
                userParkingRepository.markProvisionalDeparture(session.id, Clock.System.now().toEpochMilliseconds())
            }
            PaparcarLogger.d(
                TAG,
                "deduced departure (geof=${geofenceId.take(8)}) — spot published PROVISIONALLY, " +
                    "session + geofence KEPT until a drive is measured [DET-HANDOFF-NOT-MANUAL-001]",
            )
            detectionEventLogger?.log(
                DetectionEvent.DepartureProcessed(
                    sessionId = geofenceId,
                    timestampMs = Clock.System.now().toEpochMilliseconds(),
                    published = publishSpot && !alreadyPublishedProvisionally &&
                        session != null && session.privateZoneId == null,
                    sessionCleared = false,
                    location = session?.location,
                )
            )
            return Result.success(Unit)
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

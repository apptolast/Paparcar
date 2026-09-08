@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker
import com.rndeveloper.paparcar.domain.analytics.SessionEndReason
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingReleaseReason
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy
import com.rndeveloper.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock

/**
 * Releases the user's active parking session.
 *
 * [reason] says WHY the session is closing and owns the consequences: a departure that publishes
 * reports the freed parking to the community via a durable WorkManager job so it survives process
 * death; a departure the user keeps private, and a deleted record, only clear the local + remote
 * session. [PEEK-ACTIONS-001] [PARK-DELETE-NO-DECLARE-001]
 *
 * Returns [Result.failure] if clearing the session fails; on success the value says whether a
 * community spot actually went out — [reason] asking for one is no longer enough, because a zone
 * session wider than the publish ceiling withholds it [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] —
 * so the UI can thank the user for a plaza only when there is one. When publishing, the spot
 * report is enqueued first so the community spot is never lost even if the clear fails.
 */
class ReleaseActiveParkingSessionUseCase(
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val userParkingRepository: UserParkingRepository,
    private val geofenceService: GeofenceManager,
    private val detectionEventLogger: DetectionEventLogger,
    // Optional product-analytics sink; fire-and-forget, nullable for test doubles. [ANALYTICS-EVENTS-001]
    private val analyticsTracker: AnalyticsTracker? = null,
) {
    suspend operator fun invoke(
        lat: Double,
        lon: Double,
        currentSession: UserParking?,
        reason: ParkingReleaseReason = ParkingReleaseReason.DEPARTURE_PUBLISHED,
    ): Result<Boolean> {
        // [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] The user's "Me voy" witnesses the departure,
        // but the coordinates published are the SESSION's pin — and for an approximate session
        // that pin is the centre of a doubt circle. Same shape rule as every other publish path.
        val shape = SpotReleasePolicy.shapeFor(
            currentSession?.zoneRadiusMeters,
            currentSession?.detectionReliability,
        )
        val publishes = reason.publishesSpot && shape != SpotReleasePolicy.Shape.Withheld
        if (reason.publishesSpot && !publishes) {
            PaparcarLogger.d(
                TAG,
                "⊘ zone r=${currentSession?.zoneRadiusMeters}m > ${SpotReleasePolicy.ZONE_PUBLISH_MAX_RADIUS_METERS}m " +
                    "— freed spot WITHHELD on manual release [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001]",
            )
        }
        if (publishes) {
            val spotId = currentSession?.id
                ?: "manual_${Clock.System.now().toEpochMilliseconds()}"
            val spotType = currentSession?.spotType ?: SpotType.AUTO_DETECTED
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
                confidence = when (shape) {
                    is SpotReleasePolicy.Shape.Exact -> shape.confidence
                    is SpotReleasePolicy.Shape.Zone -> shape.confidence
                    SpotReleasePolicy.Shape.Withheld -> 0f // unreachable — gated above
                },
                sizeCategory = sizeCategory,
                carbodyType = carbodyType,
                prefetched = prefetched,
                // A zone spot is provisional whatever proved the departure: the place never was.
                provisional = shape is SpotReleasePolicy.Shape.Zone,
                zoneRadiusMeters = (shape as? SpotReleasePolicy.Shape.Zone)?.radiusMeters,
            )
        }

        // Under multi-parking we can only clear *this* specific session — clearing
        // by id leaves other vehicles' active sessions intact. If the caller did
        // not supply a session (legacy / manual delete), nothing to clear locally.
        val sessionId = currentSession?.id ?: return Result.success(publishes)

        // Release observability: who (implicit in the uid-namespaced diagnostics path), which
        // session, from where — and WHY, so a replay can tell a departure from a deleted record
        // instead of guessing from `published`. Fire-and-forget — never blocks or fails the
        // release. [VEH-ACTIVE-FENCE-001] [PARK-DELETE-NO-DECLARE-001]
        val nowMs = Clock.System.now().toEpochMilliseconds()
        detectionEventLogger.log(
            DetectionEvent.Released(
                sessionId = sessionId,
                timestampMs = nowMs,
                published = publishes,
                reason = reason,
                location = GpsPoint(latitude = lat, longitude = lon, accuracy = 0f, timestamp = nowMs, speed = 0f),
            ),
        )

        // Close provenance: the release moment + whether this close gave the community a spot.
        // [VEH-STATS-SAY-SOMETHING-USEFUL-001]
        val cleared = userParkingRepository.clearActiveParkingSession(
            sessionId, endedAtMs = nowMs, publishedSpot = publishes,
        )

        // [ANALYTICS-EVENTS-001] The close is durable → the fact is countable (cell-only). All
        // user-initiated closes count as MANUAL_RELEASE regardless of whether a spot went out.
        if (cleared.isSuccess) {
            analyticsTracker?.track(
                AnalyticsEvent.ParkingSessionEnded(
                    location = currentSession.location,
                    reason = SessionEndReason.MANUAL_RELEASE,
                )
            )
        }

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
        return cleared.map { publishes }
    }

    private companion object {
        const val TAG = "ReleaseActiveParkingSession"
    }
}

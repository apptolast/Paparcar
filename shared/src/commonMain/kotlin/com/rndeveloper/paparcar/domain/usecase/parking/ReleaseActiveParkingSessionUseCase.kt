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
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy
import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy.publishConfidence
import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy.publishRadiusMeters
import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy.publishes
import com.rndeveloper.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock

/**
 * Releases the user's active parking session.
 *
 * [reason] says WHY the session is closing and owns the consequences: a departure OFFERS the freed
 * parking to the community via a durable WorkManager job so it survives process death; a deleted
 * record only clears the local + remote session. [PEEK-ACTIONS-001] [PARK-DELETE-NO-DECLARE-001]
 *
 * Returns [Result.failure] if clearing the session fails; on success the value says whether a
 * community spot actually went out — [reason] offering one is not enough, because
 * [SpotReleasePolicy] can withhold it (the park is inside a private zone, or the session's doubt
 * circle is wider than the publish ceiling) — so the UI can thank the user for a plaza only when
 * there is one. When publishing, the spot report is enqueued first so the community spot is never
 * lost even if the clear fails.
 *
 * [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] This use case is the one that had
 * NO private-zone term at all — three sibling publish paths carried `session.privateZoneId == null`
 * and this one simply did not, so "Me voy → Liberar plaza" over a car parked in the user's own
 * garage published it to the community. The term is not added here: it moved INTO
 * [SpotReleasePolicy.shapeFor], which every door already calls, so no door can be missing it again.
 */
class ReleaseActiveParkingSessionUseCase(
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val userParkingRepository: UserParkingRepository,
    private val geofenceService: GeofenceManager,
    private val detectionEventLogger: DetectionEventLogger,
    /** [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] The user's zones as they are
     *  NOW — the gate is evaluated at publish time, not read off the stamp the park left behind. */
    private val zoneRepository: ZoneRepository,
    // Optional product-analytics sink; fire-and-forget, nullable for test doubles. [ANALYTICS-EVENTS-001]
    private val analyticsTracker: AnalyticsTracker? = null,
    /**
     * [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] Only to retire the boarding stamp — this path
     * publishes on the SAME document a boarding may have opened as RELEASING, so leaving the stamp
     * behind would let the session's own teardown withdraw the real spot it just published.
     */
    private val appPreferences: com.rndeveloper.paparcar.domain.preferences.AppPreferences? = null,
) {
    suspend operator fun invoke(
        lat: Double,
        lon: Double,
        currentSession: UserParking?,
        reason: ParkingReleaseReason = ParkingReleaseReason.DEPARTURE,
    ): Result<Boolean> {
        // [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] The user's "Me voy" witnesses the departure,
        // but the coordinates published are the SESSION's pin — and for an approximate session
        // that pin is the centre of a doubt circle. Same shape rule as every other publish path.
        // [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] …and the same private-zone
        // rule, which this path never had. Asked against the coordinates being PUBLISHED (lat/lon),
        // not the session's stamp: they are the same point on every real call, and when a caller
        // passes something else, what must be gated is what goes out.
        // A failed zone read reads as "no private zones" — the same answer an account with none
        // gives, and the one that keeps a departure from being blocked by a Room hiccup.
        val privateZones = runCatching { zoneRepository.getPrivateZonesSnapshot() }
            .onFailure { PaparcarLogger.w(TAG, "private zones unreadable — release proceeds as if there were none", it) }
            .getOrDefault(emptyList())
        val shape = SpotReleasePolicy.shapeFor(
            location = GpsPoint(latitude = lat, longitude = lon, accuracy = 0f, timestamp = 0L, speed = 0f),
            zoneRadiusMeters = currentSession?.zoneRadiusMeters,
            detectionReliability = currentSession?.detectionReliability,
            privateZones = privateZones,
        )
        val publishes = reason.offersSpot && shape.publishes()
        if (reason.offersSpot && !publishes) {
            val withheld = shape as SpotReleasePolicy.Shape.Withheld
            PaparcarLogger.d(
                TAG,
                "⊘ freed spot WITHHELD on manual release — ${withheld.reason} " +
                    "(zone r=${currentSession?.zoneRadiusMeters}m, ceiling " +
                    "${SpotReleasePolicy.ZONE_PUBLISH_MAX_RADIUS_METERS}m); session cleanup continues " +
                    "[SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001]",
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
                confidence = shape.publishConfidence(),
                sizeCategory = sizeCategory,
                carbodyType = carbodyType,
                prefetched = prefetched,
                // A zone spot is provisional whatever proved the departure: the place never was.
                claim = if (shape is SpotReleasePolicy.Shape.Zone) SpotStatus.PROVISIONAL else SpotStatus.CONFIRMED,
                zoneRadiusMeters = shape.publishRadiusMeters(),
            )
            // [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] The publish above PROMOTED the releasing
            // document, if there was one. Without this the stamp survives the release, the
            // coordinator session ends with it still live, and its teardown withdraws the very spot
            // the user's "Me voy" just published — a real plaza taken down seconds after going out.
            runCatching {
                appPreferences?.pendingSpotRelease()
                    ?.takeIf { it.spotId == spotId }
                    ?.let { appPreferences.clearPendingSpotRelease() }
            }.onFailure { PaparcarLogger.w(TAG, "could not retire the boarding stamp (continuing)", it) }
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

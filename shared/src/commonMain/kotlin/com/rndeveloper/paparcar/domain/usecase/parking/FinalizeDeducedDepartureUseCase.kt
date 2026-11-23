@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker
import com.rndeveloper.paparcar.domain.analytics.SessionEndReason
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy
import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy.publishConfidence
import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy.refuelStopAnswer
import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy.publishRadiusMeters
import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy.publishes
import com.rndeveloper.paparcar.domain.detection.parkedSessionIsContradictedByBtConnect
import com.rndeveloper.paparcar.domain.detection.ports.BtConnectStampReader
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
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
    // Optional product-analytics sink; fire-and-forget, nullable for test doubles. [ANALYTICS-EVENTS-001]
    private val analyticsTracker: AnalyticsTracker? = null,
    /** [DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS-001] The SECOND door out to the community, and it
     *  needed the same gate: a session can have been marked provisional legitimately and have the
     *  car moved by somebody else afterwards, so the promotion has to re-ask whether the pin still
     *  knows where the car is. Nullable for the same reason as everywhere: no stamp = cannot know. */
    private val btConnectStamps: BtConnectStampReader? = null,
    private val config: ParkingDetectionConfig = ParkingDetectionConfig(),
    /** [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] The user's zones as they are
     *  NOW — required, never nullable: a missing reader would silently promote a private garage's
     *  plaza to the FULL lifetime, which is the loudest version of the bug this ticket closes. */
    private val zoneRepository: ZoneRepository,
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
        // [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] Same shape rule as the departure that deduced
        // it: a withheld zone published nothing then and promotes nothing now, and a bounded zone
        // stays a ZONE spot — the drive just measured proves the HOUR of the departure, never the
        // place, so the promotion keeps the radius, the capped confidence and the provisional
        // (short) lifetime.
        // [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] The private-zone term used
        // to sit beside this line as `session.privateZoneId == null`; it is now inside the call, and
        // asked against the zones as they are at PROMOTION time. A failed read reads as "none".
        val privateZones = runCatching { zoneRepository.getPrivateZonesSnapshot() }
            .onFailure { PaparcarLogger.w(TAG, "private zones unreadable — promotion proceeds as if there were none", it) }
            .getOrDefault(emptyList())
        val shape = SpotReleasePolicy.shapeFor(
            location = session.location,
            zoneRadiusMeters = session.zoneRadiusMeters,
            detectionReliability = session.detectionReliability,
            privateZones = privateZones,
            // [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] Dated by the DEDUCED departure, the same
            // instant this close is stamped with — not by the promotion's own clock, which runs
            // whenever the drive happened to be measured and would stretch a tankful into a park.
            placeInfo = session.placeInfo,
            dwellMs = session.dwellMs(deducedAtMs),
        )
        // [DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS-001] The pin may have gone stale between the
        // deduction and this promotion: the paired car connecting somewhere else is the only signal
        // that says so, and promoting on top of it would publish a plaza freed by another person at
        // an hour nobody witnessed — with the FULL lifetime, which is worse than the provisional one.
        val stamp = session.vehicleId?.let { runCatching { btConnectStamps?.lastConnect(it) }.getOrNull() }
        val pinIsContradicted = parkedSessionIsContradictedByBtConnect(
            sessionLocation = session.location,
            connectFix = stamp?.fix,
            connectedAtMs = stamp?.atMs,
            config = config,
        )
        if (pinIsContradicted) {
            PaparcarLogger.w(
                TAG,
                "⊘ the paired car CONNECTED elsewhere since this park — promoting nothing, the " +
                    "provisional spot's own TTL is left to expire [DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS-001]",
            )
        }
        val publishes = !pinIsContradicted && shape.publishes()
        if (publishes) {
            reportSpotReleased(
                lat = session.location.latitude,
                lon = session.location.longitude,
                spotId = session.id,
                spotType = session.spotType,
                confidence = shape.publishConfidence(),
                sizeCategory = session.sizeCategory,
                carbodyType = session.carbodyType,
                prefetched = session.address?.let { AddressAndPlace(it, session.placeInfo) },
                // Promoted out of RELEASING/PROVISIONAL — except a ZONE spot, whose doubt is
                // about the PLACE and does not expire with a drive.
                claim = if (shape is SpotReleasePolicy.Shape.Zone) SpotStatus.PROVISIONAL else SpotStatus.CONFIRMED,
                // Same document, second publish — the provisional emission already counted this
                // spot. [ANALYTICS-EVENTS-001]
                promotion = true,
                zoneRadiusMeters = shape.publishRadiusMeters(),
            )
        }

        // The session ended when the deduced departure actually happened, not when the drive was
        // finally measured — and the provisional spot (now promoted) was its publication, unless
        // the zone was private and nothing ever went out. [VEH-STATS-SAY-SOMETHING-USEFUL-001]
        userParkingRepository.clearActiveParkingSession(
            session.id,
            endedAtMs = deducedAtMs,
            publishedSpot = publishes,
            refuelStop = shape.refuelStopAnswer(),
        )
            .onFailure { e ->
                // The marker stays, so a later proof (or the safety net's next pass) retries. The
                // spot is already promoted, which is the half that matters to the community.
                PaparcarLogger.e(TAG, "clearActiveParkingSession failed for session=${session.id}", e)
                return false
            }

        // [ANALYTICS-EVENTS-001] The close is durable → the fact is countable (cell-only).
        analyticsTracker?.track(
            AnalyticsEvent.ParkingSessionEnded(
                location = session.location,
                reason = SessionEndReason.DEPARTURE_DEDUCED,
            )
        )
        session.geofenceId?.let { geofenceService.removeGeofence(it) }

        detectionEventLogger?.log(
            DetectionEvent.DepartureProcessed(
                sessionId = session.geofenceId ?: session.id,
                timestampMs = now,
                published = publishes,
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

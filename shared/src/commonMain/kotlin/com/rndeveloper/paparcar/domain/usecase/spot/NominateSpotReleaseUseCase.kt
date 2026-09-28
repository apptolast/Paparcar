@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.spot

import com.rndeveloper.paparcar.domain.detection.PendingSpotRelease
import com.rndeveloper.paparcar.domain.detection.SpotReleaseAction
import com.rndeveloper.paparcar.domain.detection.sentry.triggerLedgerSessionId
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.SpotTtlPolicy
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy.publishConfidence
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock

/**
 * [DET-COORDINATOR-NO-OPTIONAL-DEPS-001] The seam the two lanes hold — same reason as
 * `FinalizeDeducedDeparture`: the concrete class is final, so a test could otherwise only inject
 * `null` and the lane would go unexercised.
 */
fun interface NominateSpotRelease {
    /** @see NominateSpotReleaseUseCase.invoke */
    suspend operator fun invoke(vehicleId: String, boardingIsCredible: Boolean, lane: String): Boolean
}

/**
 * [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] Publishes the plaza of a car somebody just boarded as
 * [SpotStatus.RELEASING], and remembers it so the trip can promote or withdraw it.
 *
 * The I/O half of [EvaluateSpotReleaseNominationUseCase]: it reads the session and the zones, asks
 * the verdict, and only then touches the world. The decision itself stays pure and testable.
 *
 * ## The id is the session's, and that is the whole promotion mechanism
 *
 * The real departure publishes with `spotId = session.id` too, so a plaza nominated here and freed
 * five minutes later is **one document that changes status**, never two spots. The driver who was
 * already heading over sees it turn from "se libera ahora" into a real spot in place, and the
 * en-route count they contributed survives.
 *
 * ## What it deliberately does not do
 *
 * It does not touch the parking session, the geofence or the detection state. A boarding takes
 * nothing away from the user — that asymmetry is the same one [DepartureProof] drew for the deduced
 * departure, and here it is even starker: nothing has been measured at all.
 */
class NominateSpotReleaseUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val zoneRepository: ZoneRepository,
    private val evaluate: EvaluateSpotReleaseNominationUseCase,
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val appPreferences: AppPreferences,
    private val detectionEventLogger: DetectionEventLogger,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : NominateSpotRelease {

    /**
     * @param vehicleId the car that was just boarded.
     * @param boardingIsCredible the observing lane's own verdict on whether this edge is a real
     *   boarding — see [EvaluateSpotReleaseNominationUseCase].
     * @param lane which lane saw the boarding (`bt` / `coordinator`), so the trace says WHO asked.
     *   [DET-A-RELEASING-SPOT-MUST-SAY-IT-WENT-OUT-001]
     * @return true when a releasing spot went out.
     */
    override suspend operator fun invoke(vehicleId: String, boardingIsCredible: Boolean, lane: String): Boolean {
        val session = runCatching { userParkingRepository.getActiveSessionByVehicle(vehicleId) }
            .onFailure { PaparcarLogger.w(TAG, "could not read the boarded car's session", it) }
            .getOrNull()
        // ⛔ A failed zone read must not read as "no zones": that is the one direction that
        // publishes a garage. The publication is skipped instead — a plaza that goes out four
        // minutes later at the real departure costs a head start; a leaked home address does not
        // come back. [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001]
        val zones = runCatching { zoneRepository.getPrivateZonesSnapshot() }
            .getOrElse { e ->
                PaparcarLogger.w(TAG, "private zones unreadable — not nominating [privacy fails closed]", e)
                // A refusal like any other, and the only one the evaluator never sees: it has to
                // name itself or the lane shows a boarding that resolved into nothing.
                record(SpotReleaseAction.SKIPPED, session?.id, lane, REASON_ZONES_UNREADABLE)
                return false
            }

        val verdict = evaluate(
            session = session,
            pending = appPreferences.pendingSpotRelease(),
            privateZones = zones,
            boardingIsCredible = boardingIsCredible,
            nowMs = nowMs(),
        )
        if (verdict is SpotReleaseNomination.Skip) {
            PaparcarLogger.d(TAG, "⊘ boarding of $vehicleId nominates nothing — ${verdict.reason}")
            record(SpotReleaseAction.SKIPPED, session?.id, lane, verdict.reason)
            return false
        }
        val publish = verdict as SpotReleaseNomination.Publish
        val park = publish.session

        reportSpotReleased(
            lat = park.location.latitude,
            lon = park.location.longitude,
            spotId = park.id,
            spotType = park.spotType,
            confidence = publish.shape.publishConfidence(),
            sizeCategory = park.sizeCategory,
            carbodyType = park.carbodyType,
            // The park's own enrichment, so the row can name the street without a second geocode.
            prefetched = park.address?.let { AddressAndPlace(it, park.placeInfo) },
            claim = SpotStatus.RELEASING,
            // ⛔ NOT a promotion: this is the FIRST publication of this document, and the one the
            // analytics should count. The departure that follows re-publishes the same id and is
            // the one that must stay silent. [ANALYTICS-EVENTS-001]
            promotion = false,
        )
        appPreferences.setPendingSpotRelease(
            // [DET-A-WITHDRAWAL-MUST-NAME-WHAT-IT-TAKES-BACK-001] El carril viaja EN el sello: es
            // lo único que permite que la retirada distinga la suya de la del otro carril.
            PendingSpotRelease(spotId = park.id, vehicleId = vehicleId, nominatedAtMs = nowMs(), lane = lane),
        )
        PaparcarLogger.d(
            TAG,
            "▶ plaza ${park.id.take(8)} published as RELEASING — somebody boarded $vehicleId " +
                "(${SpotTtlPolicy.RELEASING_SPOT_TTL_MS / 60_000} min to become real)",
        )
        // The park's own fix, so the trace says from WHERE the plaza was claimed and with what
        // accuracy — the same question every other spatial event in this lane answers for free.
        record(SpotReleaseAction.NOMINATED, park.id, lane, location = park.location)
        return true
    }

    /**
     * [DET-A-RELEASING-SPOT-MUST-SAY-IT-WENT-OUT-001] Every ending of this use case leaves one
     * event. Fire-and-forget by the logger's contract, and wrapped anyway: a diagnostics failure
     * must never change whether a plaza was published.
     *
     * A refusal with no session ([EvaluateSpotReleaseNominationUseCase.REASON_NO_PLAZA]) has no
     * parking document to hang from, so it goes under the daily trigger ledger — the same
     * out-of-session convention `Trigger` and `ProcessDeath` already use.
     */
    private suspend fun record(
        action: SpotReleaseAction,
        sessionId: String?,
        lane: String,
        reason: String? = null,
        location: GpsPoint? = null,
    ) {
        runCatching {
            detectionEventLogger.log(
                DetectionEvent.SpotRelease(
                    sessionId = sessionId ?: triggerLedgerSessionId(nowMs()),
                    timestampMs = nowMs(),
                    action = action,
                    lane = lane,
                    reason = reason,
                    location = location,
                ),
            )
        }
    }

    private companion object {
        const val TAG = "PARKDIAG/SpotRelease"

        /** The one refusal the evaluator never sees: the privacy read itself failed. */
        const val REASON_ZONES_UNREADABLE = "zones_unreadable"
    }
}

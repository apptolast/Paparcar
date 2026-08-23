package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.detection.DepartureSpeedVerdict
import io.apptolast.paparcar.domain.detection.classifyDepartureSpeed
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.domain.util.haversineMeters

/**
 * Outcome of [DetectParkingDepartureUseCase].
 *
 * - [Confirmed]   — all signals agree: the user drove away in their own car.
 * - [Rejected]    — a definitive "not a departure" (no session, wrong geofence ID,
 *                   or IN_VEHICLE_ENTER signal too old / from a previous trip).
 * - [Inconclusive]— session and geofence match, but either the IN_VEHICLE_ENTER
 *                   signal has not arrived yet, or it is present but GPS speed is
 *                   still below the departure threshold (user leaving a garage slowly).
 *                   `DepartureDetectionWorker` should retry after a short delay.
 */
sealed class DepartureDecision {
    data object Confirmed : DepartureDecision()
    data object Rejected : DepartureDecision()

    /**
     * Session and geofence match but the evidence is not conclusive yet — retry.
     *
     * [admissibleBoarding] tells the RETRY OWNER whether a valid IN_VEHICLE_ENTER backs this
     * exit: stamped AFTER this session began (a boarding older than the parking belongs to the
     * trip that CREATED it — field 2026-07-08 18:52, Redmi: a re-delivered ENTER from the
     * inbound drive "verified" a walking exit and erased a correct parking), within
     * [ParkingDetectionConfig.vehicleEnterWindowMs] of the exit, AND corroborated by physics —
     * the position must already have outrun pedestrian reach since the boarding
     * ([ParkingDetectionConfig.isBeyondPedestrianReach]); a phantom ENTER while walking made
     * the fall-through release a real spot (field 2026-07-09 11:55, Redmi). Only such a
     * boarding may authorise the attempts-exhausted fall-through (slow garage exits).
     * [DET-SESSION-BIRTH-001][DET-RIDE-PROOF-001]
     *
     * [reason] is a short telemetry breadcrumb for WHY this attempt stayed inconclusive when
     * that is not obvious from the logged speed alone — `exit_echo` marks a sample at credible
     * driving speed that was rejected for being the EXIT trigger's own fix (or its cache echo),
     * not an independent measurement. [DET-DEPART-PROOF-001]
     */
    data class Inconclusive(
        val admissibleBoarding: Boolean = false,
        val reason: String? = null,
    ) : DepartureDecision()
}

/**
 * Decides whether a geofence-exit event should trigger the publication of
 * the parked spot as free.
 *
 * Combines three independent signals to minimise false positives:
 *
 * 1. **Saved parking session** — an active [UserParking] must exist.
 * 2. **Geofence ID match** — the exited geofence must match the active session,
 *    preventing stale or mismatched events from triggering a report.
 * 3. **IN_VEHICLE_ENTER time window** — the user must have entered a vehicle
 *    within [ParkingDetectionConfig.vehicleEnterWindowMs] of the geofence exit.
 *    This is the key discriminator between "drove away" and "went for a walk".
 * 4. **Speed** (optional) — when a GPS reading is available, speed must exceed
 *    [ParkingDetectionConfig.minimumDepartureSpeedKmh] with credible accuracy AND the sample
 *    must postdate the exit by [ParkingDetectionConfig.departureProofMinGapMs]: one fix must
 *    never both fire the EXIT and confirm the departure — an indoor mirage did exactly that
 *    and published a phantom freed spot (field 2026-07-27). [DET-DEPART-PROOF-001]
 *
 * When signal 3 has not yet arrived and speed is insufficient, returns
 * [DepartureDecision.Inconclusive] instead of [DepartureDecision.Rejected] so
 * that the caller can retry — the Transitions API can take up to
 * ~2 minutes to deliver IN_VEHICLE_ENTER after the geofence fires.
 *
 * **Known limitation:** a user who boards a taxi immediately adjacent to their
 * parked car may still produce a false positive — Android's Activity Recognition
 * cannot distinguish the user's own vehicle from any other. This edge case is
 * accepted: the consequence (spot briefly published as free) is minor compared to
 * the benefit of reliable departure detection for the common case.
 */
class DetectParkingDepartureUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val departureEventBus: DepartureEventBus,
    private val config: ParkingDetectionConfig,
) {
    companion object {
        private const val TAG = "DetectParkingDepartureUseCase"
        private const val KMH_PER_MPS = 3.6f

        /** [DepartureDecision.Inconclusive.reason]: credible driving speed rejected because the
         *  sample is the exit trigger's own fix, not an independent one. [DET-DEPART-PROOF-001] */
        const val REASON_EXIT_ECHO = "exit_echo"
    }

    /**
     * @param geofenceId        ID of the geofence that fired the exit transition.
     * @param exitTimestampMs   Epoch-ms of the geofence exit event.
     * @param currentFix        The freshly sampled fix, or null if unavailable. Travels WHOLE:
     *        speed without credible accuracy is NOT movement evidence — a single acc=100 m cache
     *        jump faked 21.6 km/h on a motionless phone and confirmed a departure (field
     *        2026-07-08 04:18, Oppo) — and the position is the ENTER corroboration input
     *        ([ParkingDetectionConfig.isBeyondPedestrianReach]). Same canonical rules as the
     *        pre-arm verifier and the safety-net evaluator. [DET-EXIT-TRUST-001][DET-RIDE-PROOF-001]
     * @return [DepartureDecision] indicating whether to publish, skip, or retry.
     */
    suspend operator fun invoke(
        geofenceId: String,
        exitTimestampMs: Long,
        currentFix: GpsPoint?,
    ): DepartureDecision {
        val currentSpeedKmh = currentFix?.speed?.times(KMH_PER_MPS)
        val currentAccuracyM = currentFix?.accuracy
        // Signals 1+2: an active parking session must exist *for this exact geofence*.
        // Looking up by geofenceId (instead of fetching the single active session and
        // comparing) is correct under multi-parking — different vehicles can each have
        // their own active session, each tied to its own geofence. [MULTI-PARKING-001]
        val session = userParkingRepository.getActiveSessionByGeofence(geofenceId)
            ?: run {
                PaparcarLogger.w(TAG, "departure rejected: no active session for geofenceId=$geofenceId")
                return DepartureDecision.Rejected
            }

        // [DET-SESSION-BIRTH-001] A boarding that PREDATES this parking session is the boarding
        // of the trip that created it (or an OEM re-delivery of one), not evidence of leaving it.
        // Treat it as ABSENT — the decision falls back to speed, exactly as if AR had said
        // nothing. Field 2026-07-08 18:52 (Redmi): MIUI re-delivered the inbound drive's ENTER
        // (trueTime 17 min old) seconds after the park; 23 s later the fresh fence's walking
        // EXIT was "verified" by it and the correct parking was erased.
        val sessionStartMs = session.location.timestamp
        val vehicleEnteredAt = departureEventBus.lastVehicleEnteredAt?.takeIf { enteredAt ->
            val admissible = enteredAt >= sessionStartMs
            if (!admissible) {
                PaparcarLogger.w(TAG, "IN_VEHICLE_ENTER predates the session (enter=$enteredAt < sessionStart=$sessionStartMs) — not departure evidence, ignoring [DET-SESSION-BIRTH-001]")
            }
            admissible
        }
        // [DET-DEPART-PROOF-001] Independence gate: the confirming speed sample must be a
        // genuinely NEW measurement taken well after the exit event — one fix must never both
        // FIRE the exit and "confirm" the departure. Field 2026-07-27 18:30 (Oppo, at home,
        // stationary): a single indoor mirage (14 km/h, acc 21 m, 121 m away) broke the fence,
        // and 140 ms later the worker's getOneLocation returned the SAME cached fix, which
        // passed the credible-speed rule and published a phantom freed spot at the living room.
        // A real driver is still at driving speed on the retry samples (~45 s), so the only cost
        // is one extra retry; observed mirage bursts die within ~10 s and never survive the gap.
        // Same invariant as the coordinator's drive proof: the corroboration is the track, not
        // one Doppler sample. [DET-DRIVE-PROOF-001]
        // [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001] The rule itself now lives in
        // `classifyDepartureSpeed` — shared with the ARM lane, which used to carry an
        // independence-blind copy and armed a session `verified_speed` on a fix this very
        // evaluator was calling `exit_echo` 760 ms later (field 2026-08-22 20:50, Oppo).
        val speedVerdict = classifyDepartureSpeed(
            config = config,
            speedKmh = currentSpeedKmh,
            accuracyM = currentAccuracyM,
            fixTimestampMs = currentFix?.timestamp,
            eventTimestampMs = exitTimestampMs,
        )
        val speedConfirmsMovement = speedVerdict == DepartureSpeedVerdict.Independent
        val inconclusiveReason = if (speedVerdict == DepartureSpeedVerdict.Echo) {
            PaparcarLogger.w(
                TAG,
                "credible driving speed REJECTED as exit echo — fix.ts=${currentFix?.timestamp} " +
                    "exit.ts=$exitTimestampMs gap=${currentFix?.timestamp?.minus(exitTimestampMs)}ms " +
                    "< ${config.departureProofMinGapMs}ms [DET-DEPART-PROOF-001]",
            )
            REASON_EXIT_ECHO
        } else {
            null
        }

        return if (vehicleEnteredAt != null) {
            // Signal 3: IN_VEHICLE_ENTER is present — validate the time window. STRICT ordering:
            // the boarding must PRECEDE the exit. The bus carries TRUE transition times (the
            // receiver converts elapsedRealTimeNanos → epoch), so an ENTER stamped after the exit
            // is a vehicle boarded OUTSIDE the radius (bus/taxi after walking out) — not evidence
            // that the user drove their car away. [DET-SOLID-001]
            val enterToExitMs = exitTimestampMs - vehicleEnteredAt
            if (enterToExitMs !in 0..config.vehicleEnterWindowMs) {
                // From a previous trip (too old) or from AFTER the exit — ignore it.
                PaparcarLogger.w(TAG, "departure rejected: IN_VEHICLE_ENTER outside window — enterToExit=${enterToExitMs}ms window=${config.vehicleEnterWindowMs}ms geofenceId=$geofenceId")
                DepartureDecision.Rejected
            } else if (speedConfirmsMovement) {
                DepartureDecision.Confirmed
            } else {
                // IN_VEHICLE_ENTER within the window but no credible driving speed on this
                // sample (tight space, garage ramp — or a phantom ENTER while walking).
                // [DET-RIDE-PROOF-001] The ENTER alone never confirms: it is a NOMINATION and
                // only measured movement is proof. It stays *admissible* for the
                // attempts-exhausted fall-through only while physics corroborate it — the
                // position must already have outrun pedestrian reach since the boarding. A
                // phantom ENTER while the user walked to the hairdresser's passed the old
                // null-speed branch and the fall-through released a real spot (field
                // 2026-07-09 11:55, Redmi). Retrying (not rejecting) keeps sampling: a real
                // slow exit accelerates past the threshold or outruns the bound within the
                // retry window; a walking exit never does either.
                val admissible = currentFix != null && config.isBeyondPedestrianReach(
                    distanceMeters = haversineMeters(
                        currentFix.latitude,
                        currentFix.longitude,
                        session.location.latitude,
                        session.location.longitude,
                    ),
                    elapsedMs = (currentFix.timestamp.takeIf { it > vehicleEnteredAt } ?: exitTimestampMs) - vehicleEnteredAt,
                    fenceRadiusMeters = config.geofenceRadiusFor(session.sizeCategory, session.location.accuracy),
                    accuracyMeters = currentFix.accuracy,
                )
                DepartureDecision.Inconclusive(admissibleBoarding = admissible, reason = inconclusiveReason)
            }
        } else {
            // Signal 3 not available (or inadmissible). Fall back to speed as sole discriminator.
            // If speed is also inconclusive, ask the caller to retry — IN_VEHICLE_ENTER
            // can arrive up to ~2 minutes after the geofence exit on some devices.
            if (speedConfirmsMovement) {
                DepartureDecision.Confirmed
            } else {
                DepartureDecision.Inconclusive(admissibleBoarding = false, reason = inconclusiveReason)
            }
        }
    }
}

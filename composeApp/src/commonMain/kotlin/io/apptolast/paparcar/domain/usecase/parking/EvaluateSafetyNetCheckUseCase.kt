package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.util.haversineMeters

/**
 * Action decided by [EvaluateSafetyNetCheckUseCase] for one parked session. [DET-SAFETY-NET-001]
 */
sealed class SafetyNetAction {

    /** The fix is INSIDE the session's own geofence radius — re-register the geofence so Play
     *  Services' internal INSIDE/OUTSIDE state is rebuilt from the fresh fix. A prior false EXIT
     *  (walking away, WiFi-positioning drift) leaves that state OUTSIDE; without an *observed*
     *  re-entry the next real drive-away never produces an EXIT transition (field incident
     *  2026-07-04, Calle Gavia: the departure after a dismissed walking-EXIT was silent). */
    data class CureGeofence(val geofenceId: String, val radiusMeters: Float) : SafetyNetAction()

    /** Far from the parked car with a fresh position anchor (the phone was seen INSIDE this
     *  car's fence recently) AND proof the movement was a vehicle trip — either live (credible
     *  driving-speed fix, [preconfirmed] = false: the departure worker re-verifies by speed) or
     *  reconstructed after the fact by the step budget / AR boarding / exit∧enter conjunction /
     *  pedestrian-physics verdict ([preconfirmed] = true: the trip is already over, a speed
     *  re-check would wrongly veto it — the worker must skip straight to processing).
     *  [DET-RECONCILE-001]
     *
     *  [tripStartedAtMs] is the evaluator's best estimate of when the vehicle actually left —
     *  the AR boarding stamp when that is the evidence, else the anchor seal (the last moment
     *  the phone was provably at the car). The dispatcher dates the exit with it so the
     *  spot-publish freshness gate measures the real age of the freed spot, not the age of this
     *  wake-up. Null for live dispatches (the departure is happening NOW). */
    data class DispatchDeparture(
        val geofenceId: String,
        val preconfirmed: Boolean = false,
        val tripStartedAtMs: Long? = null,
        /** The step delta the evaluator actually TRUSTED for this verdict — null when the
         *  counter was mute or frozen-suspect ([DET-RIDE-PROOF-001]). Position-bounding
         *  decisions downstream (parking backfill) must use THIS, never the raw reading: a
         *  frozen counter's raw delta of 0 "bounds" the user to the car while they walk away
         *  (field 2026-07-09 12:39, Redmi: phantom backfill at the wake-up fix). */
        val trustedStepsSinceAnchor: Long? = null,
    ) : SafetyNetAction()

    /** Far from the parked car, MOVING at driving speed, but WITHOUT a fresh anchor — in a vehicle
     *  that was boarded away from the car (bus / taxi / lift, or a real drive-away whose anchor
     *  expired). Distance + speed alone can't tell those apart (BUG-WALK-DEPART-001), so the user
     *  disambiguates via the "still parked?" prompt; ignoring it leaves the session untouched. NB:
     *  far + STATIONARY is [None], not this — being parked-and-away on foot must never prompt. */
    data class PromptStillParked(val geofenceId: String) : SafetyNetAction()

    /** Nothing to act on: no geofence on the session, or the fix sits in the ambiguous ring
     *  between the geofence radius and the far threshold (GPS noise territory). The sampled fix
     *  has already done the passive half of the job — feeding the fused provider so the
     *  geofencing engine's state stays fresh. */
    data object None : SafetyNetAction()
}

/**
 * Pure decision core of the parked-session safety net. [DET-SAFETY-NET-001]
 *
 * Runs on every safety-net wake-up (15-min periodic worker, significant-motion hardware trigger)
 * while a session is parked and detection is idle. The caller samples ONE active fix and this
 * evaluator maps it to the action for each active session:
 *
 *  - inside the fence        → [SafetyNetAction.CureGeofence] (reset a possibly-poisoned fence)
 *  - far + driving + anchor  → [SafetyNetAction.DispatchDeparture] (recover a missed EXIT, live)
 *  - far + driving, no anchor→ [SafetyNetAction.PromptStillParked] (human disambiguates; never auto)
 *  - far + stationary + anchor + ride proof (steps ≪ distance/stride, or AR boarding after the
 *    seal, or pedestrian-physics)
 *                            → [SafetyNetAction.DispatchDeparture] preconfirmed (the trip already
 *                              happened while we slept) [DET-RECONCILE-001][DET-EXIT-TRUST-001]
 *  - far + STATIONARY, steps compatible with walking (or no anchor)
 *                            → [SafetyNetAction.None] (parked-and-away on foot — never nag)
 *  - ambiguous ring          → [SafetyNetAction.None]
 *
 * The whole point of this layer is that it does NOT depend on Play Services delivering anything:
 * the wake-ups are ours (WorkManager + sensor hub) and the decision inputs are ours (fix, step/AR
 * bus). The geofence EXIT stays the fast path; this is the guarantee that a lost EXIT can no
 * longer stall the detection chain. Evidence rules mirror [VerifyDepartureEvidenceUseCase]:
 * speed must carry credible accuracy, and an IN_VEHICLE_ENTER must PRECEDE now within
 * [ParkingDetectionConfig.vehicleEnterWindowMs].
 *
 * **The position anchor.** What makes a geofence EXIT trustworthy is not the evidence — it is
 * WHERE it fires: only when crossing the boundary of *your own car's* fence, so "vehicle motion
 * right after" almost certainly means your car. A periodic wake-up has no such anchoring: at
 * tick time the user can be doing 40 km/h in a bus 2 km from the parked car. Auto-dispatching on
 * evidence alone would re-open exactly the bus/taxi false positive the geofence design killed.
 * So [SafetyNetAction.DispatchDeparture] additionally requires [lastSeenNearCarAtMs]: the caller
 * records when a safety-net fix last landed INSIDE this session's fence, and only a departure
 * whose movement provably started at the car (near-recently + evidence) auto-releases. This is
 * the same risk envelope as the geofence EXIT itself (a bus crossing your own fence fools both —
 * the accepted limitation documented on [DetectParkingDepartureUseCase]); everything weaker
 * degrades to the human prompt.
 */
class EvaluateSafetyNetCheckUseCase(
    private val config: ParkingDetectionConfig,
) {
    /**
     * @param session             An ACTIVE parked session (caller iterates 0..N of them).
     * @param fix                 The freshly sampled fix (caller skips the check when none).
     * @param lastSeenNearCarAtMs Epoch-ms of the last safety-net fix INSIDE this session's fence
     *                            (the position anchor), or null when never observed / lost.
     * @param nowMs               Wall-clock now (epoch-ms).
     * @param stepsSinceAnchor    Cumulative-step-counter delta since the anchor was written, or
     *                            null when unknown (no hardware, read timeout, no counter sample
     *                            stored with the anchor, or a reboot reset the counter). The
     *                            caller must map a NEGATIVE delta to null. [DET-RECONCILE-001]
     * @param lastVehicleEnteredAtMs Epoch-ms of the last AR IN_VEHICLE ENTER (true transition
     *                            time from the bus), or null when none / unknown. Third proof
     *                            of a ride when the step counter is mute: a boarding stamped
     *                            AFTER the anchor seal and recently means the vehicle movement
     *                            started AT the car. [DET-EXIT-TRUST-001]
     * @param exitDeliveredAtMs   Epoch-ms when a geofence EXIT for THIS session was DELIVERED far
     *                            from its fence (recorded by the trust triage instead of granting
     *                            it departure authority), or null when none. Not positional trust —
     *                            just the fact "the OS says this fence broke", usable in
     *                            conjunction with an independent boarding. [DET-CONJUNCTION-001]
     */
    operator fun invoke(
        session: UserParking,
        fix: GpsPoint,
        lastSeenNearCarAtMs: Long?,
        nowMs: Long,
        stepsSinceAnchor: Long? = null,
        lastVehicleEnteredAtMs: Long? = null,
        exitDeliveredAtMs: Long? = null,
    ): SafetyNetAction {
        val geofenceId = session.geofenceId ?: return SafetyNetAction.None

        // ── Session-birth admissibility [DET-SESSION-BIRTH-001] ─────────────────────────────
        // Evidence older than the session itself describes the trip that CREATED this parking
        // (or an earlier life entirely) — it cannot prove departure FROM it. Field 2026-07-08:
        // a re-delivered IN_VEHICLE ENTER from the inbound drive (trueTime 17 min old) "verified"
        // a walking exit 23 s after the park and erased a correct parking. ONE filter, applied
        // to every evidence source, kills the whole class.
        val sessionStartMs = session.location.timestamp
        val boardingAtMs = lastVehicleEnteredAtMs?.takeIf { it >= sessionStartMs }
        val exitAtMs = exitDeliveredAtMs?.takeIf { it >= sessionStartMs }

        val distanceMeters = haversineMeters(
            fix.latitude,
            fix.longitude,
            session.location.latitude,
            session.location.longitude,
        )

        // Same size/accuracy-aware radius the geofence was registered with (SESSION-RESTORE-001),
        // so "inside" here means inside the REAL fence, not a flat default.
        val radiusMeters = config.geofenceRadiusFor(session.sizeCategory, session.location.accuracy)
        if (distanceMeters <= radiusMeters) {
            return SafetyNetAction.CureGeofence(geofenceId, radiusMeters)
        }

        // Accuracy-margin far gate: "far" must hold even if the fix erred by its own accuracy —
        // a single acc=100 m cache jump must not clear it (the 04:18 incident fix would need
        // d > 400 m). This also lets a mediocre-but-honest fix (acc 50-70 m at 1+ km) keep
        // feeding the evidence proofs below instead of being discarded by a flat cutoff
        // (field 2026-07-08 21:45, Redmi: acc=64 m at d=1222 m — unambiguously far).
        if (distanceMeters - fix.accuracy <= config.watchdogFarThresholdMeters) {
            return SafetyNetAction.None
        }

        val nearAgeMs = lastSeenNearCarAtMs?.let { nowMs - it }

        // ── Frozen-counter guard [DET-RIDE-PROOF-001] ────────────────────────────────────────
        // A delta of ~0 has two readings: "sat in the car the whole trip" (real drive) or "the
        // hardware counter is dead" (field 2026-07-09, Redmi: stuck at 307 all day while its own
        // session step DETECTOR counted 157 steps — the step budget then read a 354 m WALK as a
        // ride and backfilled a phantom parking). Physics disambiguates: a real drive's
        // displacement outruns pedestrian reach; a walkable displacement with a silent counter
        // is exactly what a dead counter produces. In that ambiguous case the counter loses its
        // voice ENTIRELY (delta AND anchor-freshness) and the mute-counter proofs decide.
        val counterFrozenSuspect = stepsSinceAnchor != null &&
            stepsSinceAnchor <= config.frozenCounterSuspectSteps &&
            nearAgeMs != null && nearAgeMs >= 0 &&
            !config.isBeyondPedestrianReach(distanceMeters, nearAgeMs, radiusMeters, fix.accuracy)
        val trustedStepsSinceAnchor = stepsSinceAnchor.takeUnless { counterFrozenSuspect }

        val timeFreshAnchor = nearAgeMs != null && nearAgeMs in 0..config.vehicleEnterWindowMs
        // [DET-RECONCILE-001] The anchor's real freshness clock is STEPS, not minutes. What the
        // anchor asserts is "the user was positionally AT the car" — and that assertion only
        // decays by WALKING away from it, which the hardware counter measures across kills and
        // Doze. A wall-clock window silently expires it while the user sits at a table 20 m from
        // the car (field 2026-07-07 22:41, Oppo: anchor 46 min old but only 113 steps walked
        // since — 4.3 km of displacement was blatantly a drive, vetoed purely by the clock; the
        // spot was never released). ≤ maxBoardingSteps since the seal ⇒ still at the car for all
        // decision purposes, hours later. The bus-stop-next-to-the-car residual this admits is
        // the SAME envelope the time window already accepted — it just no longer expires.
        // The time window remains for counter-less anchors (mute/absent hardware), whose only
        // evidence — pedestrian physics over nearAge — needs a time base anyway. Steps relax the
        // TIME requirement only — an anchor must still EXIST (the delta is only meaningful
        // relative to a seal moment; without one, few steps + far could be a bus boarded anywhere).
        val stepFreshAnchor = nearAgeMs != null && nearAgeMs >= 0 &&
            trustedStepsSinceAnchor != null && trustedStepsSinceAnchor <= config.maxBoardingSteps
        val anchoredToCar = timeFreshAnchor || stepFreshAnchor

        // Departure IN PROGRESS: a credible driving-speed fix far from the car. Position anchor
        // decides auto vs ask: the movement must have STARTED at the car (seen inside its fence
        // within vehicleEnterWindowMs) to auto-release — otherwise it's a vehicle boarded away
        // from the car (bus/taxi/lift) and only the user can disambiguate. Same bus/taxi risk
        // envelope as the geofence EXIT itself.
        val credibleDrivingSpeed =
            config.isCredibleDrivingSpeed(fix.speed * KMH_PER_MPS, fix.accuracy)
        if (credibleDrivingSpeed) {
            return if (anchoredToCar) {
                SafetyNetAction.DispatchDeparture(
                    geofenceId,
                    preconfirmed = false,
                    trustedStepsSinceAnchor = trustedStepsSinceAnchor,
                )
            } else {
                SafetyNetAction.PromptStillParked(geofenceId)
            }
        }

        // ── Conjunction proof [DET-CONJUNCTION-001] ─────────────────────────────────────────
        // Two independent OS events, both post-session, agreeing within a tight window: the
        // geofencing engine says THIS fence broke, and Activity Recognition says a vehicle was
        // boarded at essentially the same moment. Neither needs the position anchor — the fence
        // break IS the positional half (its boundary is the car), the boarding is the vehicular
        // half. This is the proof that survives the devices where every sense the other proofs
        // need is degraded (field 2026-07-08 cinema, Redmi: wake-up fixes always speed=0, step
        // counter frozen, anchor expired by clock — while EXIT 21:42 + ENTER 21:43 + d=1470 m
        // told the whole story). Walking out breaks the fence minutes before any later bus
        // boarding, so the pairing window rejects bus-after-walk; the live step counter, when
        // it speaks (and is trusted), still outranks this entirely (doctrine: steps are ground
        // truth). [DET-RIDE-PROOF-001] The pair alone is still two NOMINATIONS — AR misfires
        // ENTER while walking (field 2026-07-09 11:53, Redmi) and a walking exit can be
        // delivered late enough to be recorded here; the displacement must additionally have
        // outrun pedestrian reach since the boarding. The cinema trips this proof was built for
        // pass by a wide margin (1470 m in under 5 min); a walk to the hairdresser's never does.
        if (trustedStepsSinceAnchor == null && exitAtMs != null && boardingAtMs != null &&
            kotlin.math.abs(exitAtMs - boardingAtMs) <= config.exitEnterPairWindowMs &&
            config.isBeyondPedestrianReach(
                distanceMeters = distanceMeters,
                elapsedMs = nowMs - boardingAtMs,
                fenceRadiusMeters = radiusMeters,
                accuracyMeters = fix.accuracy,
            )
        ) {
            return SafetyNetAction.DispatchDeparture(
                geofenceId,
                preconfirmed = true,
                tripStartedAtMs = boardingAtMs,
            )
        }

        // Far and NOT at driving speed. [DET-RECONCILE-001] The trip may already be OVER — the
        // field-measured failure mode (2026-07-06, Oppo): the geofence EXIT arrives minutes late,
        // a 2-minute hop fits entirely inside the latency, and by the first wake-up the user is
        // parked at the destination. Catching the drive live is therefore not a guarantee anyone
        // can make. What survives the sleep is the hardware step counter: walking the observed
        // displacement MUST have produced ~distance/stride steps. A fresh anchor (was AT the car)
        // plus a step delta far below that proves the user was DRIVEN from their car — release
        // without asking. A step delta compatible with walking is the normal parked-and-left-on-
        // foot state and stays SILENT (no nag — SAFETYNET-STATIONARY-001 preserved).
        //
        // Doctrine when the counter is ALIVE and trusted: steps are the ground truth and nothing
        // overrides a walking verdict (bus-after-walk). When the counter is MUTE (null) or
        // frozen-suspect ([DET-RIDE-PROOF-001]): AR boarding, then pedestrian physics — each a
        // self-consistent proof with its own dating.
        if (fix.accuracy <= config.minGpsAccuracyForDriving) {
            if (trustedStepsSinceAnchor != null) {
                if (anchoredToCar) {
                    // Two conditions, not one: RELATIVE (steps ≪ what walking the displacement
                    // costs) proves a ride happened; ABSOLUTE (steps ≤ a fence-diameter's worth)
                    // proves the ride was boarded AT the car — 500 steps to a bus stop then a
                    // 5 km ride passes the relative check alone. [DET-RECONCILE-001]
                    val stepsToWalkHere = distanceMeters / config.strideMeters
                    if (trustedStepsSinceAnchor < stepsToWalkHere * config.walkedStepFraction &&
                        trustedStepsSinceAnchor <= config.maxBoardingSteps
                    ) {
                        return SafetyNetAction.DispatchDeparture(
                            geofenceId,
                            preconfirmed = true,
                            tripStartedAtMs = lastSeenNearCarAtMs,
                            trustedStepsSinceAnchor = trustedStepsSinceAnchor,
                        )
                    }
                }
            } else {
                // No step budget (no hardware / mute counter / read timeout / reboot). Two
                // independent ride proofs remain (besides the anchor-free conjunction above):
                //  - AR BOARDING: an IN_VEHICLE ENTER stamped AFTER the anchor seal and recently.
                //    Seal says "was AT the car", the ENTER says "then boarded a vehicle" — together
                //    they prove the displacement was a ride that started at the car. This absorbs
                //    the departure worker's old AR fall-through INTO the brain, WITH the anchor
                //    requirement the fall-through lacked (an unanchored ENTER is any bus in town).
                //    Dating the trip to the boarding keeps a short hop publishable (field
                //    2026-07-07 12:14, Redmi: 2-min 576 m hop, mute counter — physics over the
                //    14-min-old seal can't see it; the 12:12 boarding can). [DET-EXIT-TRUST-001]
                //  - PEDESTRIAN PHYSICS: a sustained average speed no walker reaches.
                val arBoardingAtCar = anchoredToCar &&
                    boardingAtMs != null &&
                    lastSeenNearCarAtMs != null &&
                    boardingAtMs >= lastSeenNearCarAtMs &&
                    (nowMs - boardingAtMs) in 0..config.vehicleEnterWindowMs
                if (arBoardingAtCar) {
                    return SafetyNetAction.DispatchDeparture(
                        geofenceId,
                        preconfirmed = true,
                        tripStartedAtMs = boardingAtMs,
                    )
                }
                if (anchoredToCar && nearAgeMs != null && nearAgeMs > 0) {
                    val averageSpeedMps = distanceMeters / (nearAgeMs / 1000.0)
                    if (averageSpeedMps > config.maxPedestrianSpeedMps) {
                        return SafetyNetAction.DispatchDeparture(
                            geofenceId,
                            preconfirmed = true,
                            tripStartedAtMs = lastSeenNearCarAtMs,
                        )
                    }
                }
            }
        }
        return SafetyNetAction.None
    }

    private companion object {
        const val KMH_PER_MPS = 3.6f
    }
}

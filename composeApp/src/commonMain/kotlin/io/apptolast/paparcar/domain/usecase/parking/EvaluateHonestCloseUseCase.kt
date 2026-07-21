package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.util.haversineMeters

/**
 * What the honest-close ladder decides for a detection session that is about to ABORT
 * (`aborted_false_enter` / `aborted_no_movement`) while a stale pin from a previous parking is
 * still active. [DET-HONEST-CLOSE-001]
 *
 * The doctrine: *no session armed by a real trigger may end in SILENCE when the car provably
 * drove away from its last pin* — it either leaves an artifact (approximate pin / zone) or asks.
 * The two dominant field aborts (Camelias hop 2026-07-14, D2 return 2026-07-15) both ended silent,
 * discarding the departure evidence the session already held: the stale pin went rancid, the car
 * ended with no pin, and nothing was asked.
 *
 * This is the SAME ride-proof question the parked-session safety net answers on its wake-ups
 * ([EvaluateSafetyNetCheckUseCase]) — "did the car drive away, or did the person walk?" — run
 * IMMEDIATELY at the abort from the live foreground service instead of waiting for the 15-min
 * worker Doze holds for hours ("the prompts only appear when you open the app"). It reuses the
 * same step-budget language: a displacement the counted steps cannot explain on foot was ridden.
 */
sealed interface HonestCloseDecision {

    /** Rung 1 — trip proven AND a pin-grade fix is in hand: release the stale pin and drop an
     *  APPROXIMATE pin here (LOW reliability, never auto-published). A point, but a soft one. */
    data class ApproximatePin(val location: GpsPoint) : HonestCloseDecision

    /** Rung 2 — trip proven but NO pin-grade anchor (urban multipath at the new spot): release the
     *  stale pin and open an APPROXIMATE ZONE — an AREA of [radiusMeters], never a deceptively
     *  precise dot. The chain never breaks (the zone carries a fence); the prompt asks to refine. */
    data class ApproximateZone(val center: GpsPoint, val radiusMeters: Float) : HonestCloseDecision

    /** Rung 3 — no ride proof: the walk explains the distance (the car never moved), the
     *  displacement is GPS wobble, the counter is mute, or there is no stale pin to reason about.
     *  Stay silent, keep the old pin intact — nagging here would assert the car is where the
     *  PEDESTRIAN is (BUG-WALK-DEPART-001). */
    data object KeepSilent : HonestCloseDecision
}

/**
 * Pure decision core of the honest-close ladder. [DET-HONEST-CLOSE-001]
 *
 * Deliberately primitives in, sealed decision out — no coroutines, no repositories — so the
 * ladder is replayable from a recorded abort and unit-tested in isolation. The coordinator owns
 * the side effects (releasing the stale pin, creating the zone/pin session, firing the nudge,
 * stamping the outcome).
 *
 * **The trip-proof gate.** Distance alone cannot tell "I drove here" from "I walked here" — the
 * D2 return (2026-07-15) delivered its fresh exit fix where the PEDESTRIAN stood 1.1 km from the
 * still-parked car. So the gate is the hardware step counter's delta since the stale pin was
 * sealed (mirror of [EvaluateSafetyNetCheckUseCase]): walking the displacement MUST have produced
 * ~distance/stride steps. A delta far below that proves a ride; a delta that matches is the normal
 * parked-and-walked-away state and stays silent.
 *
 * **Mute counter (null steps).** Conservatively → [HonestCloseDecision.KeepSilent]. Without steps
 * this evaluator cannot separate a ride from a long walk, and the doctrine is asymmetric (a false
 * negative here costs a late prompt from the safety-net, which still runs; a false zone plants a
 * wrong area). The AR-boarding / pedestrian-physics proofs for mute counters are a documented
 * follow-up — the safety net remains the backstop.
 */
class EvaluateHonestCloseUseCase(
    private val config: ParkingDetectionConfig,
) {
    /**
     * @param stalePin        The vehicle's still-active parked session (the pin the ladder may
     *                        release), or null when there is none to reason about.
     * @param abortFix        The position at the abort moment (the candidate new spot) + accuracy.
     * @param stepsSinceStalePin Hardware cumulative-counter delta since [stalePin] was sealed, or
     *                        null when the counter is mute / unknown (caller maps a negative delta
     *                        to null). NOT the session's own step-detector count — that resets per
     *                        session and never saw the drive that preceded the arm.
     */
    operator fun invoke(
        stalePin: UserParking?,
        abortFix: GpsPoint,
        stepsSinceStalePin: Long?,
    ): HonestCloseDecision {
        val pin = stalePin ?: return HonestCloseDecision.KeepSilent

        val distanceMeters = haversineMeters(
            pin.location.latitude, pin.location.longitude,
            abortFix.latitude, abortFix.longitude,
        )

        // Too close to be a trip: within both accuracy envelopes plus the floor, this is a re-arm
        // jitter beside the parked car, not a drive-away. Keep the pin, stay silent.
        if (distanceMeters <= pin.location.accuracy + abortFix.accuracy + config.honestCloseMinTripMeters) {
            return HonestCloseDecision.KeepSilent
        }

        // Mute counter → cannot prove a ride (nor rule out a long walk). Conservative silence;
        // the safety net's mute-counter proofs (AR boarding, pedestrian physics) are the backstop.
        val steps = stepsSinceStalePin ?: return HonestCloseDecision.KeepSilent

        // Trip-proof gate: walking the displacement costs ~distance/stride steps. A delta at or
        // above walkedStepFraction of that is a WALK (the car never moved) → rung 3. Below it, the
        // steps cannot account for the distance → the car was driven → rung 1/2.
        val stepsToWalkHere = distanceMeters / config.strideMeters
        val walkExplainsIt = steps >= stepsToWalkHere * config.walkedStepFraction
        if (walkExplainsIt) return HonestCloseDecision.KeepSilent

        // Trip proven. A pin-grade fix (same bar the reconcile backfill trusts) earns a point;
        // anything vaguer becomes an honest AREA rather than a precise lie.
        return if (abortFix.accuracy <= config.minGpsAccuracyForDriving) {
            HonestCloseDecision.ApproximatePin(abortFix)
        } else {
            val radiusMeters = maxOf(abortFix.accuracy, config.honestCloseMinZoneRadiusMeters)
            HonestCloseDecision.ApproximateZone(abortFix, radiusMeters)
        }
    }
}

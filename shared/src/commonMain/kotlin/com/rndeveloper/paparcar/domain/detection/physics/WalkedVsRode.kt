package com.rndeveloper.paparcar.domain.detection.physics

import kotlin.math.ceil

/**
 * [DET-RIDE-PROOF-001] **Did the body walk this distance, or was it carried?**
 *
 * The hardware step counter is the one witness that survives sleep, process death and OEM
 * batching — so when the app wakes up far from where it parked, the question is always the same:
 * walking that displacement MUST have cost roughly `distance / stride` steps. A delta compatible
 * with that is a person who walked; a delta far below it is a person who was driven.
 *
 * Two use cases ask it, at different moments and for different verdicts, and both KDocs already
 * described themselves as *"mirror of"* the other (06 §3-c):
 *  - `EvaluateHonestCloseUseCase` closing an abort — did the car move, or did the user walk away?
 *  - `EvaluateSafetyNetCheckUseCase` reconciling — may this spot be released without asking?
 *
 * **They stay two verdicts.** Merging the use cases does not hold: one produces nine typed reasons
 * for closing an abort, the other picks between cure / dispatch / prompt / silence, from different
 * inputs at different moments. What they share is this arithmetic, and only this.
 *
 * **What is deliberately NOT here:** the counter-health guards — stale seal
 * [DET-TRIP-WITNESS-001], mute counter, frozen counter [DET-FROZEN-COUNTER-001], missing seal
 * origin [DET-STEP-BUDGET-ORIGIN-001]. Each owner handles them with its own vocabulary (the
 * honest close returns a typed reason per case; the safety net routes to different proofs), and
 * folding them in here would change those verdicts rather than move them.
 *
 * ⚠️ **The origin is a parameter for a reason.** Field 2026-07-22 01:47 (Redmi, Glorieta): the
 * budget was measured from the PIN while the counter had been zeroed mid-egress ~159 m away, so
 * the remaining ~83 m walk cost ~110 steps against the 129 the pin's distance demanded — a walk
 * home read as a ride, and the pin landed inside the user's house. Both sides of the comparison
 * must share an origin. Pass the distance from wherever the counter was actually sealed.
 */

/**
 * Steps that walking [distanceMeters] must have produced to count as walked, rounded UP.
 *
 * [walkedStepFraction] is the tolerance: a real walk under-logs (pocket carries, phone in hand,
 * OEM batching), so the bar sits at a fraction of the geometric count rather than at it.
 */
fun requiredStepsToWalk(distanceMeters: Double, strideMeters: Float, walkedStepFraction: Float): Long =
    ceil((distanceMeters / strideMeters) * walkedStepFraction).toLong()

/**
 * TRUE when [steps] can account for [distanceMeters] on foot — the body walked here.
 *
 * FALSE is the ride proof: the steps cannot explain the displacement the body actually made, so
 * something carried it.
 *
 * **On the two forms this replaces.** One caller compared against `ceil(...)` and the other against
 * the raw `Double`. Those are the same test, because step counts are integers: for integer `n` and
 * real `x`, `n >= ceil(x)` ⟺ `n >= x` (forward: `n >= ceil(x) >= x`; back: `n >= x` with `n`
 * integer gives `n >= ceil(x)`). Both callers also build the same `Double` from the same operands,
 * so there is no float-precision gap between them either. Unifying on the rounded form is exact,
 * not approximate — and [WalkedVsRodeTest] pins that equivalence so it cannot be re-litigated by
 * inspection later.
 */
fun walkExplainsDisplacement(
    steps: Long,
    distanceMeters: Double,
    strideMeters: Float,
    walkedStepFraction: Float,
): Boolean = steps >= requiredStepsToWalk(distanceMeters, strideMeters, walkedStepFraction)

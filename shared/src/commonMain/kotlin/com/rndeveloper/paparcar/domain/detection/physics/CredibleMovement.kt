package com.rndeveloper.paparcar.domain.detection.physics

import com.rndeveloper.paparcar.domain.model.GpsPoint

/**
 * [LOC-002] **Is this fix's speed worth believing at all?**
 *
 * A GPS receiver reports a Doppler speed with every fix and reports it just as confidently when it
 * has no idea where it is. Field 2026-07-04: brisk walking away from a parked car produced 2.5–3.6
 * m/s readings that wiped the true anchor, and the park re-anchored 55 m away where the user next
 * stood still. The accuracy envelope is the only thing separating a measurement from a story, so
 * **every decision that reads `speed` passes through here first**.
 *
 * The same comparison was written out five times in the coordinator (07 §2.2) — five places to
 * forget if the bar is ever qualified. The coordinator's own comment at the first of them called it
 * *"the same 50 m gate"*, which is the duplication describing itself.
 *
 * The threshold is a parameter rather than a constant so this file stays free of the config: the
 * two callers that use a **different** envelope (the reposition burst, which runs its own
 * `repositionMaxAccuracyMeters`) are not forced through a gate that is not theirs.
 */
fun isCredibleFixAccuracy(fix: GpsPoint, maxAccuracyMeters: Float): Boolean =
    fix.accuracy <= maxAccuracyMeters

/**
 * [LOC-002] A fix that is **both** moving at or above [speedBarMps] **and** accurate enough to be
 * believed — the composite that decides "the car is driving".
 *
 * The bar is a parameter because the project deliberately runs two of them: a loose one for
 * "movement worth reacting to" and a strict one for "real driving", chosen by whether the anchor is
 * pinned. Once egress steps are in hand only the strict bar may clear an anchor [ANCHOR-LOCK-001].
 *
 * ⚠️ **Not every `speed`-plus-accuracy pair in the coordinator is this predicate**, and two of them
 * deliberately stay out:
 *  - the post-confirm hold's *driving resumed* test compares **strictly greater** than its bar, not
 *    `>=`. The difference only shows at exactly the bar, but that decision discards a pin that has
 *    already earned its confirm, so the boundary is not something to change while claiming a pure
 *    move [DET-C-02];
 *  - the kinematic-egress counter wants the **pedestrian** band — speed *below* the trip bar with
 *    the same accuracy gate. It shares the gate, not the question [DET-KINEMATIC-EGRESS-001].
 *
 * Both call [isCredibleFixAccuracy] directly, which is the half they really share.
 */
fun isCredibleMovingFix(fix: GpsPoint, speedBarMps: Float, maxAccuracyMeters: Float): Boolean =
    fix.speed >= speedBarMps && isCredibleFixAccuracy(fix, maxAccuracyMeters)

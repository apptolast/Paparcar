package com.rndeveloper.paparcar.domain.detection.physics

/**
 * [DET-FROZEN-COUNTER-001][DET-GAP-ANCHOR-ZONE-001] **How big is the circle we are honest enough to
 * draw?**
 *
 * When the app knows the car is parked but not exactly where, it saves an AREA rather than a
 * deceptively precise dot. This is the radius of that area, and it answers to two witnesses of the
 * same doubt:
 *  - the centre's own **accuracy** — the fix could be off by that much on its own;
 *  - whatever bound the caller **measured** — metres walked since the counter was sealed, or metres
 *    a person could have covered inside a GPS hole.
 *
 * The circle takes the larger, then sits between a floor and a ceiling. Below the floor an "area" is
 * a dot with extra steps; above the ceiling it paints half a neighbourhood and stops meaning
 * anything. At the ceiling the artifact is still saved and the nudge becomes the ask-to-refine —
 * losing the park entirely is the worse answer.
 *
 * Three callers, one formula: the unattended timeout, the user's "Sí" over an anchor the session
 * cannot vouch for [DET-USER-YES-IS-NOT-A-COORDINATE-001], and the honest close's artifact
 * [DET-HONEST-CLOSE-001]. The last of those spelled it `coerceIn` while the other two spelled it
 * `minOf(max, maxOf(min, …))`; the same clamp, and a second spelling is how a radius gets fixed in
 * one place and forgotten in two.
 *
 * ⚠️ **Order of operations is preserved deliberately.** The doubt is narrowed to `Float` BEFORE the
 * comparisons, exactly as the coordinator did it, so the result is bit-identical to what shipped
 * rather than merely close. Do not "clean this up" into a Double-domain max.
 *
 * Note this clamps rather than requiring `floor <= ceiling`: with an inverted pair it yields the
 * ceiling instead of throwing, which is what the coordinator's spelling did.
 * `ParkingDetectionConfig` already `require`s the sane ordering at construction, so the two
 * spellings never diverged in practice.
 */
fun honestZoneRadius(
    centerAccuracyMeters: Float,
    doubtMeters: Double,
    floorMeters: Float,
    ceilingMeters: Float,
): Float = minOf(
    ceilingMeters,
    maxOf(floorMeters, centerAccuracyMeters, doubtMeters.toFloat()),
)

/**
 * [DET-INFERRED-PIN-CARRIES-ITS-DOUBT-001] **May an INFERRED pin claim to be exact at all?**
 *
 * An inferred confirm — one no human placed or ratified at a map — may not claim more precision
 * than the fix it stands on. Under the floor the answer is null: the fix is sharp enough that a
 * point says more than an area would. Past it, the claim must be an AREA of the fix's own accuracy
 * (via [honestZoneRadius], zero extra doubt — the accuracy IS the doubt being drawn).
 *
 * Field 2026-08-28 (Redmi): steps+egress re-anchored on a 92.9 m network fix and saved it as an
 * EXACT pin at reliability 0.9, one street over from the car — the same shape as the night's FP.
 * Two callers, one formula: the detection executor's confirm funnel (fast confirm, candidate, hold
 * settle, unattended exact) and the safety-net backfill, which reconstructs a pin with no live
 * session at all. User-ASSERTED pins (manual / nudge / in-app confirm) never ask this question —
 * a hand-placed pin is ground truth, whatever the phone's fix claimed.
 */
fun inferredPinDoubtRadius(
    fixAccuracyMeters: Float,
    floorMeters: Float,
    ceilingMeters: Float,
): Float? =
    if (fixAccuracyMeters <= floorMeters) null
    else honestZoneRadius(
        centerAccuracyMeters = fixAccuracyMeters,
        doubtMeters = 0.0,
        floorMeters = floorMeters,
        ceilingMeters = ceilingMeters,
    )

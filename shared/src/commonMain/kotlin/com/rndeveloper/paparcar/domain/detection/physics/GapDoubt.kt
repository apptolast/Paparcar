package com.rndeveloper.paparcar.domain.detection.physics

/**
 * [DET-GAP-ANCHOR-ZONE-001][DET-USER-YES-IS-NOT-A-COORDINATE-001] How far the phone could have walked
 * while the GPS stream was silent — the only bound a hole leaves on where the car actually is.
 *
 * A PREDICATE shared by two verdicts (the unattended-timeout save and the user's "Sí" when it
 * discards a gap-born anchor), so it lives here as a pure function rather than inside either of them
 * [DET-VERDICT-NOT-PREDICATE-001]. It was one expression inlined in the unattended evaluator until
 * the user path needed the identical arithmetic; two copies of a unit conversion is exactly how a
 * bound gets widened in one caller and forgotten in the other — the divergence the deep-refactor
 * audit logged as its ninth bug.
 *
 * Pedestrian pace on purpose, not driving pace: the question is how far the BODY could have carried
 * the phone away from the car during the silence, which is what makes the doubt boundable at all.
 *
 * @param gapMs Wall-clock silence between the last fix at real driving speed and the fix that opened
 *   the stop. Zero or negative means there was no hole and therefore no doubt to bound.
 * @param maxPedestrianSpeedMps The walking ceiling the rest of detection is calibrated against.
 */
fun walkableInsideGapMeters(gapMs: Long, maxPedestrianSpeedMps: Float): Double =
    if (gapMs <= 0L) 0.0 else gapMs / MILLIS_PER_SECOND * maxPedestrianSpeedMps

/** The gap taint is measured in ms; pedestrian pace in m/s. */
private const val MILLIS_PER_SECOND = 1_000.0

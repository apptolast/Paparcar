package com.rndeveloper.paparcar.domain.detection.physics

/**
 * [DET-MOTOR-PROOF-001][DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] How long a session has SUSTAINED a
 * speed band — the statistic that separates a motor from muscle where a peak cannot.
 *
 * A PREDICATE shared by two verdicts (the drive proof at `minimumTripSpeedMps`, the motor proof at
 * `motorProofSpeedMps`), so it lives here as a pure function rather than inside either of them
 * [DET-VERDICT-NOT-PREDICATE-001]. It was one clock inlined in the coordinator until the motor band
 * needed the identical arithmetic; two copies of a five-line accumulator is exactly how a signal
 * gets fixed in one band and forgotten in the other.
 *
 * **The rule:** credit the gap between SUCCESSIVE credible in-band fixes, and only when that gap
 * fits inside the span the drive-proof shape already trusts. A real drive's band run is punched
 * through by urban accuracy holes (field Enamorados) and a skeletal stream's whole drive can be a
 * single 36-s hop (field Calle Gavia), so the window must tolerate holes — but a wider gap proves
 * nothing and credits nothing, and a lone spike has no in-band peer at all, so it credits nothing
 * either. That is what makes the clock immune to the Doppler mirage a peak swallows whole.
 *
 * @param accumulatedMs Time already credited to this band.
 * @param lastInBandFixMs Timestamp of the previous in-band fix, or 0 if there has not been one.
 * @param fixTimestampMs Timestamp of the fix being judged.
 * @param fixInBand Whether that fix is credible AND at or above the band's floor.
 * @param windowMaxMs Widest gap between two in-band fixes that still counts as one run.
 * @return the new accumulated total; the caller advances [lastInBandFixMs] itself.
 */
fun creditSpeedBand(
    accumulatedMs: Long,
    lastInBandFixMs: Long,
    fixTimestampMs: Long,
    fixInBand: Boolean,
    windowMaxMs: Long,
): Long {
    if (!fixInBand || lastInBandFixMs <= 0L) return accumulatedMs
    val gapMs = fixTimestampMs - lastInBandFixMs
    return if (gapMs in 1..windowMaxMs) accumulatedMs + gapMs else accumulatedMs
}

/**
 * [DET-MOTOR-PROOF-001] **Did this session WITNESS a drive?** — the reading of [creditSpeedBand]'s
 * total that every "was there really a trip" question in the system asks.
 *
 * The same `>=` was spelled inline at three call sites — the parking decision's `sessionSawDriving`,
 * the assertion guard's escape hatch, and the confirm's repark sibling — each one free to drift into
 * reading a different quantity. The quantity is the load-bearing part: it must be the band the drive
 * proof already PROMOTED (`ParkingDetectionState.provenDrivingBandMs` / the evaluator's
 * `sustainedDrivingMs`), never the raw accumulator and never a PEAK. A peak is one sample and one
 * sample is a mirage — 5,33 m/s out of 25 fixes walked a guard on 2026-08-24
 * [DET-ASSERTION-OUTRANKS-INFERENCE-001].
 *
 * Trivial arithmetic with a name is the point: when the drive proof moves into its own sub-state,
 * what changes is what FEEDS this, in one place, instead of three comparisons agreeing by luck.
 *
 * @param provenBandMs Band time the track has corroborated — zero until it has.
 * @param proofMs `ParkingDetectionConfig.sustainedDriveProofMs`.
 */
fun sustainedDriveWitnessed(provenBandMs: Long, proofMs: Long): Boolean = provenBandMs >= proofMs

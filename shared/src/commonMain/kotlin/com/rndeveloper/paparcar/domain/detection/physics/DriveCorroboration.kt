package com.rndeveloper.paparcar.domain.detection.physics

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.util.haversineMeters

/**
 * [DET-CREDIBLE-DRIVE-001][DET-DRIVE-PROOF-001] **Corroboration by displacement** — believing the
 * track instead of the fix.
 *
 * Every function here exists because a single GPS fix is not evidence. Its `speed` field is a claim,
 * its `accuracy` is a claim, and both are made with equal confidence when the receiver is lost.
 * What cannot be faked is *ground actually covered between two positions*, so these predicates all
 * ask the same question in different time scales: **did the position move in a way no walker and no
 * cache glitch could produce?**
 *
 * The three scales are deliberately separate and stay separate:
 *  - [isCorroboratedVehicleHop] — one fix to the next. Used where the Doppler may not be trusted at
 *    all (the mute ambiguous band) and where a "stop" claims zero speed while moving.
 *  - [sustainedDepartureFromAnchor] — the anchor's stop to now. Unfreezes an anchor when the OEM
 *    starves every individual fix of credible accuracy.
 *  - [corroboratesDrive] — a bounded look-back window. The session's "measured driving" statistic.
 */

/**
 * [DET-DRIVE-PROOF-001] The bounds [corroboratesDrive] judges against, **and** the retention rule
 * [pruneRecentFixes] keeps the history by.
 *
 * They live in one object on purpose. The ring must hold fixes at least as old as the widest
 * look-back window or the window silently finds nothing to look back at — a drive that stops
 * proving itself, with no error anywhere. Bundling them makes that coupling something you have to
 * edit together rather than something you have to remember.
 */
data class DriveProofBounds(
    /** A look-back fix younger than this is too close to say anything. */
    val windowMinMs: Long,
    /** …and older than this is no longer about the current movement. */
    val windowMaxMs: Long,
    /** Pathology margin on top of both accuracy envelopes: GPS recovery swings reach ~68 m and
     *  double back (field 2026-07-15, Camelias-Oppo). */
    val hopMarginMeters: Float,
    /** Ground a real trip must have covered across the window. */
    val minDistanceMeters: Float,
    /** Above this the "movement" is a cache teleport claiming an absurd rate, not a drive. */
    val maxRateMps: Float,
    /** Fraction of the window's displacement the late-half fixes must already have left the
     *  look-back position by. A real drive progresses through its window; a mirage is
     *  flat-then-jump (field 2026-07-27: the phone sat at home for every in-window fix and
     *  "moved" only at the burst). */
    val progressFraction: Float,
    /** Extra age the ring keeps beyond [windowMaxMs] so a window never starves. */
    val retentionSlackMs: Long,
    /** Hard cap so a hot stream cannot grow the state without bound. */
    val maxRetainedFixes: Int,
)

/**
 * [DET-CREDIBLE-DRIVE-001] The position **provably hopped** from [prev] to [curr]: beyond both
 * accuracy envelopes plus [hopMarginMeters], at a ground rate no walker sustains.
 *
 * Declared Doppler is exactly what the mute band may not trust; a measured hop is independent
 * evidence. Field-calibrated on both sides: the Galeote deceleration passes (23.7 m in 5 s against
 * 9.9 m of joint accuracy — the car rolling to the kerb), and the Camelias walk-back recovery swing
 * fails every hop, because its envelopes balloon exactly when it "moves" (best case 11.9 m against
 * 14.1 m of noise) — which is what keeps the drag-to-home laundering impossible.
 */
fun isCorroboratedVehicleHop(
    prev: GpsPoint?,
    curr: GpsPoint,
    hopMarginMeters: Float,
    minRateMps: Float,
): Boolean {
    if (prev == null) return false
    val dtSeconds = (curr.timestamp - prev.timestamp) / 1000.0
    if (dtSeconds <= 0.0) return false
    val d = haversineMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
    if (d <= prev.accuracy + curr.accuracy + hopMarginMeters) return false
    return d / dtSeconds >= minRateMps
}

/** What a sustained departure measured, so the caller can say it. */
data class SustainedDeparture(val distanceMeters: Double, val rateMps: Double)

/**
 * [DET-CREDIBLE-DRIVE-001] The position has **RUN** from the anchor at sustained vehicle pace since
 * the anchor's stop began — or null when it has not.
 *
 * Believes no single fix, not its speed field and not its accuracy: the corroboration is the track.
 * The floor sits beyond both accuracy envelopes plus a pathology margin, and the rate window
 * `[minRateMps, maxRateMps]` excludes both the walk home (≤2 m/s average) and the cache teleport.
 * The current fix must itself be moving above walking pace — a pedestrian-band fix never carries
 * this verdict however far the anchor sits; that judgement belongs to the egress machinery.
 *
 * This is what unfreezes the anchor when the OEM starves every individual fix of credible accuracy
 * (field 2026-07-15, Enamorados: 10.12 m/s at accuracy 52.4 — the root of the 1.11 km false
 * positive).
 *
 * ## Two ceilings, because one of them cannot see time
 *
 * [DET-A-DEPARTURE-RATE-MUST-BE-PHYSICALLY-REACHABLE-001] [maxRateMps] is a flat bar, and a flat bar
 * cannot tell these two apart — both sit under it:
 *
 * | | rate | window | verdict |
 * |---|---|---|---|
 * | field 2026-08-26, Valdés→Góndola under OEM batching | 26.2 m/s | 163 s | a REAL drive |
 * | field 2026-08-31, Oppo, fifth second of the session | 40.5 m/s | 5.1 s | a cache teleport |
 *
 * So the second ceiling asks what the first cannot: **could the vehicle have got there from the
 * state the anchor declared?** `v0·t + ½·a·t²` with [maxAccelerationMps2]. It tightens as the window
 * shrinks, which is exactly the shape of the problem — a teleport's signature is covering ground in
 * no time, while the batched-stream case this function exists to tolerate has minutes of window and
 * clears the bound by three orders of magnitude.
 *
 * The measured distance is discounted by both accuracy envelopes before the comparison, the same way
 * the floor above adds them: this bar must refute the physically impossible, never a real drive-away
 * that GPS noise pushed a few metres over. Refusing wrongly is not the safe side here — an anchor
 * that fails to unfreeze is what planted the pin 1.11 km away at Enamorados.
 *
 * **Returns the measurement rather than a boolean** so the log stays where the numbers are known
 * without this function having to do I/O. That is the whole point of it living here.
 */
fun sustainedDepartureFromAnchor(
    anchor: GpsPoint,
    anchorStoppedSinceMs: Long,
    fix: GpsPoint,
    nowMs: Long,
    movingBarMps: Float,
    floorMeters: Float,
    minRateMps: Float,
    maxRateMps: Float,
    maxAccelerationMps2: Float,
): SustainedDeparture? {
    if (fix.speed < movingBarMps) return null
    val elapsedSeconds = (nowMs - anchorStoppedSinceMs) / 1000.0
    if (elapsedSeconds <= 0.0) return null
    val d = haversineMeters(anchor.latitude, anchor.longitude, fix.latitude, fix.longitude)
    val jointAccuracyMeters = anchor.accuracy + fix.accuracy
    if (d <= jointAccuracyMeters + floorMeters) return null
    val rate = d / elapsedSeconds
    if (rate < minRateMps || rate > maxRateMps) return null
    // The anchor's own declared speed is the starting point, so a stop that was rolling is not
    // judged as if it had been at rest. At Cañada it declared 0.0 m/s, which is what makes 207 m in
    // 5.1 s unreachable by a factor of four.
    val reachableMeters = anchor.speed * elapsedSeconds +
        0.5 * maxAccelerationMps2 * elapsedSeconds * elapsedSeconds
    if (d - jointAccuracyMeters > reachableMeters) return null
    return SustainedDeparture(distanceMeters = d, rateMps = rate)
}

/**
 * [DET-DRIVE-PROOF-001] The position **provably covered a trip's worth of ground** ending at [curr],
 * judged against a look-back fix inside the window.
 *
 * Field-calibrated on both correct traces: Calle Gavia's whole drive is ONE 36-second hop of 255 m
 * with no in-window witnesses (a sparse stream — it passes), and the OEM-starved Enamorados leg
 * proves itself across 25-second windows of ~200 m even though NO single hop ever escapes its joint
 * accuracy envelopes. The at-home mirage has no window at all: its burst died 10 s into the session.
 */
fun corroboratesDrive(history: List<GpsPoint>, curr: GpsPoint, bounds: DriveProofBounds): Boolean {
    val eligible = history.filter {
        (curr.timestamp - it.timestamp) in bounds.windowMinMs..bounds.windowMaxMs
    }
    return eligible.any { anchor ->
        val d = haversineMeters(anchor.latitude, anchor.longitude, curr.latitude, curr.longitude)
        val dtSeconds = (curr.timestamp - anchor.timestamp) / 1000.0
        val midTs = anchor.timestamp + (curr.timestamp - anchor.timestamp) / 2
        d > anchor.accuracy + curr.accuracy + bounds.hopMarginMeters &&
            d >= bounds.minDistanceMeters &&
            d / dtSeconds <= bounds.maxRateMps &&
            history.filter { it.timestamp in (midTs + 1) until curr.timestamp }.all {
                haversineMeters(anchor.latitude, anchor.longitude, it.latitude, it.longitude) >=
                    d * bounds.progressFraction
            }
    }
}

/**
 * [DET-DRIVE-PROOF-001] The bounded ring behind [corroboratesDrive]: keeps fixes young enough to
 * serve a future look-back window, hard-capped so a hot stream cannot grow the state.
 *
 * Takes the same [DriveProofBounds] as [corroboratesDrive] — see the type's own note on why they
 * are one object.
 */
fun pruneRecentFixes(history: List<GpsPoint>, curr: GpsPoint, bounds: DriveProofBounds): List<GpsPoint> =
    (history.filter { curr.timestamp - it.timestamp <= bounds.windowMaxMs + bounds.retentionSlackMs } + curr)
        .takeLast(bounds.maxRetainedFixes)

package com.rndeveloper.paparcar.domain.detection.physics

/**
 * [DET-A-USER-YES-DOES-NOT-SHRINK-A-WALK-ENTERED-DOUBT-001][DET-WALK-ENTERED-ANCHOR-ZONE-001] How
 * far the pedestrian had already walked when the anchor was captured — the bound on how wrong a
 * **walk-entered** anchor can be about where the car is.
 *
 * The sibling of [walkableInsideGapMeters], and it exists for the same reason that one does: this
 * arithmetic was inlined in `EvaluateUnattendedParkingSaveUseCase` until a second verdict needed the
 * identical bound, and two copies of a bound is how it gets widened in one caller and forgotten in
 * the other. It was forgotten in the other — `UserConfirmStage` consulted the GAP doubt and nothing
 * else, so a user's "Sí" over a walk-entered anchor saved an exact pin where the unattended timeout
 * on the same stream drew an area.
 *
 * Two witnesses, whichever spoke: **counted steps** when the counter was alive, and the **GPS span
 * of the walk-band run** that led into the capture, which exists whether or not it was. The larger
 * wins — they measure the same walk through different instruments, and the mute-counter case is
 * exactly the one that produced the field trace this closes.
 *
 * ⚠️ **A lower bound, not a measurement, and the difference is load-bearing.** On field 2026-07-15
 * (Oppo, Camelias) this returns **29,5 m** while the anchor was **37 m** from the car: the span of
 * the walk-band run is where the phone was SEEN walking, not where it started. So a caller may use
 * this to SIZE an area, and may not use "the number is small" to conclude the anchor is good enough
 * — that inference is what the exact pin was resting on.
 *
 * @param stepEventsAtCapture Step events counted during the walk-in; zero when the counter was mute.
 * @param walkInSpanMeters GPS span of the walk-band run leading into the capture.
 * @param strideMeters The stride the rest of detection converts step counts with.
 */
fun walkedInToAnchorMeters(
    stepEventsAtCapture: Int,
    walkInSpanMeters: Double,
    strideMeters: Float,
): Double = maxOf(stepEventsAtCapture * strideMeters.toDouble(), walkInSpanMeters)

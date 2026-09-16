package com.rndeveloper.paparcar.domain.detection.coordinator.replay

import com.rndeveloper.paparcar.domain.detection.coordinator.ingestion.TraceEvent

/**
 * [DET-A-TRAIN-IS-NOT-A-CAR-001] The long-distance rail journey that used to pin a terminus.
 *
 * ## ⚠️ SYNTHESISED, not exported — and why
 *
 * Every other trace in this package is generated 1:1 by `tools/trace2fixture` from a diagnostics
 * export. This one is built by [railJourney] from the *measured profile* of the 2026-09-15 session
 * (Samsung SM-A556B, `diagnostics/qRWE…/sessions/1789472756175`) rather than from its 1 616 events:
 *
 *  - 270 minutes, arm `GEOFENCE_EXIT` with the departure corroborated by its own fix;
 *  - a first hour on conventional track — the session's own fixes there sit around 140-150 km/h;
 *  - then high-speed line, `vmax 253 km/h (credible 253)` at 16-25 m of accuracy, 762 credible
 *    driving fixes out of 1 616;
 *  - arrival, a stop, and 191 steps of walking out of the station — the egress that confirmed
 *    `steps+egress` at 0,9 and planted the pin 600 km from the car.
 *
 * The profile is what this ticket turns on, so a generated stream reproduces the failure exactly
 * while staying readable — and, more importantly, it can be re-run at a different speed. That is
 * what [railJourney]'s scale parameter buys: the SAME geometry, timing, stop and egress walk at road
 * speeds is the control, and without it "no pin" would be a result a broken replay could also
 * produce. [project_test_a_green_suite_must_prove_it_looked_001]
 *
 * @param speedScale 1.0 replays the train. Below 1, every speed AND every distance shrink by the
 *   same factor, so the trip stays geometrically identical and only its physics change.
 */
fun railJourney(speedScale: Double = 1.0): List<TraceEvent> = buildList {
    val t0 = 1_789_472_756_175L
    val originLat = 36.6040534
    val lon = -6.2181624

    /** Due north, so the arithmetic that places each fix is one division. */
    fun fixAt(dtMs: Long, metresFromOrigin: Double, speedMps: Double, accuracy: Float) =
        add(
            TraceEvent(
                tMs = t0 + dtMs,
                kind = TraceEvent.Kind.FIX,
                lat = originLat + metresFromOrigin / METRES_PER_DEGREE,
                lon = lon,
                accuracy = accuracy,
                speed = (speedMps * speedScale).toFloat(),
            ),
        )

    var elapsedMs = 0L
    var metres = 0.0

    /** One constant-speed leg, sampled every [FIX_INTERVAL_MS] like the real stream. */
    fun leg(durationMs: Long, speedMps: Double, accuracy: Float) {
        var t = 0L
        while (t < durationMs) {
            fixAt(elapsedMs + t, metres, speedMps, accuracy)
            metres += speedMps * speedScale * (FIX_INTERVAL_MS / 1_000.0)
            t += FIX_INTERVAL_MS
        }
        elapsedMs += durationMs
    }

    // Conventional track out of the coastal station: fast, but nothing a motorway does not do.
    leg(durationMs = 60 * 60 * 1_000L, speedMps = 40.0, accuracy = 22f)
    // The high-speed line. This is the stretch the whole verdict rests on.
    leg(durationMs = 115 * 60 * 1_000L, speedMps = 70.0, accuracy = 19f)
    // Braking into the terminus.
    leg(durationMs = 3 * 60 * 1_000L, speedMps = 18.0, accuracy = 14f)

    // Stopped on the platform. Long enough for the stop to mature into a CANDIDATE — which is
    // exactly the state in which the real session confirmed.
    var t = 0L
    while (t < 12 * 60 * 1_000L) {
        fixAt(elapsedMs + t, metres, 0.0, 8f)
        t += FIX_INTERVAL_MS
    }
    elapsedMs += 12 * 60 * 1_000L

    // The walk out of the station: 191 steps and ~140 m of displacement, the egress proof that
    // made the pin. Everything this ticket does has to survive it being present and convincing.
    repeat(EGRESS_STEPS) { i ->
        val dt = elapsedMs + i * 1_500L
        add(TraceEvent(tMs = t0 + dt, kind = TraceEvent.Kind.STEP))
        if (i % 8 == 0) fixAt(dt, metres + i * 0.75, 1.1, 6f)
    }
}

/** The 2026-09-15 profile itself. */
val TRACE_RAIL_TO_TERMINUS_001: List<TraceEvent> = railJourney()

/**
 * The control: identical geometry, timing, stop and egress walk, at road speeds (peak 113 km/h,
 * inside the fastest drive the field corpus has ever recorded). If this one stops confirming, the
 * ceiling has started eating real parks.
 */
val TRACE_ROAD_TO_DESTINATION_001: List<TraceEvent> = railJourney(speedScale = 0.45)

private const val METRES_PER_DEGREE = 111_320.0
private const val FIX_INTERVAL_MS = 10_000L
private const val EGRESS_STEPS = 191

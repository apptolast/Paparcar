package io.apptolast.paparcar.domain.coordinator.replay

/**
 * [DET-HONEST-CLOSE-001] Field trace of the **short-hop false negative** at Calle Camelias
 * (2026-07-14 19:19Z, Firestore `diagnostics/WZB7…/sessions/1784056795594`, Redmi Note 11).
 *
 * A ~300 m / ~2 min hop from the Melgarejo park to Camelias. The geofence EXIT of the Melgarejo
 * fence (d=264 m, dep=self_observed) was delivered with its fix **already at Camelias** — the
 * whole trip happened BEFORE the session existed. The session was born watching a pedestrian:
 * every fix is walking-band around the new spot (max 4.65 m/s @ acc 73.9, never credible
 * driving), 23 steps arrive, and the false-ENTER guard aborts at the 8th.
 *
 * Field outcome: `aborted_false_enter` — SILENT, despite verified departure evidence. The old
 * Melgarejo pin stayed stale and the car ended up with no pin (user fixed it by hand hours
 * later). DET-HONEST-CLOSE-001's ladder must turn this into: old pin released + approximate
 * zone at the new spot + actionable prompt.
 */
val TRACE_CAMELIAS_HOP_001: List<TraceEvent> = buildList {
    val t0 = 1_784_056_795_594L // session arm (late GEOFENCE_EXIT, geof=a2767b3d d=264 m), 19:19:55Z

    fun fix(dtMs: Long, lat: Double, lon: Double, acc: Float, speed: Float) =
        add(TraceEvent(t0 + dtMs, TraceEvent.Kind.FIX, lat, lon, acc, speed))
    fun step(dtMs: Long) = add(TraceEvent(t0 + dtMs, TraceEvent.Kind.STEP))

    // The arm fix itself — already AT Camelias (exitLoc 36.59766,-6.25054, acc 24.6).
    fix(46, 36.5976626, -6.2505381, 24.62f, 0f)
    // Post-arrival noise: walking-band speeds, urban accuracies — never credible driving.
    fix(4_275, 36.5973346, -6.2502637, 73.86f, 4.65f)
    fix(10_246, 36.5974205, -6.2502924, 49.54f, 0f)
    fix(16_248, 36.5973386, -6.2503778, 53.48f, 2.35f)
    fix(20_258, 36.5974656, -6.2504848, 32.67f, 2.26f)
    fix(27_280, 36.5974568, -6.2505633, 49.58f, 2.57f)
    fix(30_236, 36.5976461, -6.2504849, 51.70f, 2.38f)
    fix(37_282, 36.5977149, -6.2508240, 62.38f, 0.59f)
    fix(40_239, 36.5976531, -6.2506923, 70.60f, 1.72f)
    fix(46_308, 36.5975790, -6.2506235, 30.32f, 0.19f)
    fix(50_238, 36.5975343, -6.2505502, 23.76f, 0.57f)
    fix(57_265, 36.5975058, -6.2505272, 115.59f, 1.05f)
    fix(61_253, 36.5975439, -6.2505992, 29.23f, 1.06f)
    fix(65_357, 36.5974853, -6.2505160, 28.90f, 0f)
    fix(71_259, 36.5974872, -6.2505146, 32.64f, 0.40f)
    // The user walks off — 23 steps in ~19 s; the 8th (Δ 83 240) trips the false-ENTER guard.
    step(71_649)
    step(72_677)
    step(72_679)
    step(74_262)
    step(74_264)
    step(74_266)
    fix(75_321, 36.5974760, -6.2504966, 28.87f, 0.34f)
    step(82_253)
    fix(82_259, 36.5973276, -6.2505452, 67.35f, 2.86f)
    step(83_240)
    step(83_243)
    step(83_428)
    step(83_434)
    step(84_434)
    step(84_441)
    fix(85_241, 36.5971687, -6.2504876, 52.78f, 1.30f)
    step(85_786)
    step(85_788)
    step(86_430)
    step(87_458)
    step(87_466)
    step(88_535)
    step(90_188)
    step(90_926)
    step(90_930)
    step(90_933)
    // Field SESSION_ENDED at Δ 91 353: aborted_false_enter.
}

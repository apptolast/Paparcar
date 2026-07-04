package io.apptolast.paparcar.domain.coordinator.replay

/**
 * [DET-SOLID-001][C4] Field trace of **BUG-REPARK-WALK-001** (2026-07-03 22:13–22:16Z,
 * El Puerto de Santa María — Firestore `diagnostics/fiyp…/sessions/1783116798598`).
 *
 * The user parked for real at Calle la Angelita 3 (session 22:11:15Z), walked home, and the
 * geofence EXIT armed a coordinator session at 22:13:18.598Z. **Every fix of the session is
 * pedestrian** (speed 0.05–0.95 m/s, all below the 1 m/s stop threshold), the position drifts
 * ~44 m south of the first fix, and 93 steps accumulate — at 22:15:43Z the then-unconditional
 * DET-G-04 seed let steps+egress confirm a bogus re-park at the pedestrian's position, ~120 m
 * from the car.
 *
 * Representative subsample of the session's LOCATION_FIX/STEP events preserving the shape:
 * walking-speed fixes drifting south + interleaved step bursts up to the confirm moment.
 */
val TRACE_BUG_REPARK_WALK_001: List<TraceEvent> = buildList {
    val t0 = 1_783_116_798_598L // session arm, 2026-07-03T22:13:18.598Z

    fun fix(dtMs: Long, lat: Double, lon: Double, acc: Float, speed: Float) =
        add(TraceEvent(t0 + dtMs, TraceEvent.Kind.FIX, lat, lon, acc, speed))
    fun steps(dtMs: Long, count: Int) = repeat(count) { add(TraceEvent(t0 + dtMs + it, TraceEvent.Kind.STEP)) }

    // First pedestrian fix — becomes bestStopLocation (speed < 1 m/s ⇒ "stopped").
    fix(4_103, 36.6046792, -6.2308008, 3.486f, 0.43f)
    steps(3_650, 1)
    fix(8_145, 36.6046475, -6.2308362, 4.54f, 0.91f)
    steps(6_570, 5)
    fix(12_109, 36.6046018, -6.2308107, 4.794f, 0.78f)
    steps(9_509, 5)   // ~11 steps
    fix(14_207, 36.6045876, -6.2308124, 4.284f, 0.72f)
    fix(16_425, 36.6045821, -6.2308061, 3.981f, 0.58f)
    steps(27_256, 9)  // ~20 steps
    fix(26_629, 36.6045065, -6.2305325, 5.057f, 0.20f)
    fix(33_678, 36.6045038, -6.2307593, 57.231f, 0.10f)
    steps(39_176, 13) // ~33 steps
    fix(39_754, 36.6045112, -6.2305633, 5.079f, 0.19f)
    fix(46_497, 36.6045456, -6.2306476, 6.234f, 0.42f)
    steps(58_332, 11) // ~44 steps
    fix(64_814, 36.6045221, -6.2307001, 4.165f, 0.12f)
    steps(66_931, 13) // ~57 steps
    fix(76_694, 36.6044613, -6.2307797, 5.59f, 0.44f)
    steps(80_131, 12) // ~69 steps
    fix(86_403, 36.6043759, -6.2307352, 5.951f, 0.95f)
    steps(88_134, 13) // ~82 steps
    fix(93_894, 36.6043457, -6.2306828, 5.56f, 0.42f)
    steps(97_841, 8)  // ~90 steps
    fix(99_656, 36.6043104, -6.2306094, 5.486f, 0.81f)
    steps(142_754, 3) // ~93 steps
    // Final fixes ~44 m south of the anchor — the egress displacement that sealed the false confirm.
    fix(132_478, 36.6043349, -6.2306044, 3.871f, 0.06f)
    fix(139_808, 36.6043343, -6.2306062, 3.748f, 0.08f)
}

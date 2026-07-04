package io.apptolast.paparcar.domain.coordinator.replay

/**
 * [ANCHOR-LOCK-001] Field trace of a **CORRECT** detection that the anchor lock must NOT break
 * (2026-07-04 17:11Z, Firestore `diagnostics/fiyp…/sessions/1783185093721`).
 *
 * Real drive from the supermarket to **Calle Gavia**: brief traffic stop at 77 s where phone
 * jiggle produced **2 spurious steps** (this is why the lock threshold is 8 — locking on those
 * would pin the park at the light), slow final approach at 2.5–3.6 m/s (clears the light's
 * anchor, as it should), real park at `36.602430, -6.276520` (~2 min still), exit + steps, and
 * an in-store walk providing the egress. The park must anchor at Calle Gavia.
 */
val TRACE_CALLE_GAVIA_001: List<TraceEvent> = buildList {
    val t0 = 1_783_185_093_721L // session arm, 2026-07-04T17:11:33.721Z

    fun fix(dtMs: Long, lat: Double, lon: Double, acc: Float, speed: Float) =
        add(TraceEvent(t0 + dtMs, TraceEvent.Kind.FIX, lat, lon, acc, speed))
    fun steps(dtMs: Long, count: Int) = repeat(count) { add(TraceEvent(t0 + dtMs + it, TraceEvent.Kind.STEP)) }

    // Cold first fix, then the real drive (6–8 m/s).
    fix(5_000, 36.602770, -6.270849, 33f, 0.00f)
    fix(7_000, 36.602845, -6.271360, 19f, 7.89f)
    fix(43_000, 36.603278, -6.274157, 7f, 6.06f)
    // Traffic stop: phone jiggle fires 2 SPURIOUS steps — must not lock the anchor here.
    fix(77_000, 36.602918, -6.276077, 46f, 0.00f)
    steps(78_000, 2)
    fix(83_000, 36.602898, -6.275962, 5f, 0.00f)
    fix(88_000, 36.602747, -6.276040, 4f, 0.00f)
    // Slow final approach (2.5–3.6 m/s) — clears the light's anchor, unlocked as intended.
    fix(118_000, 36.602353, -6.276912, 3f, 3.18f)
    fix(123_000, 36.602257, -6.276778, 2f, 3.03f)
    fix(128_000, 36.602385, -6.276568, 2f, 3.61f)
    // REAL park on Calle Gavia — anchor captured here.
    fix(143_000, 36.602430, -6.276520, 2f, 0.00f)
    fix(148_000, 36.602412, -6.276525, 2f, 0.00f)
    // User exits: steps while GPS still pinned at the car.
    steps(274_000, 8)
    fix(291_000, 36.602430, -6.276535, 2f, 0.00f)
    // Walks into the store — sub-threshold speeds, position drifting ≥18 m → egress proof.
    fix(486_000, 36.601815, -6.276488, 6f, 0.69f)
    fix(499_000, 36.601774, -6.276444, 5f, 1.05f)
    fix(534_000, 36.601557, -6.276503, 3f, 0.10f)
}

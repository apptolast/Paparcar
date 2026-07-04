package io.apptolast.paparcar.domain.coordinator.replay

/**
 * [ANCHOR-LOCK-001] Field trace of the anchor-relocation incident (2026-07-04 17:11Z,
 * Firestore `diagnostics/fiyp…/sessions/1783185093721`).
 *
 * Short hop, park on **Avda. Alcalde Eduardo Ruiz** (fixes at 77–88 s refine the anchor to
 * `36.602747, -6.276040` acc 4), user exits (steps at 78–79 s) and walks briskly toward the
 * supermarket — Doppler fixes at 3.0–3.6 m/s with good accuracy crossed
 * `clearBestStopSpeedMps` (2.5) and, pre-fix, WIPED the true anchor. The user then stood
 * still ~2 min on **Calle Gavia** (`36.602430, -6.276520`, 55 m away), the anchor re-captured
 * there, and steps+egress saved the park on the wrong street.
 *
 * Condensed subsample preserving the shape: drive → park+exit → brisk walk → stand → steps.
 */
val TRACE_SUPERMARKET_ANCHOR_001: List<TraceEvent> = buildList {
    val t0 = 1_783_185_093_721L // session arm, 2026-07-04T17:11:33.721Z

    fun fix(dtMs: Long, lat: Double, lon: Double, acc: Float, speed: Float) =
        add(TraceEvent(t0 + dtMs, TraceEvent.Kind.FIX, lat, lon, acc, speed))
    fun steps(dtMs: Long, count: Int) = repeat(count) { add(TraceEvent(t0 + dtMs + it, TraceEvent.Kind.STEP)) }

    // Cold first fix at the origin, then the real drive (6–8 m/s).
    fix(5_000, 36.602770, -6.270849, 33f, 0.00f)
    fix(7_000, 36.602845, -6.271360, 19f, 7.89f)
    fix(43_000, 36.603278, -6.274157, 7f, 6.06f)
    // Park on Avda. Alcalde Eduardo Ruiz: stop, door slam + first steps, anchor refines to acc 4.
    fix(77_000, 36.602918, -6.276077, 46f, 0.00f)
    steps(78_000, 2)
    fix(83_000, 36.602898, -6.275962, 5f, 0.00f)
    fix(88_000, 36.602747, -6.276040, 4f, 0.00f)
    // Brisk walk toward the supermarket — 3.0–3.6 m/s Doppler with excellent accuracy. These
    // fixes are what wiped the anchor pre-fix (above clearBestStopSpeedMps AND a reposition
    // burst); with the lock they must not touch it.
    fix(118_000, 36.602353, -6.276912, 3f, 3.18f)
    fix(123_000, 36.602257, -6.276778, 2f, 3.03f)
    fix(128_000, 36.602385, -6.276568, 2f, 3.61f)
    // Standing still on Calle Gavia (~2 min in the real trace) — pre-fix this became the anchor.
    fix(143_000, 36.602430, -6.276520, 2f, 0.00f)
    fix(148_000, 36.602412, -6.276525, 2f, 0.00f)
    // User walks on (in-store): steps accumulate while stopped-by-GPS.
    steps(274_000, 6) // steps 3..8
    fix(291_000, 36.602430, -6.276535, 2f, 0.00f)
}

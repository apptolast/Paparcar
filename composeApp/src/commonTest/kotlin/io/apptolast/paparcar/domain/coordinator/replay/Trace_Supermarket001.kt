package io.apptolast.paparcar.domain.coordinator.replay

/**
 * [ANCHOR-LOCK-001] Field trace of the supermarket incident — the REAL complaint
 * (2026-07-04 16:46Z, Firestore `diagnostics/fiyp…/sessions/1783183613431`).
 *
 * Ultra-short hop (Calle Cuatro Pinos 48B → the supermarket on Avda. Alcalde Eduardo Ruiz G 2):
 * the geofence exit delivered so late that the session ARMED with the car already parked in the
 * lot — the stream never saw driving. The anchor was correctly captured at the car (first 46 s,
 * phone still inside it), the user exited (step burst at 47 s) and B3 correctly degraded to the
 * "¿Has aparcado?" prompt at 62 s. THEN two things went wrong pre-fix:
 *  1. wandering inside the store re-captured the anchor at each indoor re-stop → pin drifted
 *     off the car and into the building;
 *  2. the departure worker's late `verified_late` upgrade flipped the weak-evidence policy and
 *     silently SAVED (at the drifted anchor) a park the user had been asked about and never
 *     answered.
 */
object TraceSupermarket001 {
    private const val T0 = 1_783_183_613_431L // session arm, 2026-07-04T16:46:53.431Z

    private fun MutableList<TraceEvent>.fix(dtMs: Long, lat: Double, lon: Double, acc: Float, speed: Float) =
        add(TraceEvent(T0 + dtMs, TraceEvent.Kind.FIX, lat, lon, acc, speed))
    private fun MutableList<TraceEvent>.steps(dtMs: Long, count: Int) =
        repeat(count) { add(TraceEvent(T0 + dtMs + it, TraceEvent.Kind.STEP)) }

    /** Arm-with-car-parked through the exit step burst and the egress that triggers the prompt. */
    val park: List<TraceEvent> = buildList {
        // Armed late: the car is ALREADY parked in the lot; phone still inside it for 46 s.
        fix(1_000, 36.602125, -6.256822, 13f, 0.00f)
        fix(14_000, 36.602147, -6.256833, 16f, 0.00f)
        fix(23_000, 36.602084, -6.256802, 15f, 0.00f)
        fix(37_000, 36.602127, -6.256847, 7f, 0.00f)
        // User exits — real step burst.
        steps(47_000, 14)
        fix(52_000, 36.602038, -6.256732, 7f, 0.00f)
        // ≥18 m from the anchor → egress; with weak evidence this must PROMPT, not save.
        // (lon nudged ~2 m east of the raw fix for assertion margin — the field value sat at
        // exactly 17.99 m from the anchor.)
        fix(62_000, 36.602085, -6.256600, 6f, 0.00f)
    }

    /** In-store wander: sub-threshold speeds with indoor re-stops that used to re-capture the
     *  anchor (better-accuracy fixes at the user's position) and drag the pin into the store. */
    val wander: List<TraceEvent> = buildList {
        steps(90_000, 40)
        fix(353_000, 36.601907, -6.256809, 6f, 0.10f)
        fix(374_000, 36.601933, -6.256824, 13f, 0.46f)
        fix(397_000, 36.601961, -6.256723, 8f, 1.06f) // breaks the stop
        fix(414_000, 36.602086, -6.256642, 11f, 0.89f) // new indoor stop begins
        fix(424_000, 36.602046, -6.256715, 9f, 0.33f)  // pre-fix: re-captured the anchor here
        steps(436_000, 20)
        fix(441_000, 36.602082, -6.256721, 7f, 0.33f)
        fix(466_000, 36.602173, -6.256817, 9f, 0.20f)
    }
}

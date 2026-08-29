package com.rndeveloper.paparcar.domain.detection.coordinator.replay

/**
 * [DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001] Field trace of the **supersede that threw
 * away a measured drive** (2026-08-25 17:59:05Z, Firestore
 * `diagnostics/fiypN…/sessions/1787680745542`, OPPO CPH2371, Ford Focus / Coordinator).
 *
 * ## What the user did
 *
 * Gym → a petrol station with a queue (left without refuelling) → home, except he pulled into the
 * petrol station two streets from his door, got out, got back in, and drove the last ~150 m.
 *
 * ## What the app did
 *
 * The session that followed the 23-minute drive home (`1787679356333`: 98 km/h, 60 driving fixes of
 * 261, 206 steps) stopped at the petrol station at 19:57:28 local and was still evaluating the park
 * when, at 19:59:05, the AR fired a **TRUE** `IN_VEHICLE ENTER` — the user really was re-boarding.
 * The service superseded it: `cancel()` at `…745472`, this session armed at `…745482`, **10 ms**
 * apart. The predecessor's outcome fell to `ended`, the default, and everything it had measured
 * went with it.
 *
 * This trace is what the replacement saw on its own: a ~150 m loop peaking at **3,77 m/s
 * (13,6 km/h)** — a manoeuvring speed no drive proof can be built from — a stop at Δ35,7 s, and then
 * the egress walk. Twelve steps before any driving speed, and `FalseEnterAbortStage` did exactly
 * what it exists to do: `aborted_false_enter`. No pin. The Redmi, riding the same car with an
 * uninterrupted session, confirmed at 20:02:51 at the petrol station (`550cca8e`,
 * 36.6089721,-6.2778915 — the first fix below, to five decimal places).
 *
 * The park is at **36.60871,-6.27821**, where the car stops from Δ35 695 ms onwards and never moves
 * again. Neither phone has it.
 *
 * ## Fidelity
 *
 * 17 fixes and 12 steps, matching the `drive 0/17fix · steps 12` the session header wrote on exit.
 * The device's own `parkdiag.log` had already rotated when this was pulled (it only reached 22:12
 * local), so Firestore is the source here — the reverse of the 22-08 traces.
 */
val TRACE_GONDOLA_2508_SUPERSEDE: List<TraceEvent> = buildList {
    val t0 = 1_787_680_745_542L // session arm (AR_VEHICLE_ENTER, geof=a786c135, dep=enter_at_car)

    fun fix(dtMs: Long, lat: Double, lon: Double, acc: Float, speed: Float) =
        add(TraceEvent(t0 + dtMs, TraceEvent.Kind.FIX, lat, lon, acc, speed))
    fun step(dtMs: Long) = add(TraceEvent(t0 + dtMs, TraceEvent.Kind.STEP))

    // Re-boarding at the petrol station — this first fix IS where the Redmi planted its pin.
    fix(44, 36.6089869, -6.2779194, 3.804f, 0.031f)
    // The last hop: out to the corner and back round to the door. Accuracies are excellent (1,7–3,8 m),
    // so nothing here is refused for credibility — it is simply too slow and too short to prove a drive.
    fix(706, 36.6088717, -6.2777367, 3.434f, 1.723f)
    fix(5_788, 36.6088317, -6.2775967, 3.205f, 2.387f)
    fix(10_766, 36.6086983, -6.2776483, 2.350f, 2.558f)
    fix(15_756, 36.6085217, -6.2777350, 2.550f, 3.430f)
    fix(20_766, 36.6084783, -6.2778417, 2.600f, 3.408f)
    fix(25_789, 36.6085800, -6.2780233, 2.600f, 3.013f)
    fix(30_797, 36.6087200, -6.2782133, 2.750f, 3.766f) // the session peak: 13,6 km/h
    // Parked. Every fix from here sits within ~5 m of 36.60871,-6.27821 — this is the spot.
    fix(35_695, 36.6087433, -6.2782083, 2.800f, 0f)
    fix(40_749, 36.6087083, -6.2781883, 2.650f, 0.491f)
    fix(45_772, 36.6087333, -6.2782467, 2.500f, 0.763f)
    fix(50_693, 36.6087383, -6.2782450, 2.400f, 0.493f)
    fix(55_767, 36.6087300, -6.2782300, 2.250f, 0f)
    fix(60_759, 36.6087133, -6.2782100, 2.150f, 0f)
    // The user gets out and walks off. The 9th step is what tripped the false-ENTER abort in the field.
    step(61_945)
    step(62_505)
    step(63_501)
    step(64_619)
    fix(65_768, 36.6087133, -6.2782100, 1.950f, 0f)
    step(69_803)
    step(70_463)
    fix(70_783, 36.6086833, -6.2781767, 1.800f, 0.307f)
    step(70_959)
    step(71_704)
    step(72_560)
    fix(75_803, 36.6087067, -6.2781717, 1.750f, 0f)
    step(76_984)
    step(77_726)
    step(78_122)
    // Field ending at Δ78 940: aborted_false_enter.
}

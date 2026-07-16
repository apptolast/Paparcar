package io.apptolast.paparcar.domain.coordinator.replay

/**
 * [DET-HONEST-CLOSE-001] Field trace of the **correctly-silent abort** that must STAY silent
 * (2026-07-15 02:11Z, Firestore `diagnostics/fiyp…/sessions/1784081508556`, Oppo CPH2371).
 *
 * The user had walked ~1.1 km away from the parked car (Rosa de los Vientos fence, 0239ed5c).
 * GMS delivered the fence EXIT late (d=1099 m, dep=self_observed) while the phone was AT REST at
 * the destination: 52 fixes with acc 2.4-2.9 m, speed 0 (the "vmax 10 km/h" of the session
 * summary is the single 2.76 m/s walking fix), 2 steps. The car had NOT moved.
 *
 * `aborted_no_movement` was the CORRECT verdict — the walk explains the distance, so any
 * approximate-zone / release / prompt here would assert the car is where the PEDESTRIAN is
 * (BUG-WALK-DEPART-001). This fixture is the permanent counterexample that gates
 * DET-HONEST-CLOSE-001's ladder: rung 3 (no ride proof) must stay silent, keep the old pin,
 * save nothing, prompt nothing.
 *
 * (The systemic damage that night was DOWNSTREAM of this session: the late delivery burned the
 * fence's only EXIT, so the real drive home minutes later had no nominator left — that is the
 * chain-continuity half of DET-HONEST-CLOSE-001, not an assertion of this trace.)
 */
val TRACE_LATE_EXIT_ON_FOOT_001: List<TraceEvent> = buildList {
    val t0 = 1_784_081_508_556L // session arm (late GEOFENCE_EXIT, geof=0239ed5c d=1099 m), 02:11:51Z

    fun fix(dtMs: Long, lat: Double, lon: Double, acc: Float, speed: Float) =
        add(TraceEvent(t0 + dtMs, TraceEvent.Kind.FIX, lat, lon, acc, speed))
    fun step(dtMs: Long) = add(TraceEvent(t0 + dtMs, TraceEvent.Kind.STEP))

    // Arm fix + the last strides of the walk (2.1-2.8 m/s), then at rest.
    fix(33, 36.6092156, -6.2843802, 34.38f, 0f)
    step(53)
    step(559)
    fix(4_283, 36.6091284, -6.2845303, 64.60f, 0.24f)
    fix(9_821, 36.6087837, -6.2846948, 4.90f, 2.76f)
    fix(14_877, 36.6087033, -6.2845843, 4.55f, 2.15f)
    fix(19_825, 36.6087725, -6.2843905, 3.60f, 2.54f)
    fix(21_975, 36.6087787, -6.2843849, 4.07f, 0f)
    fix(24_275, 36.6087787, -6.2843849, 4.30f, 0f)
    fix(29_839, 36.6087927, -6.2843254, 18.06f, 2.06f)
    fix(34_850, 36.6088067, -6.2843250, 16.58f, 0f)
    // Four minutes genuinely stationary — GNSS-grade fixes, zero displacement.
    fix(39_818, 36.6088183, -6.2843017, 2.45f, 0f)
    fix(44_832, 36.6088167, -6.2842933, 2.40f, 0f)
    fix(49_825, 36.6088167, -6.2842933, 2.40f, 0f)
    fix(54_832, 36.6088167, -6.2842933, 2.40f, 0f)
    fix(59_818, 36.6088167, -6.2842933, 2.40f, 0f)
    fix(64_829, 36.6088167, -6.2842933, 2.50f, 0f)
    fix(69_813, 36.6087617, -6.2843033, 2.50f, 0f)
    fix(74_888, 36.6087667, -6.2843067, 2.60f, 0f)
    fix(79_812, 36.6087783, -6.2843033, 2.55f, 0f)
    fix(84_825, 36.6087783, -6.2843033, 2.50f, 0f)
    fix(89_864, 36.6087783, -6.2843033, 2.55f, 0f)
    fix(94_806, 36.6087783, -6.2843033, 2.60f, 0f)
    fix(99_796, 36.6087783, -6.2843033, 2.60f, 0f)
    fix(104_918, 36.6087783, -6.2843033, 2.60f, 0f)
    fix(109_832, 36.6088050, -6.2842683, 2.65f, 0f)
    fix(114_830, 36.6088050, -6.2842683, 2.70f, 0f)
    fix(119_898, 36.6088050, -6.2842683, 2.70f, 0f)
    fix(124_808, 36.6088050, -6.2842683, 2.65f, 0f)
    fix(129_829, 36.6088050, -6.2842683, 2.60f, 0f)
    fix(134_826, 36.6088050, -6.2842683, 2.60f, 0f)
    fix(139_838, 36.6088050, -6.2842683, 2.60f, 0f)
    fix(144_801, 36.6087817, -6.2842933, 2.65f, 0f)
    fix(149_850, 36.6087817, -6.2843050, 2.70f, 0f)
    fix(154_781, 36.6087817, -6.2843050, 2.70f, 0f)
    fix(159_810, 36.6087817, -6.2843050, 2.70f, 0f)
    fix(164_827, 36.6088017, -6.2842883, 2.75f, 0f)
    fix(169_830, 36.6088267, -6.2842950, 2.85f, 0f)
    fix(174_875, 36.6088233, -6.2842950, 2.85f, 0f)
    fix(179_393, 36.6088233, -6.2842950, 108.96f, 0f)
    fix(179_817, 36.6088650, -6.2842817, 2.70f, 0f)
    fix(184_770, 36.6088500, -6.2842883, 2.75f, 0f)
    fix(189_848, 36.6088500, -6.2842883, 2.70f, 0f)
    fix(194_845, 36.6088550, -6.2842933, 2.60f, 0f)
    fix(199_826, 36.6088550, -6.2842933, 2.60f, 0f)
    fix(204_827, 36.6088550, -6.2842933, 2.60f, 0f)
    fix(209_869, 36.6088117, -6.2843133, 2.80f, 0f)
    fix(214_808, 36.6088217, -6.2842983, 2.80f, 0f)
    fix(219_829, 36.6088450, -6.2842833, 2.85f, 0f)
    fix(224_810, 36.6088533, -6.2842800, 2.90f, 0f)
    fix(229_825, 36.6089433, -6.2842633, 2.85f, 0f)
    fix(234_820, 36.6089333, -6.2842517, 2.85f, 0f)
    fix(239_814, 36.6088633, -6.2842733, 2.70f, 0f)
    // The 4-min no-movement guard fires on this fix (Δ 244 825 > maxNoMovementMs since Δ 33).
    fix(244_825, 36.6088517, -6.2842600, 2.80f, 0f)
    // Field SESSION_ENDED at Δ 249 849: aborted_no_movement.
}

package io.apptolast.paparcar.domain.detection.coordinator.replay

/**
 * [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001] Field trace of the **indoor-mirage re-park**
 * (2026-08-22 20:50:38–20:54:31 local, El Puerto de Santa María — Oppo CPH2371, device log
 * `files/parkdiag.log`; the session never reached Firestore intact because the phone died on
 * battery that night).
 *
 * The user had parked for real on Calle Frutos at 20:38:17 (`ce3bb858…`, 36.60772,-6.2679683) and
 * gone into the house. At 20:50:37 a stationary indoor GPS burst reported **36 km/h at acc 5.5 m
 * from a position 101 m north** — enough to break the parked car's 83 m geofence. The exit's own
 * fix was then handed back as proof of the exit, arming the coordinator `verified_speed`.
 *
 * What makes this trace worth keeping is the TAIL: the mirage did not stop at the trigger. The
 * session's very first fix still carries **8.2 m/s** — above `minimumTripSpeedMps` and at credible
 * accuracy — so the stream itself would flip `hasEverReachedDrivingSpeed` even without the arm
 * seed. Every one of the 50 fixes after it reads **0.0 m/s**: the phone was on a table indoors.
 * The departure worker sampled 0.9 / 0.0 / 0.0 km/h and DISMISSED the departure at +104 s, and
 * 2 s later `steps+egress` fast-confirmed on 9 indoor steps. At 20:54:31 the hold settled and a
 * phantom park was planted at 36.6081233,-6.26774 — 49 m from the correct pin, inside the house —
 * replacing that pin and deleting its geofence.
 *
 * Faithful subsample of the session's fixes and step bursts (device log line numbers preserved in
 * order), from the arm to the confirm decision.
 */
val TRACE_HOUSE_MIRAGE_001: List<TraceEvent> = buildList {
    val t0 = 1_787_424_638_256L // coordinator.invoke() entry, 2026-08-22 20:50:38.256 local

    fun fix(dtMs: Long, lat: Double, lon: Double, acc: Float, speed: Float) =
        add(TraceEvent(t0 + dtMs, TraceEvent.Kind.FIX, lat, lon, acc, speed))
    fun steps(dtMs: Long, count: Int) = repeat(count) { add(TraceEvent(t0 + dtMs + it, TraceEvent.Kind.STEP)) }

    // The mirage's TAIL — one more "driving" sample, 101 m north of the car, phone on a table.
    fix(318, 36.6086367, -6.2679617, 5.566f, 8.235556f)
    // …and from here every single fix is dead still. Nothing ever drove.
    fix(1_769, 36.6085667, -6.2681017, 5.65f, 0.07566417f)
    fix(3_772, 36.6085283, -6.26798, 5.633f, 0.042721946f)
    fix(5_807, 36.60848, -6.2679383, 5.533f, 0.12095972f)
    fix(7_796, 36.60848, -6.267915, 5.433f, 0f)
    fix(12_004, 36.608515, -6.2679233, 11.744f, 0f)
    fix(18_794, 36.6083267, -6.267835, 5.0f, 0f)
    steps(19_500, 2)
    fix(23_798, 36.6082, -6.267775, 4.9f, 0f)
    steps(24_500, 3) // 5 steps
    fix(28_787, 36.6081917, -6.2677733, 5.05f, 0f)
    fix(32_814, 36.6081917, -6.2677733, 3.75f, 0f)
    // The fix that ended up being the phantom pin.
    fix(37_794, 36.6081233, -6.26774, 2.65f, 0f)
    fix(42_800, 36.6080817, -6.26769, 3.5f, 0f)
    fix(47_827, 36.6080667, -6.2676433, 4.35f, 0f)
    fix(52_856, 36.6080817, -6.2676233, 4.95f, 0f)
    fix(57_855, 36.60805, -6.2675717, 5.15f, 0f)
    fix(62_858, 36.6080483, -6.2675733, 5.15f, 0f)
    fix(67_857, 36.6080433, -6.267535, 17.25f, 0f)
    fix(71_473, 36.6080433, -6.267535, 40.388f, 0f)
    fix(77_894, 36.6080283, -6.2675183, 6.3f, 0f)
    fix(82_816, 36.6079733, -6.2675683, 6.35f, 0f)
    fix(87_861, 36.60791, -6.2675583, 6.55f, 0f)
    fix(92_878, 36.6078533, -6.2675217, 6.5f, 0f)
    fix(97_821, 36.60787, -6.26756, 5.55f, 0f)
    fix(101_882, 36.60787, -6.26756, 17.208f, 0f)
    steps(102_500, 4) // 9 steps — the count the fast confirm quoted
    fix(107_893, 36.6079367, -6.2676483, 2.95f, 0f)
    steps(108_500, 2) // 11
    fix(112_856, 36.6079333, -6.2676333, 4.509f, 0f)
    steps(113_500, 1) // 12
    fix(116_388, 36.6079333, -6.2676333, 13.436f, 0f)
    fix(122_869, 36.6079333, -6.2676417, 3.3f, 0f)
    fix(127_800, 36.6079583, -6.2676383, 3.4f, 0f)
    fix(130_111, 36.6079583, -6.2676383, 10.181f, 0f)
    fix(132_815, 36.6079617, -6.2676567, 7.887f, 0f)
    fix(137_831, 36.607975, -6.267665, 2.55f, 0f)
    fix(142_848, 36.6079717, -6.267665, 1.8f, 0f)
    fix(147_835, 36.6079733, -6.2676617, 2.3f, 0f)
    fix(152_843, 36.6079817, -6.267655, 2.3f, 0f)
    fix(157_854, 36.6079867, -6.2676467, 1.8f, 0f)
    fix(162_835, 36.6079733, -6.267655, 1.8f, 0f)
    fix(167_824, 36.6079433, -6.2676583, 1.8f, 0f)
    fix(172_872, 36.6079383, -6.26766, 1.8f, 0f)
    fix(177_834, 36.6079367, -6.2676567, 1.8f, 0f)
    fix(182_851, 36.607935, -6.267655, 1.75f, 0f)
    fix(187_828, 36.60792, -6.267645, 1.75f, 0f)
    fix(192_869, 36.607915, -6.2676333, 1.75f, 0f)
    fix(197_864, 36.6079233, -6.2676283, 1.7f, 0f)
    fix(202_834, 36.6079383, -6.26763, 1.7f, 0f)
    fix(207_871, 36.6079483, -6.26763, 2.25f, 0f)
    fix(212_850, 36.607955, -6.2676333, 2.3f, 0f)
    fix(217_854, 36.607955, -6.2676333, 1.8f, 0f)
    fix(222_849, 36.607955, -6.267645, 1.95f, 0f)
    fix(227_883, 36.60795, -6.267645, 2.6f, 0f)
    fix(232_838, 36.60795, -6.267645, 2.4f, 0f)
}

/** The pin the car had really been left at 12 minutes earlier — Calle Frutos, `ce3bb858…`. The
 *  departure anchor this session was armed against. */
val HOUSE_MIRAGE_001_REAL_PIN_LAT: Double = 36.60772
val HOUSE_MIRAGE_001_REAL_PIN_LON: Double = -6.2679683

/** Epoch-ms at which the departure worker exhausted its attempts and DISMISSED the departure
 *  (device log 20:52:23.879 — 105.6 s into the session, 2.3 s before the fast confirm). */
val HOUSE_MIRAGE_001_DISMISSED_AT_MS: Long = 1_787_424_638_256L + 105_623L

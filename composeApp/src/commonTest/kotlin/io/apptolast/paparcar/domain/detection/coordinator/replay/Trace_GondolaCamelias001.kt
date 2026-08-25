package io.apptolast.paparcar.domain.detection.coordinator.replay

/**
 * [DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001] Field trace of **trip 1 of 2026-08-22**, Calle
 * Gondola -> Camelias (14:41:50–14:51:40 local, El Puerto de Santa Maria — Redmi Note 11
 * `2201117TY`, device log `files/parkdiag.log`). All 76 fixes and 106 steps, 1:1.
 *
 * A 75 km/h car trip with 57 driving fixes, armed by GEOFENCE_EXIT with the honest `self_observed`
 * label ([ArmEvidence.Unverified]). The stream proved itself twice over: displacement from the pin
 * unlocked the speed statistic at D 11 276, and 46 s of sustained driving band satisfied
 * [DET-MOTOR-PROOF-001] at D 47 067.
 *
 * And then the field build called it a **bicycle**:
 *
 * ```
 * D 293 030  anchor FROZEN — drive-entered stop matured   <- the car is ALREADY parked
 * D 322 016  step #35 (egress walk, anchor set)
 * D 328 034  anchor LOCKED (steps=37) — ignoring walking-range speed 4.27 m/s
 * D 329 669  pedal cadence — 12 steps concurrent with 4 above-ceiling fixes -> human-powered ride
 * D 342 068  confirm degraded to user prompt (steps+egress, reason=human_powered)
 * ```
 *
 * The veto fired **36 seconds after the anchor froze**, and the steps it counted are labelled by the
 * log itself as `egress walk, anchor set`. That is the walk away from the car, and this phone's GPS
 * (accuracies of 20-380 m here) turns it into 4.27 m/s fixes that sit squarely inside the
 * 3.0-11.1 m/s cadence band. The motorway refutation could not save it: it demands 30 s SUSTAINED
 * above 11.1 m/s and the Redmi's starved stream never banked them despite touching 75 km/h.
 *
 * The park degraded to a prompt, and the user's "yes" then planted the pin inside the house — a
 * second bug ([DET-USER-YES-IS-NOT-A-COORDINATE-001], `8bf6f02b`) riding on this one.
 *
 * Fixed in master (`06fbc5e8`): after the anchor is pinned, steps concurrent with fast fixes are
 * the EXPECTED signature of walking away with noisy GPS — the opposite of a pedalling proof.
 */
object TraceGondolaCamelias001 {

    /** AR `IN_VEHICLE ENTER`, stamped mid-drive. Injected at delivery time with its true time. */
    const val AR_RIDE_TRUE_TIME_MS = 1_787_402_651_536L
    const val AR_RIDE_DELIVERED_AT_MS = 1_787_402_651_725L

    /** The field tap on "Sí", 14:51:34.400 local — Δ 584 231 into the session. */
    const val USER_YES_AT_MS = 1_787_403_094_400L

    /** Where the car actually parked — the frozen anchor, corroborated by the Oppo. */
    const val REAL_SPOT_LAT = 36.5977
    const val REAL_SPOT_LON = -6.2505

    val events: List<TraceEvent> = buildList {
        val t0 = 1_787_402_510_169L

        fun fix(dtMs: Long, lat: Double, lon: Double, acc: Float, speed: Float) =
            add(TraceEvent(t0 + dtMs, TraceEvent.Kind.FIX, lat, lon, acc, speed))

        fun step(dtMs: Long) = add(TraceEvent(t0 + dtMs, TraceEvent.Kind.STEP))

        fix(3999, 36.6027807, -6.2601728, 63.281f, 1.6052034f) // loc#1
        fix(5983, 36.6028142, -6.2598451, 66.052f, 3.6846452f) // loc#2
        fix(11020, 36.602795, -6.2598745, 107.787f, 4.7364654e-07f) // loc#3
        fix(17013, 36.6027961, -6.2597775, 104.695f, 0.005793641f) // loc#4
        fix(20993, 36.6024867, -6.2567461, 38.925f, 11.22f) // loc#5
        fix(27072, 36.6022574, -6.256611, 18.606f, 5.5403204f) // loc#6
        fix(31274, 36.6020509, -6.2566831, 30.026f, 5.608187f) // loc#7
        fix(37163, 36.6016851, -6.2567818, 23.646f, 5.6880655f) // loc#8
        fix(41046, 36.6014906, -6.2568478, 26.533f, 5.5343113f) // loc#9
        fix(47301, 36.6012773, -6.2568852, 75.557f, 2.5797284f) // loc#10
        fix(50446, 36.6012162, -6.2568826, 133.214f, 2.5567877f) // loc#11
        fix(56965, 36.6012312, -6.2568337, 67.526f, 2.1964166f) // loc#12
        fix(59620, 36.6012949, -6.2569097, 62.423f, 1.7625457f) // loc#13
        fix(67066, 36.6006923, -6.2572137, 40.605f, 9.762314f) // loc#14
        fix(71081, 36.6002612, -6.2573843, 41.05f, 10.032096f) // loc#15
        fix(77048, 36.5997798, -6.2575667, 44.987f, 8.38974f) // loc#16
        fix(81175, 36.5995395, -6.2576667, 45.02f, 7.251907f) // loc#17
        fix(87105, 36.5991152, -6.2579111, 12.005f, 6.829963f) // loc#18
        fix(91023, 36.5989546, -6.2581289, 15.261f, 6.665059f) // loc#19
        fix(97096, 36.5987282, -6.2574264, 12.909f, 7.806962f) // loc#20
        fix(101014, 36.5985317, -6.2569694, 16.014f, 8.928585f) // loc#21
        fix(107026, 36.5983671, -6.2563565, 18.388f, 9.208284f) // loc#22
        fix(111176, 36.5983054, -6.2559065, 22.623f, 9.532843f) // loc#23
        fix(117362, 36.5982579, -6.2552871, 31.336f, 10.101463f) // loc#24
        fix(121095, 36.5982379, -6.2548309, 35.484f, 10.293407f) // loc#25
        fix(127096, 36.5981658, -6.2541912, 120.675f, 8.024241f) // loc#26
        fix(131055, 36.5980707, -6.2538734, 108.323f, 7.538397f) // loc#27
        fix(138039, 36.5979819, -6.2538017, 56.37f, 0.6599201f) // loc#28
        fix(141031, 36.5980032, -6.2540403, 48.312f, 0.26039618f) // loc#29
        fix(147025, 36.598098, -6.2545246, 88.499f, 2.16f) // loc#30
        fix(151009, 36.5980478, -6.2543065, 76.351f, 1.45f) // loc#31
        fix(157021, 36.5980314, -6.2539271, 37.817f, 3.85f) // loc#32
        step(157275) // #0
        step(157291) // #0
        step(157296) // #0
        step(158287) // #0
        step(158292) // #0
        fix(161114, 36.597878, -6.2540448, 34.325f, 4.05f) // loc#33
        fix(168044, 36.5976885, -6.2529444, 27.081f, 10.19f) // loc#34
        fix(171115, 36.5974727, -6.2527117, 26.845f, 10.33f) // loc#35
        fix(177064, 36.5969041, -6.2523738, 27.669f, 11.63f) // loc#36
        fix(180999, 36.5966919, -6.2519886, 20.174f, 10.77f) // loc#37
        fix(187016, 36.59691, -6.2514715, 39.062f, 6.8f) // loc#38
        fix(187066, 36.59691, -6.2514715, 39.062f, 6.8f) // loc#39
        fix(287586, 36.5975543, -6.2506949, 21.524f, 0.0f) // loc#40
        step(289740) // #1
        fix(289831, 36.5976334, -6.2506357, 15.694f, 0.0f) // loc#41
        step(290775) // #2
        step(290781) // #3
        fix(291069, 36.5976716, -6.2506248, 26.695f, 0.0f) // loc#42
        step(291788) // #4
        step(291790) // #5
        step(292846) // #6
        step(292854) // #7
        fix(293028, 36.597708, -6.2505736, 58.984f, 0.0f) // loc#43
        step(293752) // #8
        step(293757) // #9
        step(294787) // #10
        step(294797) // #11
        step(295791) // #12
        step(296750) // #13
        step(296763) // #14
        step(297836) // #15
        step(298799) // #16
        step(302795) // #17
        step(303797) // #18
        step(303808) // #19
        step(304809) // #20
        step(304815) // #21
        step(305809) // #22
        step(305818) // #23
        step(306803) // #24
        step(306816) // #25
        fix(307505, 36.5977132, -6.2505947, 16.161f, 0.0f) // loc#44
        step(307820) // #26
        step(307822) // #27
        step(318647) // #28
        step(318656) // #29
        step(319649) // #30
        step(319663) // #31
        step(320650) // #32
        step(321651) // #33
        step(321673) // #34
        fix(322014, 36.5976488, -6.2504516, 55.57f, 1.68f) // loc#45
        step(322654) // #35
        step(323811) // #36
        step(327665) // #37
        fix(328033, 36.5977523, -6.2505911, 10.244f, 4.27f) // loc#46
        step(328662) // #37
        step(329672) // #37
        step(329676) // #37
        step(330665) // #37
        step(331675) // #37
        step(331680) // #37
        step(332666) // #37
        fix(333037, 36.5976871, -6.2505664, 114.576f, 0.27f) // loc#47
        step(333667) // #38
        step(334682) // #39
        step(334687) // #40
        step(337674) // #41
        step(338674) // #42
        step(339679) // #43
        step(339687) // #44
        step(340639) // #45
        step(342022) // #46
        step(342028) // #47
        fix(342065, 36.5974302, -6.2504103, 159.829f, 0.0f) // loc#48
        step(342746) // #48
        step(342752) // #49
        step(347646) // #50
        step(347657) // #51
        step(348736) // #52
        step(348744) // #53
        step(349743) // #54
        step(349748) // #55
        step(350736) // #56
        fix(351508, 36.5974577, -6.2504235, 381.993f, 0.0f) // loc#49
        step(351641) // #57
        step(351650) // #58
        fix(352033, 36.5976454, -6.2506264, 107.71f, 0.0f) // loc#50
        step(353060) // #59
        step(353063) // #60
        step(353856) // #61
        step(355809) // #62
        step(355812) // #63
        step(355815) // #64
        step(356845) // #65
        step(356864) // #66
        step(356871) // #67
        step(357826) // #68
        step(357842) // #69
        step(358723) // #70
        step(361727) // #71
        step(361732) // #72
        step(364730) // #73
        step(364734) // #74
        step(364736) // #75
        step(365673) // #76
        step(365675) // #77
        step(365677) // #78
        fix(367025, 36.5975591, -6.2502821, 120.142f, 5.26f) // loc#51
        fix(367041, 36.5975591, -6.2502821, 120.142f, 5.26f) // loc#52
        fix(386920, 36.5976886, -6.2505734, 20.155f, 0.0f) // loc#53
        fix(408016, 36.5973719, -6.2507625, 233.283f, 12.19f) // loc#54
        fix(408094, 36.5973719, -6.2507625, 233.283f, 12.19f) // loc#55
        step(415105) // #78
        step(415113) // #78
        step(416103) // #78
        step(416116) // #78
        step(416118) // #78
        fix(465467, 36.5976609, -6.250615, 17.954f, 0.0f) // loc#56
        fix(467655, 36.5976833, -6.2506131, 18.706f, 0.0f) // loc#57
        fix(482051, 36.5976875, -6.2503516, 59.667f, 0.46f) // loc#58
        fix(489488, 36.6020956, -6.2562494, 114.837f, 1.1f) // loc#59
        fix(492482, 36.6020666, -6.2562422, 99.632f, 1.09f) // loc#60
        fix(499476, 36.6021294, -6.2562655, 158.489f, 1.39f) // loc#61
        fix(502472, 36.6021693, -6.256303, 162.233f, 1.39f) // loc#62
        fix(508494, 36.599376, -6.2522499, 165.563f, 20.14f) // loc#63
        fix(508529, 36.599376, -6.2522499, 165.563f, 20.14f) // loc#64
        fix(540632, 36.5976585, -6.2506213, 18.841f, 0.0f) // loc#65
        fix(540702, 36.5976585, -6.2506213, 18.841f, 0.0f) // loc#66
        fix(543493, 36.5975952, -6.2501859, 136.396f, 0.0f) // loc#67
        fix(546483, 36.5974386, -6.2498846, 153.642f, 0.0f) // loc#68
        fix(552501, 36.5976521, -6.2505833, 250.836f, 0.0f) // loc#69
        fix(559489, 36.5970566, -6.249882, 162.928f, 0.0f) // loc#70
        fix(561491, 36.5969655, -6.2498191, 164.28f, 0.0f) // loc#71
        fix(567484, 36.5970545, -6.2495926, 243.371f, 0.0f) // loc#72
        step(570748) // #79
        step(570750) // #80
        step(571711) // #81
        step(571716) // #82
        fix(572464, 36.5974872, -6.250205, 106.908f, 0.0f) // loc#73
        step(572713) // #83
        step(573706) // #84
        step(573708) // #85
        step(575714) // #86
        step(576780) // #87
        fix(576886, 36.597771, -6.2505573, 56.395f, 0.0f) // loc#74
        step(577754) // #88
        step(577756) // #89
        fix(581491, 36.5978461, -6.2506082, 133.07f, 0.0f) // loc#75
        fix(588488, 36.5979373, -6.2507234, 192.927f, 0.0f) // loc#76
    }
}

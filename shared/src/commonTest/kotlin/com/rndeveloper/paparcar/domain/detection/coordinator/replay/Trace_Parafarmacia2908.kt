package com.rndeveloper.paparcar.domain.detection.coordinator.replay

import com.rndeveloper.paparcar.domain.detection.coordinator.ingestion.TraceEvent

/**
 * [DET-GUARDRAILS-KEEP-THE-DOCTRINE-001] Field 2026-08-29 23:47:44–23:56:33 (Redmi, uid `itmG…`,
 * session `c6a57fad`): **the parafarmacia false positive** — the first of the two FPs that caused
 * the detection redesign.
 *
 * An AR `IN_VEHICLE ENTER` was delivered **89 019 ms late** while the phone stood 38 m from its own
 * parked car's fence, so the session armed `enter_at_car`. Then **one** fix carried driving speed:
 * loc#2, 7.71 m/s at 16.086 m accuracy, 71.6 m out from loc#1 — and loc#3, 3.5 s later, had already
 * undone 64.8 m of it, leaving the phone 8.5 m from where it started. That single Doppler sample
 * flipped `hasEverReachedDrivingSpeed` and disarmed the anti-walking aborts. `hasEverMoved` stayed
 * **false** for all 102 fixes; `tripMaxSpeedMps` was stored as 0.0.
 *
 * Twelve steps at rest were then enough for the `steps+egress` fast path, and after the 125 s hold
 * the field build planted an **exact** pin at reliability 0.9 —
 * [PARAFARMACIA_2908_FIELD_PIN_LAT]/[PARAFARMACIA_2908_FIELD_PIN_LON] — replacing a perfectly good
 * pin 58.3 m away and deleting its geofence.
 *
 * Ground truth: **the car never moved**. It sat at
 * [PARAFARMACIA_2908_REAL_CAR_LAT]/[PARAFARMACIA_2908_REAL_CAR_LON] (Calle del Vivero 10A, session
 * `092c74d7`, confirmed 88 minutes earlier at the end of a real 9.3 km drive) the whole time, and it
 * was still there when the session ended.
 */
/**
 * The pin the field build planted — the parafarmacia FP. Trilaterated from 15 SafetyNet fence
 * readings (rms 0.28 m); the log never prints a pin's coordinates.
 *
 * ⚠️ **Read by no test, ON PURPOSE — do not "fix" this by inventing an assertion.**
 * [TEST-A-TRACE-WHOSE-GROUND-TRUTH-IS-NEVER-ASSERTED-001] audited it: this trace's verdict is *no
 * pin, one question*, so there is no position to assert. The coordinate is here to say what was
 * AVOIDED. The one run that would place a pin — the user tapping "Sí" — rests on a false premise,
 * because on this stream the car never moved; and even then the tap would outrank the inference
 * [DET-ASSERTION-OUTRANKS-INFERENCE-001], so there would be nothing to accuse the app of.
 */
const val PARAFARMACIA_2908_FIELD_PIN_LAT = 36.5992450
const val PARAFARMACIA_2908_FIELD_PIN_LON = -6.2513309

/** Where the car actually was, and stayed: the pin this session replaced (`092c74d7`, r=89 m fence).
 *  Trilaterated the same way from 30 readings (rms 0.24 m). */
const val PARAFARMACIA_2908_REAL_CAR_LAT = 36.5991329
const val PARAFARMACIA_2908_REAL_CAR_LON = -6.2519689

/** The AR `IN_VEHICLE ENTER` that armed the session: true time 91 698 ms BEFORE the session began,
 *  delivered with 89 019 ms of lag. */
const val PARAFARMACIA_2908_BOARDING_TRUE_TIME_MS = 1788040064334L - 91_698L

val TRACE_PARAFARMACIA_2908: List<TraceEvent> = buildList {
    val t0 = 1788040064334L
    fun fix(dtMs: Long, lat: Double, lon: Double, acc: Float, speed: Float) =
        add(TraceEvent(t0 + dtMs, TraceEvent.Kind.FIX, lat, lon, acc, speed))
    fun step(dtMs: Long) = add(TraceEvent(t0 + dtMs, TraceEvent.Kind.STEP))
    fix(18, 36.5993395, -6.251627, 16.377f, 0.0f)
    step(3902)
    step(3910)
    step(4903)
    step(4910)
    fix(7746, 36.5990717, -6.2508977, 16.086f, 7.71f)
    step(8727)
    step(8983)
    step(8993)
    fix(11271, 36.5993522, -6.2515334, 11.643f, 1.52f)
    fix(15262, 36.5992437, -6.2513251, 11.277f, 0.25f)
    fix(19267, 36.5992543, -6.2513439, 12.675f, 0.81f)
    fix(24256, 36.5992936, -6.2513616, 28.68f, 0.94f)
    fix(29254, 36.5993314, -6.2513678, 63.197f, 0.76f)
    fix(34289, 36.5993494, -6.2513812, 123.498f, 0.55f)
    fix(39256, 36.599367, -6.2513975, 220.735f, 0.68f)
    fix(45268, 36.5991594, -6.2509808, 211.197f, 1.87f)
    fix(49281, 36.5992046, -6.2509828, 149.692f, 1.51f)
    fix(54259, 36.5992393, -6.2509635, 251.249f, 1.5f)
    fix(61251, 36.5992867, -6.2514176, 85.147f, 0.76f)
    fix(64269, 36.5993088, -6.2514146, 73.599f, 1.08f)
    fix(70256, 36.5993526, -6.2514384, 72.483f, 0.86f)
    fix(74298, 36.5993904, -6.2514596, 180.098f, 0.79f)
    fix(80242, 36.5993901, -6.2514573, 95.179f, 0.27f)
    fix(84263, 36.599393, -6.2514671, 73.136f, 0.26f)
    fix(89273, 36.5994044, -6.251481, 56.654f, 0.54f)
    fix(95283, 36.5993749, -6.2514523, 72.38f, 0.59f)
    fix(99258, 36.5992396, -6.2512527, 56.949f, 1.64f)
    fix(104278, 36.5992397, -6.2511838, 108.136f, 1.63f)
    fix(117254, 36.5993177, -6.2513493, 106.755f, 2.3f)
    fix(133264, 36.5991048, -6.250993, 185.993f, 0.0f)
    fix(139382, 36.5991805, -6.2509969, 115.064f, 1.06f)
    fix(144276, 36.5991826, -6.251272, 147.11f, 0.81f)
    fix(149261, 36.5991994, -6.2512877, 163.505f, 0.58f)
    fix(154265, 36.5990589, -6.2511202, 197.109f, 1.92f)
    fix(159262, 36.5990695, -6.2510776, 139.646f, 2.41f)
    fix(164264, 36.5992233, -6.2513373, 117.587f, 1.77f)
    fix(169261, 36.5993078, -6.2513362, 171.265f, 1.96f)
    fix(177256, 36.5993632, -6.2514284, 248.373f, 0.89f)
    fix(179267, 36.599352, -6.2514474, 239.353f, 0.83f)
    fix(184267, 36.5993548, -6.2514114, 208.872f, 0.84f)
    fix(189292, 36.5993484, -6.2513955, 242.588f, 0.37f)
    fix(194251, 36.5993366, -6.2513912, 139.988f, 0.18f)
    fix(199280, 36.599296, -6.2513784, 68.511f, 0.0f)
    fix(204269, 36.5992759, -6.2513288, 31.073f, 0.0f)
    fix(209260, 36.5992595, -6.2513093, 30.032f, 0.0f)
    fix(214248, 36.5992584, -6.2513075, 72.234f, 0.0f)
    fix(219263, 36.5992543, -6.2513036, 125.632f, 0.0f)
    fix(224278, 36.5992435, -6.2512917, 194.623f, 0.0f)
    fix(229293, 36.5993385, -6.2515752, 65.773f, 0.0f)
    fix(235247, 36.5993391, -6.2515751, 111.016f, 0.0f)
    fix(240265, 36.5993393, -6.2515749, 132.661f, 0.0f)
    fix(245285, 36.599338, -6.2515753, 180.793f, 0.0f)
    fix(249266, 36.5993369, -6.2515757, 187.099f, 0.0f)
    fix(254252, 36.5993357, -6.2515761, 206.615f, 0.0f)
    fix(259298, 36.5993353, -6.2515763, 237.973f, 0.0f)
    fix(265250, 36.5993351, -6.2515761, 94.004f, 0.0f)
    fix(269287, 36.599336, -6.2515755, 71.622f, 0.0f)
    fix(274252, 36.5993363, -6.2515751, 40.707f, 0.0f)
    fix(279256, 36.5993377, -6.2515745, 32.868f, 0.0f)
    fix(284253, 36.599338, -6.2515744, 23.471f, 0.0f)
    fix(289270, 36.5993419, -6.2515778, 16.333f, 0.0f)
    fix(294299, 36.5993426, -6.2515785, 15.605f, 0.0f)
    fix(299296, 36.5993408, -6.251578, 16.042f, 0.0f)
    fix(304263, 36.5993371, -6.2515849, 15.314f, 0.0f)
    fix(309241, 36.5993329, -6.2515884, 14.318f, 0.0f)
    fix(314256, 36.5993305, -6.2515893, 14.762f, 0.0f)
    fix(319269, 36.5993287, -6.2515902, 16.353f, 0.0f)
    fix(324257, 36.5993283, -6.2515904, 17.441f, 0.0f)
    fix(329254, 36.5993276, -6.2515914, 16.644f, 0.0f)
    fix(334253, 36.599328, -6.2515904, 24.756f, 0.0f)
    fix(339241, 36.5993288, -6.2515894, 32.435f, 0.0f)
    fix(344255, 36.5993307, -6.2515874, 53.934f, 0.0f)
    fix(349253, 36.5993314, -6.2515861, 43.146f, 0.0f)
    fix(354257, 36.5993292, -6.2515873, 22.562f, 0.0f)
    fix(359257, 36.5993301, -6.2515851, 14.39f, 0.0f)
    fix(364272, 36.5993306, -6.251584, 18.805f, 0.0f)
    fix(369258, 36.5993285, -6.2515855, 13.634f, 0.0f)
    fix(374253, 36.5993281, -6.2515851, 22.619f, 0.0f)
    step(374755)
    step(376777)
    step(376780)
    step(376782)
    fix(379277, 36.5993274, -6.2515854, 71.223f, 0.0f)
    fix(384250, 36.5993262, -6.2515868, 64.139f, 0.0f)
    fix(389276, 36.5993263, -6.2515864, 54.312f, 0.0f)
    fix(394268, 36.5993275, -6.2515846, 46.543f, 0.0f)
    step(395487)
    step(396496)
    step(396498)
    step(396503)
    fix(399264, 36.5993279, -6.2515839, 22.287f, 0.0f)
    fix(404274, 36.5993291, -6.251582, 27.472f, 0.0f)
    fix(409242, 36.5993301, -6.2515805, 83.215f, 0.0f)
    fix(414265, 36.5993308, -6.2515794, 158.447f, 0.0f)
    fix(419262, 36.5993292, -6.2515808, 62.745f, 0.0f)
    fix(424262, 36.5993279, -6.2515821, 58.581f, 0.0f)
    fix(429265, 36.5993271, -6.251583, 59.955f, 0.0f)
    fix(434257, 36.5993267, -6.2515834, 70.665f, 0.0f)
    fix(439258, 36.5993265, -6.2515836, 72.282f, 0.0f)
    fix(444255, 36.5993266, -6.2515836, 146.168f, 0.0f)
    fix(449259, 36.5993266, -6.2515836, 226.885f, 0.0f)
    fix(455257, 36.5993246, -6.2515854, 258.931f, 0.0f)
    fix(460262, 36.599324, -6.2515866, 150.34f, 0.0f)
    fix(464253, 36.5993239, -6.2515868, 133.351f, 0.0f)
    fix(469263, 36.5993235, -6.2515874, 22.872f, 0.0f)
    fix(474255, 36.5993232, -6.2515879, 19.422f, 0.0f)
    fix(479257, 36.5993226, -6.2515886, 17.68f, 0.0f)
    fix(484261, 36.5993218, -6.2515894, 16.795f, 0.0f)
    fix(489275, 36.5993215, -6.2515897, 18.034f, 0.0f)
    fix(494252, 36.5993213, -6.2515898, 40.201f, 0.0f)
    fix(499259, 36.5993214, -6.2515898, 112.316f, 0.0f)
    step(502250)
    step(502257)
    step(502260)
    step(503243)
    fix(504270, 36.5993212, -6.2515914, 66.066f, 0.0f)
    fix(509254, 36.5993215, -6.2515919, 53.824f, 0.0f)
    fix(514272, 36.599322, -6.251592, 22.344f, 0.0f)
    fix(519258, 36.5993226, -6.2515917, 19.566f, 0.0f)
    fix(524267, 36.5993222, -6.251593, 33.812f, 0.0f)
}

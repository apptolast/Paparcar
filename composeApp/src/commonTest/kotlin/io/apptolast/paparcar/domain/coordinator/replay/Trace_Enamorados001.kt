package io.apptolast.paparcar.domain.coordinator.replay

/**
 * [DET-ANCHOR-EGRESS-001] Field trace of the **1.11 km false positive** (2026-07-15 16:11Z,
 * Firestore `diagnostics/WZB7…/sessions/1784131878857`, Redmi Note 11 / MIUI).
 *
 * Real drive from Glorieta Juan de Austria to Calle Camelias 22. The anchor FROZE at a traffic
 * stop on **Camino de los Enamorados** (Δ 692–711 s: 5 stopped fixes, best acc 16.6 m) and the
 * unfreeze never came — MIUI throttled the stream (gaps 33–79 s) and the only driving fixes left
 * were 10.12 m/s @ acc 52.4 (fails `minGpsAccuracyForDriving=50` by 2.4 m) and 5.62 m/s @ 68.6.
 * Real arrival at Camelias with recovered GPS (acc 3–8 m), genuine egress walk at 1.2–1.35 m/s
 * with **zero step events** (the hardware counter went mute) → the field session confirmed
 * `kinematic+egress` 0.85 **at the frozen anchor**, 1.11 km from the car.
 *
 * The egress displacement check only has a FLOOR: at 1.11 km from the anchor it was trivially
 * true. This ticket adds the CEILING (egress must be born within walk-reach of the anchor) and
 * the bounded egress-birth refinement.
 */
object TraceEnamorados001 {

    const val T0 = 1_784_131_878_857L // session arm (GEOFENCE_EXIT, dep=self_observed), 16:11:18Z

    /** AR `IN_VEHICLE → EXIT` landed here in the field (Δ 868 703 ms) — the replayer only carries
     *  FIX/STEP, so the test emits it via `coordinator.onVehicleExit()` at this timestamp. */
    const val AR_EXIT_AT = T0 + 868_703L

    /** Where the field session planted the pin — the frozen traffic-stop anchor (the BUG). */
    const val FROZEN_ANCHOR_LAT = 36.592125
    const val FROZEN_ANCHOR_LON = -6.2402124

    /** Where the car actually was (user-corrected to Calle Camelias 22; the egress walk both
     *  starts and ends within metres of it). */
    const val REAL_CAR_LAT = 36.5976
    const val REAL_CAR_LON = -6.2506

    /** [DET-CREDIBLE-DRIVE-001] Worst-case MIUI variant: the four post-freeze recovery fixes
     *  (Δ 720 701 / 721 667 at 10.12 m/s and Δ 795 000 / 795 057 at 5.62 m/s) are removed —
     *  those are exactly what sustained departure corroborates — and ONE pedestrian-band fix
     *  (1.2 m/s, acc 60) is kept at the Δ 720 701 position, standing in for the degraded
     *  mid-drive sample any real stream delivers. It breaks the stop (so the traffic light and
     *  the arrival stay SEPARATE stops, as in the field) but can corroborate nothing. With no
     *  sustained departure the anchor stays frozen at the light and the egress-born-at-anchor
     *  CEILING is the last line of defence: prompt, never pin. (Without any moving fix at all
     *  the whole light→arrival span reads as one continuous stop and same-stop refinement walks
     *  the anchor to the arrival by itself — a different, self-correcting regime.) */
    val eventsWithoutRecovery: List<TraceEvent> by lazy {
        val recoveryFixes = setOf(T0 + 720_701L, T0 + 721_667L, T0 + 795_000L, T0 + 795_057L)
        events.filterNot { it.kind == TraceEvent.Kind.FIX && it.tMs in recoveryFixes } +
            TraceEvent(T0 + 720_701L, TraceEvent.Kind.FIX, 36.5934918, -6.2439405, 60f, 1.2f)
    }

    val events: List<TraceEvent> = buildList {
        fun fix(dtMs: Long, lat: Double, lon: Double, acc: Float, speed: Float) =
            add(TraceEvent(T0 + dtMs, TraceEvent.Kind.FIX, lat, lon, acc, speed))
        fun step(dtMs: Long) = add(TraceEvent(T0 + dtMs, TraceEvent.Kind.STEP))

        // ── Departure: stationary at Glorieta Juan de Austria, then pulling out. ─────────────
        fix(38, 36.6049584, -6.2281513, 29.33f, 0f)
        fix(2707, 36.6050722, -6.2281837, 17.89f, 0f)
        fix(5668, 36.6050522, -6.2281687, 15.26f, 0f)
        fix(10650, 36.6051449, -6.2280885, 34.34f, 0f)
        fix(16673, 36.6052287, -6.2277391, 24.51f, 0f)
        fix(20692, 36.6051862, -6.2278291, 25.76f, 0f)
        fix(25708, 36.6051429, -6.227841, 35.96f, 0f)
        fix(31659, 36.6051249, -6.2279321, 67.94f, 0f)
        // Fast leg at 24 m/s — accuracy 77-221 m, so maxSpeed must NOT credit these fixes.
        fix(35678, 36.6044626, -6.2299083, 77.67f, 24.42f)
        fix(40670, 36.6038812, -6.231112, 119.50f, 23.69f)
        fix(45673, 36.6034482, -6.2323153, 221.24f, 23.62f)
        fix(51698, 36.6035057, -6.2317956, 59.64f, 4.21f)
        fix(56674, 36.6037014, -6.231964, 48.37f, 3.96f)
        fix(60664, 36.6035614, -6.2321955, 28.08f, 4.42f)
        fix(66657, 36.6033658, -6.232425, 73.08f, 4.53f)
        fix(70687, 36.603223, -6.2325872, 125.26f, 5.38f)
        fix(75686, 36.6030776, -6.2328158, 217.57f, 5.30f)
        // Credible driving (9-13 m/s, acc ≤ 50) — sessionSawDriving is earned HERE.
        fix(82682, 36.6024041, -6.2334252, 26.99f, 9.87f)
        fix(86641, 36.6020522, -6.233697, 24.75f, 11.72f)
        fix(91670, 36.6015886, -6.2336743, 23.91f, 9.67f)
        fix(95680, 36.6011743, -6.2338959, 50.70f, 9.41f)
        fix(100684, 36.6007604, -6.2340819, 106.06f, 9.40f)
        fix(106627, 36.6004098, -6.2341209, 183.57f, 8.76f)
        fix(111687, 36.6010376, -6.2343019, 20.18f, 3.95f)
        fix(115680, 36.600852, -6.2344136, 18.07f, 5.35f)
        fix(121657, 36.6006341, -6.2345118, 20.25f, 5.11f)
        fix(126646, 36.6002479, -6.2348372, 42.06f, 9.40f)
        fix(131664, 36.5999108, -6.235147, 83.26f, 9.53f)
        fix(136691, 36.5996296, -6.2361598, 47.58f, 13.5f)
        fix(140671, 36.5994785, -6.2367328, 40.19f, 13.14f)
        fix(145685, 36.5993334, -6.2370228, 55.50f, 9.15f)
        fix(150711, 36.5985264, -6.2373601, 69.27f, 11.36f)
        fix(155677, 36.5982264, -6.2378109, 105.97f, 10.88f)
        // ── Intermediate stop with real steps (passenger drop-off) — the anchor may lock here
        // briefly; the credible driving fix at Δ307s clears it, as designed. ─────────────────
        fix(162666, 36.5985385, -6.2375559, 16.28f, 2.13f)
        fix(166648, 36.5985955, -6.2374027, 18.20f, 2.77f)
        fix(171679, 36.5986238, -6.2372213, 47.98f, 2.74f)
        fix(176650, 36.5985985, -6.2371115, 98.29f, 1.98f)
        fix(180671, 36.5980078, -6.2362671, 41.12f, 3.19f)
        fix(185646, 36.5984051, -6.2363539, 51.37f, 1.41f)
        fix(190678, 36.598011, -6.2359544, 117.29f, 5.65f)
        fix(190717, 36.598011, -6.2359544, 117.29f, 5.65f)
        fix(226608, 36.5976254, -6.2357775, 14.35f, 0f)
        fix(226667, 36.5976254, -6.2357775, 14.35f, 0f)
        fix(229717, 36.5976148, -6.2358019, 15.31f, 0.22f)
        fix(232674, 36.5976424, -6.2357925, 11.78f, 0.40f)
        fix(237672, 36.5976564, -6.2356977, 8.50f, 0.93f)
        step(239321)
        step(239330)
        step(240182)
        step(240186)
        step(241143)
        step(241149)
        step(242139)
        step(242143)
        fix(242652, 36.5976722, -6.2357423, 6.38f, 0.57f)
        fix(247666, 36.5976395, -6.2357142, 9.10f, 0.99f)
        step(249694)
        step(250686)
        step(250688)
        fix(252673, 36.5976375, -6.2357145, 7.66f, 0.71f)
        step(252707)
        step(252708)
        step(252710)
        step(252711)
        fix(257648, 36.5976398, -6.2356829, 7.46f, 0.70f)
        fix(262704, 36.5976462, -6.2357539, 10.52f, 0.56f)
        fix(267678, 36.597645, -6.2357929, 11.73f, 0.46f)
        fix(272677, 36.5976622, -6.2358001, 19.29f, 0.62f)
        fix(277662, 36.5976946, -6.2357885, 20.27f, 0.72f)
        fix(282675, 36.5977169, -6.2358009, 19.50f, 0.53f)
        fix(287656, 36.5977054, -6.2358018, 21.23f, 0.48f)
        fix(292670, 36.5977038, -6.2358019, 23.95f, 0.52f)
        fix(297668, 36.5977226, -6.2357943, 28.01f, 0.71f)
        fix(302685, 36.5977132, -6.235731, 19.01f, 2.14f)
        fix(307665, 36.597558, -6.2354496, 20.45f, 5.94f)
        fix(307733, 36.597558, -6.2354496, 20.45f, 5.94f)
        // ── MIUI-throttled legs: gaps of 33-79 s, stops served with degraded accuracy. ───────
        fix(386631, 36.596831, -6.2343171, 16.75f, 0f)
        fix(390364, 36.5960596, -6.2332417, 48.90f, 0f)
        fix(391025, 36.5960444, -6.2330936, 204.89f, 2.06f)
        fix(398648, 36.5960485, -6.2330711, 104.11f, 0f)
        fix(402670, 36.5960476, -6.2330431, 98.38f, 0f)
        fix(407683, 36.5960454, -6.2330336, 76.99f, 0f)
        fix(412714, 36.5960358, -6.2329489, 75.04f, 0f)
        fix(417712, 36.5960296, -6.2329436, 82.25f, 0f)
        fix(422666, 36.5960192, -6.2328151, 186.64f, 0f)
        fix(427669, 36.5960343, -6.2327102, 104.20f, 0f)
        fix(432661, 36.5956509, -6.231621, 94.17f, 6.45f)
        fix(432727, 36.5956509, -6.231621, 94.17f, 6.45f)
        step(477442)
        fix(477499, 36.593982, -6.2301002, 13.65f, 0f)
        fix(481692, 36.593962, -6.2300671, 95.98f, 0f)
        fix(483653, 36.5939558, -6.2300595, 104.72f, 0f)
        fix(489697, 36.5939941, -6.2300011, 146.35f, 0f)
        fix(493697, 36.5939816, -6.2299948, 215.84f, 0f)
        fix(499667, 36.5940395, -6.2299325, 101.39f, 0f)
        fix(503686, 36.5940787, -6.2299262, 87.18f, 0f)
        fix(510650, 36.5941424, -6.2299532, 64.72f, 1.30f)
        fix(513690, 36.5939769, -6.2301371, 29.26f, 0f)
        fix(519671, 36.5937153, -6.2305118, 37.41f, 7.62f)
        fix(519720, 36.5937153, -6.2305118, 37.41f, 7.62f)
        fix(552707, 36.5933489, -6.2320572, 25.90f, 0f)
        fix(555684, 36.5934474, -6.2324817, 70.56f, 0f)
        fix(558688, 36.5934273, -6.2327013, 55.25f, 6.05f)
        fix(558748, 36.5934273, -6.2327013, 55.25f, 6.05f)
        step(618426)
        step(618428)
        fix(618825, 36.5937522, -6.2361418, 23.55f, 0f)
        fix(621374, 36.5933756, -6.2384466, 19.91f, 0f)
        fix(622678, 36.5934071, -6.2368224, 108.76f, 0f)
        fix(624670, 36.5932482, -6.2369872, 87.51f, 0f)
        fix(626667, 36.593085, -6.2370952, 66.90f, 0f)
        fix(633684, 36.5930137, -6.2390395, 9.32f, 5.22f)
        fix(633726, 36.5930137, -6.2390395, 9.32f, 5.22f)
        // ── THE FREEZE: traffic stop at Camino de los Enamorados. 5 stopped fixes (best acc
        // 16.6 m) mature the end-of-drive freeze [DET-SHORT-TRIP-FREEZE-001]. ────────────────
        fix(692499, 36.592125, -6.2402124, 16.61f, 0f)
        fix(695018, 36.5927463, -6.2419451, 98.40f, 0f)
        fix(699687, 36.5921745, -6.2403401, 175.43f, 0f)
        fix(705741, 36.59228, -6.2404514, 103.78f, 0f)
        fix(711655, 36.5923664, -6.240551, 76.21f, 0f)
        // ── Unfreeze starvation: driving resumes but 10.12 m/s carries acc 52.4 (> 50 by
        // 2.4 m!) and 5.62 m/s carries acc 68.6 — the frozen anchor never lets go. ───────────
        fix(715674, 36.5932043, -6.2431125, 74.75f, 0f)
        fix(720701, 36.5934918, -6.2439405, 52.40f, 10.12f)
        fix(721667, 36.5934918, -6.2439405, 52.40f, 10.12f)
        fix(790102, 36.5952509, -6.2475961, 87.60f, 0f)
        fix(795000, 36.5971498, -6.2511308, 68.63f, 5.62f)
        fix(795057, 36.5971498, -6.2511308, 68.63f, 5.62f)
        // ── Real arrival at Calle Camelias — GPS recovers (acc 3-8 m). AR IN_VEHICLE EXIT
        // lands at Δ 868 703 (emitted by the test via onVehicleExit). ────────────────────────
        fix(832490, 36.5978804, -6.2510599, 82.5f, 0f)
        fix(844688, 36.5979966, -6.2509618, 25.18f, 0f)
        fix(848664, 36.5979841, -6.2510074, 19.58f, 0f)
        fix(853673, 36.598072, -6.2509657, 8.04f, 0f)
        fix(858655, 36.5980043, -6.2508915, 6.92f, 0f)
        fix(863659, 36.5979853, -6.2509339, 5.71f, 0f)
        // ── Genuine egress walk (1.24-1.35 m/s, acc 4-8 m) with ZERO steps — the hardware
        // counter went mute; this is exactly the kinematic-egress case. It is born 1.11 km
        // from the frozen anchor: the inconsistency this ticket's ceiling must catch. ────────
        fix(868703, 36.5979842, -6.2509073, 12.04f, 1.08f)
        fix(870574, 36.597935, -6.250981, 15.04f, 1.14f)
        fix(872672, 36.597854, -6.2510071, 11.92f, 1.28f)
        fix(874690, 36.5977926, -6.2510082, 8.11f, 1.26f)
        fix(876684, 36.5977575, -6.2509971, 6.21f, 1.30f)
        fix(878593, 36.5977364, -6.2509755, 4.60f, 1.28f)
        fix(880699, 36.5977221, -6.2509439, 4.33f, 1.28f)
        fix(882688, 36.5977154, -6.2509162, 4.37f, 1.24f)
        fix(884510, 36.597708, -6.2508876, 4.49f, 1.30f)
        fix(886669, 36.5976998, -6.2508565, 4.80f, 1.32f)
        fix(888688, 36.5976837, -6.2508308, 5.23f, 1.33f)
        fix(890669, 36.5976697, -6.2508015, 5.43f, 1.35f)
        fix(892414, 36.5976602, -6.2507752, 5.53f, 1.32f)
        fix(894489, 36.5976476, -6.2507468, 5.59f, 1.30f)
        fix(896594, 36.5976312, -6.2507246, 5.62f, 1.30f)
        fix(898670, 36.5976224, -6.2506935, 5.70f, 1.30f)
        fix(900687, 36.5976121, -6.2506624, 5.72f, 1.27f)
        fix(902661, 36.5976032, -6.2506323, 5.82f, 1.30f)
        fix(904684, 36.5975877, -6.2506143, 5.82f, 1.01f)
        // ── Standing at the doorstep of Camelias 22 until the field DECISION at Δ 998 916:
        // CONFIRMED kinematic+egress 0.85 at the frozen anchor — the 1.11 km false positive. ─
        fix(906679, 36.5975916, -6.2506155, 5.60f, 0.46f)
        fix(908666, 36.5975824, -6.2506274, 4.84f, 0.15f)
        fix(910666, 36.5975782, -6.2506347, 3.97f, 0.12f)
        fix(912681, 36.597572, -6.2506398, 3.65f, 0.12f)
        fix(914659, 36.5975694, -6.2506423, 3.62f, 0.15f)
        fix(916662, 36.5975689, -6.2506427, 3.68f, 0.13f)
        fix(918667, 36.5975682, -6.2506426, 3.83f, 0.14f)
        fix(920542, 36.597567, -6.2506435, 3.95f, 0.13f)
        fix(922255, 36.5975692, -6.2506403, 3.95f, 0.25f)
        fix(924514, 36.5975665, -6.25062, 4.10f, 0.90f)
        fix(926653, 36.5975705, -6.2506177, 4.21f, 0.64f)
        fix(928656, 36.5975733, -6.2506283, 4.04f, 0.15f)
        fix(930681, 36.5975776, -6.2506322, 3.45f, 0.11f)
        fix(932664, 36.5975801, -6.2506404, 3.19f, 0.06f)
        fix(934588, 36.5975798, -6.2506446, 3.25f, 0.07f)
        fix(936667, 36.5975802, -6.2506534, 3.39f, 0.07f)
        fix(938670, 36.5975833, -6.2506554, 3.54f, 0.07f)
        fix(940675, 36.5975857, -6.2506597, 3.65f, 0.07f)
        fix(942669, 36.5975872, -6.2506622, 3.68f, 0.07f)
        fix(944664, 36.5975875, -6.2506661, 3.71f, 0.07f)
        fix(946680, 36.5975876, -6.2506685, 3.90f, 0.07f)
        fix(948670, 36.597588, -6.2506763, 3.81f, 0.07f)
        fix(950664, 36.5975886, -6.2506808, 3.49f, 0.08f)
        fix(952670, 36.5975893, -6.2506829, 3.21f, 0.08f)
        fix(954681, 36.5975915, -6.2506812, 3f, 0.09f)
        fix(956683, 36.5975921, -6.2506804, 3f, 0.09f)
        fix(958666, 36.5975927, -6.2506796, 3f, 0.09f)
        fix(960660, 36.5975932, -6.2506787, 3f, 0.10f)
        fix(962600, 36.5975938, -6.2506779, 3f, 0.10f)
        fix(964677, 36.597596, -6.2506741, 3.05f, 0.11f)
        fix(966667, 36.5975968, -6.2506733, 3.21f, 0.12f)
        fix(968662, 36.5975996, -6.2506681, 3.66f, 0.62f)
        fix(970669, 36.5976042, -6.250658, 5.73f, 1.09f)
        fix(972677, 36.5976187, -6.2506354, 7.72f, 1.09f)
        fix(974660, 36.5976205, -6.2506432, 8.70f, 0.63f)
        fix(976665, 36.5976279, -6.2506465, 9.53f, 0.56f)
        fix(978665, 36.5976337, -6.2506557, 9.57f, 0.25f)
        fix(980615, 36.5976359, -6.2506507, 9.30f, 0.16f)
        fix(982669, 36.5976391, -6.2506564, 8.22f, 0.22f)
        fix(984660, 36.5976371, -6.2506722, 7.73f, 0.49f)
        fix(986662, 36.597643, -6.2506824, 7.60f, 0.41f)
        fix(988681, 36.5976486, -6.2506644, 7.62f, 0.21f)
        fix(990667, 36.5976498, -6.2506645, 7.81f, 0.16f)
        fix(992667, 36.5976484, -6.250664, 7.84f, 0.14f)
        fix(994663, 36.5976483, -6.2506621, 7.86f, 0.12f)
        fix(996672, 36.5976484, -6.250652, 7.69f, 0.09f)
        fix(998680, 36.5976479, -6.2506502, 7.42f, 0.09f)
    }
}

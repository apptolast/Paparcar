package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [DET-HONEST-CLOSE-001] Pure-decision coverage for the honest-close ladder. The two field aborts
 * that motivated the ticket are pinned as direct inputs: the Camelias hop (driven → zone) and the
 * D2 return (walked → silent). The gate that separates them is the hardware step budget, never
 * distance alone.
 *
 * [DET-STEP-BUDGET-ORIGIN-001] The budget is compared against the displacement measured FROM THE
 * SEAL POINT (where the body was when the counter was zeroed), never from the pin: sealing happens
 * at confirm time, mid-egress, so the egress walk is excluded from the count but was included in
 * the pin distance — the mix that read a walk home as a ride (Glorieta regression below).
 *
 * [DET-FROZEN-COUNTER-001] The budget's authority depends on the counter being ALIVE. The Jerez
 * restaurant regression pins the frozen-counter case: the session's own step detector is the
 * liveness witness, and a cumulative delta below it proves the counter frozen → silence.
 *
 * [DET-WALK-FLOOR-001] The budget's authority also depends on there being enough DISTANCE for the
 * count to mean anything, and no inference ever deposes a pin the USER asserted. The Glorieta FP
 * (field 2026-07-26) pins both: a hand-placed pin released on a one-step margin over a 32 m walk.
 */
class EvaluateHonestCloseUseCaseTest {

    private val useCase = EvaluateHonestCloseUseCase(ParkingDetectionConfig())

    private companion object {
        /** A seal minutes old — the shape of every legit close (they abort MINUTES after the
         *  real trip); keeps the seal-age gate out of the way of the other regressions. */
        const val FRESH_SEAL_AGE_MS = 10 * 60 * 1_000L
    }

    private fun pinAt(lat: Double, lon: Double, acc: Float = 12f, reliability: Float? = null) = UserParking(
        id = "stale",
        vehicleId = "v-1",
        location = GpsPoint(lat, lon, accuracy = acc, timestamp = 0L, speed = 0f),
        geofenceId = "stale-fence",
        isActive = true,
        detectionReliability = reliability,
    )

    private fun fixAt(lat: Double, lon: Double, acc: Float) =
        GpsPoint(lat, lon, accuracy = acc, timestamp = 0L, speed = 0f)

    @Test
    fun should_open_approximate_zone_when_short_hop_is_driven_but_anchor_is_not_pin_grade() {
        // Camelias hop (field 2026-07-14): Melgarejo pin → ~318 m to Camelias, only 23 steps since
        // the seal (the drive counted none; 23 is the walk around the new spot). The seal happened
        // beside the pin, so the displacement since it is the same ~318 m: 23 ≪ 170 (=40 % of
        // 318/0.75) → driven. The new spot's urban accuracy (60 m > pin-grade) → ZONE, not pin.
        val verdict = useCase(
            stalePin = pinAt(36.6002, -6.2512),
            abortFix = fixAt(36.5974, -6.2505, acc = 60f),
            stepsSinceStalePin = 23L,
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            stepSealPoint = fixAt(36.6002, -6.2512, acc = 10f),
        )
        val zone = assertIs<HonestCloseDecision.ApproximateZone>(verdict.decision)
        assertTrue(zone.radiusMeters >= 60f, "the zone must read as an area, not a dot")
        assertEquals(HonestCloseVerdict.REASON_TRIP_PROVEN, verdict.reason)
        assertNotNull(verdict.requiredSteps, "the trace must carry the budget the walk failed")
    }

    // ── The walk between the car and this fix [DET-CLOSE-ZONE-WHEN-THE-BODY-WALKED-001] ────────

    @Test
    fun should_open_a_zone_the_walk_wide_when_a_precise_fix_lands_far_from_the_car() {
        // Field 2026-08-21 23:50, Oppo (session 1787348798966). Covirán → home after a 7 min 43 s
        // blind gap: 991 m of pin distance, 206 counted steps against the 530 the walk demanded,
        // and an abort fix precise to 3,6 m. The old rule read only that 3,6 and planted the car
        // INSIDE the user's house, ~80 m from the kerb he had actually been dropped at.
        //
        // The fix was not imprecise — it was confident about the wrong thing. 206 × 0,75 m = 154 m
        // of walking stands between that fix and the car, and that is what the artifact must say.
        val verdict = useCase(
            stalePin = pinAt(36.6143783, -6.2863817, acc = 2.25f),
            abortFix = fixAt(36.6083588, -6.2781826, acc = 3.644f),
            stepsSinceStalePin = 206L,
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            stepSealPoint = fixAt(36.6143783, -6.2863817, acc = 2.25f),
        )
        val zone = assertIs<HonestCloseDecision.ApproximateZone>(
            verdict.decision,
            "a fix 154 m of walking away from the car is not a point",
        )
        assertEquals(HonestCloseVerdict.REASON_TRIP_PROVEN, verdict.reason)
        assertEquals(206L * ParkingDetectionConfig().strideMeters, zone.radiusMeters)
        assertTrue(
            zone.radiusMeters >= 80f,
            "the zone must reach the real drop-off point, ~80 m from where the body stood",
        )
    }

    @Test
    fun should_keep_the_pin_when_the_counted_walk_is_short_enough_to_be_pin_grade() {
        // The shape every legit close has: the abort follows the park by minutes, so the counter
        // has ticked a handful of times and the doubt it buys is smaller than a pin-grade fix's own
        // error. Precision earned, not assumed — the Camelias/D2 cases keep exactly what they had.
        val verdict = useCase(
            stalePin = pinAt(36.6002, -6.2512),
            abortFix = fixAt(36.5974, -6.2505, acc = 8f),
            stepsSinceStalePin = 20L, // 15 m of walking — well inside pin-grade
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            stepSealPoint = fixAt(36.6002, -6.2512, acc = 10f),
        )
        assertIs<HonestCloseDecision.ApproximatePin>(verdict.decision)
        assertEquals(HonestCloseVerdict.REASON_TRIP_PROVEN, verdict.reason)
    }

    @Test
    fun should_cap_the_zone_so_it_never_paints_half_a_neighbourhood() {
        // A long blind gap with a big step budget would otherwise draw a kilometre-wide "area",
        // which stops being an answer. Same ceiling the unattended zone uses; the artifact is still
        // saved at the cap and the nudge is the ask-to-refine.
        val config = ParkingDetectionConfig()
        val verdict = useCase(
            stalePin = pinAt(36.7000, -6.4000),
            abortFix = fixAt(36.6083588, -6.2781826, acc = 4f),
            stepsSinceStalePin = 4_000L, // 3 km of walking on paper
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            stepSealPoint = fixAt(36.7000, -6.4000, acc = 10f),
        )
        val zone = assertIs<HonestCloseDecision.ApproximateZone>(verdict.decision)
        assertEquals(config.unattendedZoneMaxRadiusMeters, zone.radiusMeters)
    }

    @Test
    fun should_fall_back_to_the_fix_accuracy_when_the_counter_offers_no_bound() {
        // Measured driving needs no counter, and a mute one has no walk to report: the artifact is
        // decided by the fix alone, exactly as it always was.
        val verdict = useCase(
            stalePin = pinAt(36.6002, -6.2512, reliability = 1f),
            abortFix = fixAt(36.5974, -6.2505, acc = 8f),
            stepsSinceStalePin = null,
            sealAgeMs = null,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            stepSealPoint = null,
            sessionMaxSpeedMps = 12f,
        )
        assertIs<HonestCloseDecision.ApproximatePin>(verdict.decision)
        assertEquals(HonestCloseVerdict.REASON_SESSION_MEASURED_DRIVING, verdict.reason)
    }

    @Test
    fun should_stay_silent_when_the_walk_explains_the_distance() {
        // D2 return (field 2026-07-15): the user WALKED ~1.1 km from the still-parked car; the
        // stale exit was delivered at rest at the destination. The hardware counter recorded the
        // whole walk (~1099 steps ≥ 589 = 40 % of 1104/0.75) → the car never moved → silence.
        val verdict = useCase(
            stalePin = pinAt(36.6054, -6.2727),
            abortFix = fixAt(36.6088, -6.2843, acc = 3f),
            stepsSinceStalePin = 1099L,
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            stepSealPoint = fixAt(36.6054, -6.2727, acc = 10f),
        )
        assertEquals(HonestCloseDecision.KeepSilent, verdict.decision, "a walk must never release the pin")
        assertEquals(HonestCloseVerdict.REASON_WALK_EXPLAINS, verdict.reason)
    }

    @Test
    fun should_drop_approximate_pin_when_driven_and_a_pin_grade_fix_is_in_hand() {
        // Trip proven (15 steps ≪ ~300 m since the seal) AND a pin-grade fix (acc 8 ≤ 50) →
        // rung 1: a soft point.
        val verdict = useCase(
            stalePin = pinAt(36.6000, -6.2500),
            abortFix = fixAt(36.6027, -6.2500, acc = 8f),
            stepsSinceStalePin = 15L,
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            stepSealPoint = fixAt(36.6000, -6.2500, acc = 10f),
        )
        val pin = assertIs<HonestCloseDecision.ApproximatePin>(verdict.decision)
        assertEquals(8f, pin.location.accuracy)
    }

    @Test
    fun should_stay_silent_when_the_counter_is_mute() {
        // Same driven geometry as the hop, but a MUTE counter cannot prove a ride OR rule out a
        // long walk → conservative silence; the safety net's mute-counter proofs are the backstop.
        val verdict = useCase(
            stalePin = pinAt(36.6002, -6.2512),
            abortFix = fixAt(36.5974, -6.2505, acc = 60f),
            stepsSinceStalePin = null,
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            stepSealPoint = fixAt(36.6002, -6.2512, acc = 10f),
        )
        assertEquals(HonestCloseDecision.KeepSilent, verdict.decision)
        assertEquals(HonestCloseVerdict.REASON_MUTE_COUNTER, verdict.reason)
    }

    @Test
    fun should_stay_silent_when_the_displacement_is_gps_wobble_beside_the_car() {
        // ~30 m from the stale pin — within both accuracy envelopes plus the trip floor. A re-arm
        // jitter beside the parked car, not a drive-away.
        val verdict = useCase(
            stalePin = pinAt(36.6000, -6.2500, acc = 12f),
            abortFix = fixAt(36.60027, -6.2500, acc = 10f),
            stepsSinceStalePin = 3L,
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            stepSealPoint = fixAt(36.6000, -6.2500, acc = 10f),
        )
        assertEquals(HonestCloseDecision.KeepSilent, verdict.decision)
        assertEquals(HonestCloseVerdict.REASON_TOO_CLOSE, verdict.reason)
    }

    @Test
    fun should_stay_silent_when_there_is_no_stale_pin_to_reason_about() {
        val verdict = useCase(
            stalePin = null,
            abortFix = fixAt(36.5974, -6.2505, acc = 8f),
            stepsSinceStalePin = 5L,
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            stepSealPoint = fixAt(36.5974, -6.2505, acc = 8f),
        )
        assertEquals(HonestCloseDecision.KeepSilent, verdict.decision)
        assertEquals(HonestCloseVerdict.REASON_NO_STALE_PIN, verdict.reason)
    }

    // ── [DET-STEP-BUDGET-ORIGIN-001] Same-origin regression ──────────────────────────────────────

    @Test
    fun should_stay_silent_when_the_seal_happened_mid_egress_and_the_walk_since_it_explains_the_rest() {
        // Glorieta FP (field 2026-07-22 01:47, Redmi): parked at la Angelita (pin), walked home.
        // The baseline sealed at CONFIRM time, mid-egress ~160 m from the pin; the remaining ~85 m
        // walk home cost ~110 steps. Against the PIN distance (~243 m) the old gate demanded 129
        // steps → 110 < 129 read as "driven" and planted a 0.5 pin INSIDE the user's home,
        // deposing the real 0.9 park. Against the SEAL distance (~85 m) the walk explains itself
        // (110 ≥ 45) → silence, the real pin survives.
        val verdict = useCase(
            stalePin = pinAt(36.6057922, -6.2315528, acc = 5f),
            abortFix = fixAt(36.6038644, -6.2302701, acc = 15f),
            stepsSinceStalePin = 110L,
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            stepSealPoint = fixAt(36.604539, -6.230719, acc = 10f),
        )
        assertEquals(
            HonestCloseDecision.KeepSilent,
            verdict.decision,
            "a walk home after parking must never replace the real pin with one at the walker's position",
        )
    }

    @Test
    fun should_stay_silent_when_the_seal_recorded_no_origin() {
        // Legacy seal (steps without a position): the budget is not comparable — steps measured
        // from an unknown point cannot be judged against any displacement. Conservative silence;
        // the safety net remains the backstop.
        val verdict = useCase(
            stalePin = pinAt(36.6002, -6.2512),
            abortFix = fixAt(36.5974, -6.2505, acc = 8f),
            stepsSinceStalePin = 23L,
            stepSealPoint = null,
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = null,
            witnessAgeMs = null,
        )
        assertEquals(HonestCloseDecision.KeepSilent, verdict.decision)
        assertEquals(HonestCloseVerdict.REASON_NO_SEAL_ORIGIN, verdict.reason)
    }

    // ── [DET-FROZEN-COUNTER-001] Counter-liveness regression ─────────────────────────────────────

    @Test
    fun should_stay_silent_when_the_cumulative_counter_is_provably_frozen() {
        // Jerez restaurant FP (field 2026-07-25 22:29, Redmi): Calle Cobre pin sealed at confirm,
        // user WALKED ~150 m to the restaurant. The MIUI cumulative counter froze in background —
        // delta 2 over a walk the session's own step DETECTOR witnessed (8 pedestrian steps before
        // the false-ENTER abort). A live cumulative delta can never be below the in-session
        // detector count → frozen → silence. The old gate read "2 ≪ 80 required" as a ride and
        // planted an approximate pin inside the restaurant, deposing the correct 6-minute-old pin.
        val verdict = useCase(
            stalePin = pinAt(36.69944, -6.10992, acc = 10f),
            abortFix = fixAt(36.70078, -6.10972, acc = 10f),
            stepsSinceStalePin = 2L,
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            stepSealPoint = fixAt(36.69944, -6.10992, acc = 10f),
            sessionStepEvents = 8,
        )
        assertEquals(
            HonestCloseDecision.KeepSilent,
            verdict.decision,
            "a frozen cumulative counter must never testify a ride",
        )
        assertEquals(HonestCloseVerdict.REASON_FROZEN_COUNTER, verdict.reason)
    }

    @Test
    fun should_still_prove_the_trip_when_the_session_saw_no_steps_and_the_counter_agrees() {
        // Liveness witness absent (sessionStepEvents = 0 — e.g. a no-movement abort where nobody
        // walked): the cross-check must stay out of the way and the Camelias budget still decides.
        val verdict = useCase(
            stalePin = pinAt(36.6002, -6.2512),
            abortFix = fixAt(36.5974, -6.2505, acc = 60f),
            stepsSinceStalePin = 23L,
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            stepSealPoint = fixAt(36.6002, -6.2512, acc = 10f),
            sessionStepEvents = 0,
        )
        assertIs<HonestCloseDecision.ApproximateZone>(verdict.decision)
    }

    // ── [DET-WALK-FLOOR-001] Walk floor + user-asserted pin regression ───────────────────────────

    @Test
    fun should_stay_silent_when_the_body_displaced_too_little_for_the_budget_to_judge() {
        // Glorieta FP (field 2026-07-26 20:28, Oppo), with an AUTO pin so the floor alone is on
        // trial: pin 100.4 m from the abort (passes the pin-distance trip floor) but the BODY only
        // displaced ~32 m since the seal — the budget demanded 18 steps and the live counter gave
        // 16. A verdict that flips on a one-step margin is quantization noise, not ride proof.
        val verdict = useCase(
            stalePin = pinAt(36.604657, -6.230782, acc = 8f, reliability = 0.9f),
            abortFix = fixAt(36.604041, -6.2299597, acc = 3.5f),
            stepsSinceStalePin = 16L,
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            stepSealPoint = fixAt(36.60379, -6.23014, acc = 5f),
            sessionStepEvents = 13,
        )
        assertEquals(
            HonestCloseDecision.KeepSilent,
            verdict.decision,
            "a one-step budget margin over a 32 m walk must never testify a ride",
        )
        assertEquals(HonestCloseVerdict.REASON_WALK_TOO_SHORT, verdict.reason)
        assertNotNull(verdict.walkDistanceMeters, "the trace must carry the displacement that was too short")
    }

    @Test
    fun should_stay_silent_when_the_stale_pin_is_the_users_own_assertion() {
        // Same abort, but the pin is the one the user HAND-PLACED 12 minutes earlier (reliability
        // 1.0 is stamped by user confirmation only). The step budget is an inference; an inference
        // never deposes an assertion — regardless of geometry.
        val verdict = useCase(
            stalePin = pinAt(36.604657, -6.230782, acc = 8f, reliability = 1.0f),
            abortFix = fixAt(36.604041, -6.2299597, acc = 3.5f),
            stepsSinceStalePin = 16L,
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            stepSealPoint = fixAt(36.60379, -6.23014, acc = 5f),
            sessionStepEvents = 13,
        )
        assertEquals(
            HonestCloseDecision.KeepSilent,
            verdict.decision,
            "a step-budget inference must never release a pin the user asserted",
        )
        assertEquals(HonestCloseVerdict.REASON_USER_ASSERTED_PIN, verdict.reason)
    }

    @Test
    fun should_release_a_user_asserted_pin_when_the_session_itself_measured_driving() {
        // The shield yields to MEASURED movement: a session that reached driving speed proves the
        // car left even a hand-placed pin behind — assertion is outranked only by measurement.
        val verdict = useCase(
            stalePin = pinAt(36.6002, -6.2512, reliability = 1.0f),
            abortFix = fixAt(36.5974, -6.2505, acc = 8f),
            stepsSinceStalePin = null,
            stepSealPoint = null,
            sealAgeMs = null,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            sessionMaxSpeedMps = 9f,
        )
        assertIs<HonestCloseDecision.ApproximatePin>(verdict.decision)
        assertEquals(HonestCloseVerdict.REASON_SESSION_MEASURED_DRIVING, verdict.reason)
    }

    // ── [DET-TRIP-WITNESS-001] Seal-age regression ───────────────────────────────────────────────

    @Test
    fun should_stay_silent_when_the_seal_is_hours_old_even_with_no_session_witness() {
        // Glorieta home FP (field 2026-07-30 17:53, Redmi): MIUI delivered an EXIT echo of the
        // Angelita fence to a phone sitting at HOME since the previous night. The 16-hour-old
        // seal's cumulative delta read 0 (frozen through sleep + process deaths) and the aborting
        // session witnessed 0 steps (nobody moved) — so the frozen-counter cross-check was blind
        // and "198 m without steps" became a proven trip: the correct pin was released and an
        // approximate pin planted on the user's home. The budget EXPIRES: a seal this old refuses
        // the verdict outright, witness or no witness.
        val verdict = useCase(
            stalePin = pinAt(36.60583, -6.23159, acc = 5f, reliability = 0.9f),
            abortFix = fixAt(36.60387, -6.23029, acc = 16f),
            stepsSinceStalePin = 0L,
            stepSealPoint = fixAt(36.60583, -6.23159, acc = 5f),
            sealAgeMs = 16 * 60 * 60 * 1_000L,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            sessionStepEvents = 0,
        )
        assertEquals(
            HonestCloseDecision.KeepSilent,
            verdict.decision,
            "a 16-hour-old step delta must never testify a ride",
        )
        assertEquals(HonestCloseVerdict.REASON_STALE_SEAL, verdict.reason)
    }

    @Test
    fun should_stay_silent_when_the_seal_age_is_just_over_the_ceiling() {
        // Camelias geometry (trip otherwise provable) with the seal one ms past the ceiling —
        // the boundary is the ceiling itself, inclusive on the fresh side.
        val verdict = useCase(
            stalePin = pinAt(36.6002, -6.2512),
            abortFix = fixAt(36.5974, -6.2505, acc = 60f),
            stepsSinceStalePin = 23L,
            stepSealPoint = fixAt(36.6002, -6.2512, acc = 10f),
            sealAgeMs = ParkingDetectionConfig().honestCloseMaxSealAgeMs + 1L,
            lastWitnessedFix = null,
            witnessAgeMs = null,
        )
        assertEquals(HonestCloseDecision.KeepSilent, verdict.decision)
        assertEquals(HonestCloseVerdict.REASON_STALE_SEAL, verdict.reason)
    }

    @Test
    fun should_still_prove_the_trip_when_the_seal_age_is_at_the_ceiling() {
        // Same geometry with the seal exactly AT the ceiling → the budget still testifies.
        val verdict = useCase(
            stalePin = pinAt(36.6002, -6.2512),
            abortFix = fixAt(36.5974, -6.2505, acc = 60f),
            stepsSinceStalePin = 23L,
            stepSealPoint = fixAt(36.6002, -6.2512, acc = 10f),
            sealAgeMs = ParkingDetectionConfig().honestCloseMaxSealAgeMs,
            lastWitnessedFix = null,
            witnessAgeMs = null,
        )
        assertIs<HonestCloseDecision.ApproximateZone>(verdict.decision)
        assertEquals(HonestCloseVerdict.REASON_TRIP_PROVEN, verdict.reason)
    }

    @Test
    fun should_stay_silent_when_the_seal_has_no_timestamp() {
        // Legacy seal (steps + position, no timestamp): unknown age is indistinguishable from old
        // — refuse rather than guess. The safety net remains the backstop.
        val verdict = useCase(
            stalePin = pinAt(36.6002, -6.2512),
            abortFix = fixAt(36.5974, -6.2505, acc = 60f),
            stepsSinceStalePin = 23L,
            stepSealPoint = fixAt(36.6002, -6.2512, acc = 10f),
            sealAgeMs = null,
            lastWitnessedFix = null,
            witnessAgeMs = null,
        )
        assertEquals(HonestCloseDecision.KeepSilent, verdict.decision)
        assertEquals(HonestCloseVerdict.REASON_STALE_SEAL, verdict.reason)
    }

    @Test
    fun should_prove_the_trip_directly_when_the_session_itself_measured_driving() {
        // Measured movement outranks every inference: a session that reached driving speed proves
        // the ride with no step budget at all (defensive today — the two triggering aborts never
        // carry driving speed — decisive for any future caller that does).
        val verdict = useCase(
            stalePin = pinAt(36.6002, -6.2512),
            abortFix = fixAt(36.5974, -6.2505, acc = 8f),
            stepsSinceStalePin = null,
            stepSealPoint = null,
            sealAgeMs = null,
            lastWitnessedFix = null,
            witnessAgeMs = null,
            sessionMaxSpeedMps = 9f,
        )
        assertIs<HonestCloseDecision.ApproximatePin>(verdict.decision)
        assertEquals(HonestCloseVerdict.REASON_SESSION_MEASURED_DRIVING, verdict.reason)
    }

    // ── [DET-UNWITNESSED-DISPLACEMENT-001] Abort-fix coherence vs the last witnessed position ───

    @Test
    fun should_stay_silent_when_the_abort_fix_teleported_from_a_fresh_witness() {
        // Cantarranas FP (field 2026-08-19 03:26, Oppo asleep at home): indoor multipath
        // teleported the fix ~995 m with optimistic accuracy (7 m) while an independent wake had
        // witnessed the phone stationary AT HOME 32 s earlier — implied ~107 km/h between two
        // stationary observations, zero measured movement all night. Every other gate passed
        // legitimately (seal cure-fresh at home, delta 36 alive ≥ 13 session steps, 36 ≪ 511
        // required); only the witness contradiction exposes the mirage. Silence — whichever
        // endpoint was the mirage, no fix is pin-grade when witnesses disagree.
        val home = fixAt(36.60793, -6.27807, acc = 14f)
        val verdict = useCase(
            stalePin = pinAt(36.608515, -6.27778, acc = 2.2f, reliability = 0.9f),
            abortFix = fixAt(36.6164806, -6.2748436, acc = 7.016f),
            stepsSinceStalePin = 36L,
            stepSealPoint = home,
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = home,
            witnessAgeMs = 32_000L,
            sessionStepEvents = 13,
        )
        assertEquals(
            HonestCloseDecision.KeepSilent,
            verdict.decision,
            "a fix the body could not have reached must never prove a trip",
        )
        assertEquals(HonestCloseVerdict.REASON_UNWITNESSED_DISPLACEMENT, verdict.reason)
        assertNotNull(verdict.witnessDistanceMeters, "the trace must carry the contradiction it refused")
    }

    @Test
    fun should_prove_the_trip_when_the_witness_is_hours_old() {
        // Same teleport geometry but the witness is 2 h stale — the time term dwarfs any real
        // displacement, the gate is transparent (this is what keeps Camelias/D2-shaped late
        // closes untouched), and the step budget testifies as before. The witness numbers are
        // still stamped so the field audits how close legit trips come to the ceiling.
        val home = fixAt(36.60793, -6.27807, acc = 14f)
        val verdict = useCase(
            stalePin = pinAt(36.608515, -6.27778, acc = 2.2f, reliability = 0.9f),
            abortFix = fixAt(36.6164806, -6.2748436, acc = 7.016f),
            stepsSinceStalePin = 36L,
            stepSealPoint = home,
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = home,
            witnessAgeMs = 2 * 60 * 60 * 1_000L,
            sessionStepEvents = 13,
        )
        assertIs<HonestCloseDecision.ApproximatePin>(verdict.decision)
        assertEquals(HonestCloseVerdict.REASON_TRIP_PROVEN, verdict.reason)
        assertNotNull(verdict.witnessDistanceMeters)
        assertNotNull(verdict.witnessAgeMs)
    }

    @Test
    fun should_stay_silent_when_the_displacement_is_just_over_the_implied_speed_ceiling() {
        // Envelope arithmetic at the boundary: accuracies 10+10 and 32 s × 15 m/s allow 500 m;
        // ~523 m (0.00470° of latitude) is over → refused.
        val witness = fixAt(36.60000, -6.25000, acc = 10f)
        val verdict = useCase(
            stalePin = pinAt(36.60000, -6.25000, acc = 2f),
            abortFix = fixAt(36.60470, -6.25000, acc = 10f),
            stepsSinceStalePin = 0L,
            stepSealPoint = witness,
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = witness,
            witnessAgeMs = 32_000L,
        )
        assertEquals(HonestCloseDecision.KeepSilent, verdict.decision)
        assertEquals(HonestCloseVerdict.REASON_UNWITNESSED_DISPLACEMENT, verdict.reason)
    }

    @Test
    fun should_prove_the_trip_when_the_displacement_sits_under_the_implied_speed_ceiling() {
        // ~479 m (0.00430°) against the same 500 m allowance is physically reachable → the gate
        // steps aside and the zero-step budget proves the ride as before.
        val witness = fixAt(36.60000, -6.25000, acc = 10f)
        val verdict = useCase(
            stalePin = pinAt(36.60000, -6.25000, acc = 2f),
            abortFix = fixAt(36.60430, -6.25000, acc = 10f),
            stepsSinceStalePin = 0L,
            stepSealPoint = witness,
            sealAgeMs = FRESH_SEAL_AGE_MS,
            lastWitnessedFix = witness,
            witnessAgeMs = 32_000L,
        )
        assertIs<HonestCloseDecision.ApproximatePin>(verdict.decision)
        assertEquals(HonestCloseVerdict.REASON_TRIP_PROVEN, verdict.reason)
    }
}

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.VehicleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit coverage for the pure candidate-phase decision. [DET-D-04]
 *
 * These exercise the wall-clock-driven paths (`windowElapsed`) that were impossible to drive
 * through the coordinator's real-Clock collect loop, plus the Prague replay (steps without egress).
 */
class EvaluateParkingDecisionUseCaseTest {

    private val config = ParkingDetectionConfig()
    private val evaluate = EvaluateParkingDecisionUseCase(config)

    // window for the no-exit slow path (5 min) and exit path (2 min)
    private val slowWindow = config.confirmationObservationWindowMs
    private val exitWindow = config.vehicleExitObservationWindowMs

    private fun input(
        stepCount: Int = 0,
        hasEgressDisplacement: Boolean = false,
        hadVehicleExit: Boolean = false,
        elapsedSinceHighMs: Long = 0L,
        vehicleType: VehicleType? = VehicleType.CAR,
        sessionDurationMs: Long = 60_000L,
        maxSpeedKmh: Float = 60f,
        evidenceLabel: String? = null,
        hasKinematicEgress: Boolean = false,
        lastSpeedMps: Float = 0f,
        egressExceedsWalkReach: Boolean = false,
    ) = ParkingDecisionInput(
        stepCount, hasEgressDisplacement, hadVehicleExit,
        elapsedSinceHighMs, vehicleType, sessionDurationMs, maxSpeedKmh, evidenceLabel,
        hasKinematicEgress, lastSpeedMps,
        egressExceedsWalkReach = egressExceedsWalkReach,
    )

    // ── Kinematic egress: GPS-measured walk from the frozen anchor [DET-KINEMATIC-EGRESS-001] ─

    @Test
    fun should_confirm_via_kinematicEgress_when_sessionMeasuredDriving() {
        // Mute step counter (field 2026-07-11, Redmi): zero steps, but the frozen anchor watched
        // a sustained quality walk away. Confirms at its own reliability tier + path label.
        val decision = evaluate(
            input(
                stepCount = 0,
                hasEgressDisplacement = true,
                hasKinematicEgress = true,
                maxSpeedKmh = 25f,
            )
        )
        val confirmed = assertIs<ParkingDecision.Confirmed>(decision)
        assertEquals("kinematic+egress", confirmed.pathLabel)
        assertEquals(config.reliabilityKinematicEgress, confirmed.reliability, 0.0001f)
    }

    // ── Rolling veto: never confirm while the car is still moving [DET-STEP-SPEED-GATE-001] ─────

    @Test
    fun should_confirm_steps_egress_when_user_exits_fast_and_is_stationary_or_walking() {
        // A user who parks and jumps out in < 25 s must NOT become a false negative: at the moment
        // steps+egress fires they are stationary or walking away (lastSpeedMps below the pedestrian
        // ceiling), so the rolling veto does not apply. [caso "usuario rápido"]
        val stationary = evaluate(input(stepCount = 8, hasEgressDisplacement = true, lastSpeedMps = 0f))
        assertEquals("steps+egress", assertIs<ParkingDecision.Confirmed>(stationary).pathLabel)

        val walkingAway = evaluate(input(stepCount = 8, hasEgressDisplacement = true, lastSpeedMps = 1.4f))
        assertEquals("steps+egress", assertIs<ParkingDecision.Confirmed>(walkingAway).pathLabel)
    }

    @Test
    fun should_reject_steps_egress_when_car_is_still_rolling_in_traffic() {
        // FP Avenida de los Mástiles (field 2026-07-12): phantom steps + growing displacement while
        // the car crawled through traffic. steps+egress is blind to instantaneous speed, so it used
        // to CONFIRM mid-route. With the last fix above the pedestrian ceiling (4 m/s) the proofs are
        // present but the car is rolling → decisive Rejected, not a saved pin.
        val decision = evaluate(input(stepCount = 8, hasEgressDisplacement = true, lastSpeedMps = 4f))
        assertEquals(ParkingDecision.Rejected, decision)
    }

    @Test
    fun should_notConfirmViaKinematics_when_sessionNeverMeasuredDriving() {
        // A seeded arm whose stream never saw the trip: the freeze can mature on a pedestrian
        // stand, so kinematics alone must not confirm — stay inconclusive, keep asking.
        val decision = evaluate(
            input(
                stepCount = 0,
                hasEgressDisplacement = true,
                hasKinematicEgress = true,
                maxSpeedKmh = 4f,
            )
        )
        assertIs<ParkingDecision.Inconclusive>(decision)
    }

    @Test
    fun should_prompt_notConfirm_when_kinematicEgressOnHumanPoweredProfile() {
        val decision = evaluate(
            input(
                stepCount = 0,
                hasEgressDisplacement = true,
                hasKinematicEgress = true,
                maxSpeedKmh = 25f,
                vehicleType = VehicleType.BIKE,
            )
        )
        assertIs<ParkingDecision.Prompt>(decision)
    }

    @Test
    fun should_preferStepsPath_when_bothProofsPresent() {
        // Steps are ground truth — when they speak, the label and reliability are theirs.
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                hasKinematicEgress = true,
                maxSpeedKmh = 25f,
            )
        )
        val confirmed = assertIs<ParkingDecision.Confirmed>(decision)
        assertEquals("steps+egress", confirmed.pathLabel)
        assertEquals(config.reliabilityVehicleExit, confirmed.reliability, 0.0001f)
    }

    @Test
    fun should_notConfirmViaKinematics_without_egressDisplacement() {
        // DET-C-01: egress displacement is mandatory for every confirm path, this one included.
        val decision = evaluate(
            input(
                stepCount = 0,
                hasEgressDisplacement = false,
                hasKinematicEgress = true,
                maxSpeedKmh = 25f,
            )
        )
        assertIs<ParkingDecision.Inconclusive>(decision)
    }

    // ── Weak-evidence policy [DET-SOLID-001] ────────────────────────────────────

    @Test
    fun should_prompt_when_only_evidence_is_vehicle_enter_and_session_never_drove() {
        // ENTER-only arm (falsifiable by bus/taxi) + the session's own stream never saw driving
        // → all confirm conditions hold but the save must ask, not assert.
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                maxSpeedKmh = 4f,
                sessionDurationMs = 60_000L, // short session — mismatch guard (>=8 min) not in play
                evidenceLabel = io.apptolast.paparcar.domain.detection.ArmEvidence.LABEL_VERIFIED_ENTER,
            )
        )
        assertIs<ParkingDecision.Prompt>(decision)
        assertEquals("steps+egress", decision.pathLabel)
    }

    @Test
    fun should_confirm_when_enter_evidence_is_corroborated_by_observed_driving() {
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                maxSpeedKmh = 30f, // the session itself witnessed the drive
                evidenceLabel = io.apptolast.paparcar.domain.detection.ArmEvidence.LABEL_VERIFIED_ENTER,
            )
        )
        assertIs<ParkingDecision.Confirmed>(decision)
    }

    @Test
    fun should_confirm_with_speed_evidence_even_without_observed_driving() {
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                maxSpeedKmh = 4f,
                evidenceLabel = io.apptolast.paparcar.domain.detection.ArmEvidence.LABEL_VERIFIED_SPEED,
            )
        )
        assertIs<ParkingDecision.Confirmed>(decision)
    }

    // ── Human-powered opt-out [DET-SOLID-001 C2] ────────────────────────────────

    @Test
    fun should_prompt_never_confirm_for_bike_profile() {
        // A bike crossing 18 km/h once looks like a car to every speed signal — always ask.
        val decision = evaluate(
            input(stepCount = 8, hasEgressDisplacement = true, vehicleType = VehicleType.BIKE, maxSpeedKmh = 26f)
        )
        assertIs<ParkingDecision.Prompt>(decision)
    }

    @Test
    fun should_prompt_never_confirm_for_scooter_profile() {
        val decision = evaluate(
            input(stepCount = 8, hasEgressDisplacement = true, vehicleType = VehicleType.SCOOTER, maxSpeedKmh = 26f)
        )
        assertIs<ParkingDecision.Prompt>(decision)
    }

    @Test
    fun should_keep_auto_confirm_for_motorcycle_profile() {
        val decision = evaluate(
            input(stepCount = 8, hasEgressDisplacement = true, vehicleType = VehicleType.MOTORCYCLE, maxSpeedKmh = 45f)
        )
        assertIs<ParkingDecision.Confirmed>(decision)
    }

    @Test
    fun should_confirm_enter_only_when_strong_evidence_flag_is_off() {
        val relaxed = EvaluateParkingDecisionUseCase(ParkingDetectionConfig(autoConfirmRequiresStrongEvidence = false))
        val decision = relaxed(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                maxSpeedKmh = 4f,
                sessionDurationMs = 60_000L,
                evidenceLabel = io.apptolast.paparcar.domain.detection.ArmEvidence.LABEL_VERIFIED_ENTER,
            )
        )
        assertIs<ParkingDecision.Confirmed>(decision)
    }

    // ── Steps path ──────────────────────────────────────────────────────────────

    @Test
    fun should_confirm_via_steps_when_steps_and_egress_present() {
        val decision = evaluate(input(stepCount = 8, hasEgressDisplacement = true))
        assertIs<ParkingDecision.Confirmed>(decision)
        assertEquals("steps+egress", decision.pathLabel)
        assertEquals(config.reliabilityVehicleExit, decision.reliability)
    }

    @Test
    fun should_stay_inconclusive_when_steps_present_but_no_egress_and_window_open() {
        // The Prague false positive at the decision level: 8 steps from a bouncing phone but the
        // car never moved → no egress → must NOT confirm while the window is still open.
        val decision = evaluate(input(stepCount = 8, hasEgressDisplacement = false, elapsedSinceHighMs = 10_000L))
        assertEquals(ParkingDecision.Inconclusive, decision)
    }

    @Test
    fun should_reject_when_steps_present_but_no_egress_and_window_elapsed() {
        // Same Prague inputs, but the (no-exit) 5-min window has now expired → discard the candidate.
        val decision = evaluate(input(stepCount = 8, hasEgressDisplacement = false, elapsedSinceHighMs = slowWindow))
        assertEquals(ParkingDecision.Rejected, decision)
    }

    // ── Pedestrian egress ceiling [DET-EGRESS-PEDESTRIAN-CEILING-001] ────────────

    @Test
    fun should_reject_steps_egress_when_displacement_outran_the_steps() {
        // FP Calle Abeto (field 2026-07-18): stopped to pick a passenger up. ~26 incidental steps
        // were counted while parked and the car then drove ~500 m away — a displacement no pedestrian
        // could cover in those steps. steps+egress would have pinned a phantom park at the stop. The
        // egress floor is cleared but the ceiling is breached (a vehicle moved, not a person) →
        // decisive Rejected, and the real park after it is free to anchor.
        val decision = evaluate(
            input(stepCount = 26, hasEgressDisplacement = true, lastSpeedMps = 0f, egressExceedsWalkReach = true)
        )
        assertEquals(ParkingDecision.Rejected, decision)
    }

    @Test
    fun should_confirm_steps_egress_when_displacement_keeps_pace_with_steps() {
        // Regression guard: a genuine walk-away keeps pace with its own step count (outruns=false),
        // so the ceiling never touches a real park. Same inputs as the canonical steps+egress confirm.
        val decision = evaluate(
            input(stepCount = 26, hasEgressDisplacement = true, lastSpeedMps = 0f, egressExceedsWalkReach = false)
        )
        assertEquals("steps+egress", assertIs<ParkingDecision.Confirmed>(decision).pathLabel)
    }

    @Test
    fun should_still_confirm_via_kinematics_even_when_displacement_outruns_the_zero_steps() {
        // The kinematic path proves egress from pedestrian-BAND fixes (a departing car cannot make
        // them) and legitimately carries ~0 steps, so `outruns` is trivially true for it. The ceiling
        // must NOT veto it — only the step- and window-based paths.
        val decision = evaluate(
            input(
                stepCount = 0,
                hasEgressDisplacement = true,
                hasKinematicEgress = true,
                maxSpeedKmh = 25f,
                egressExceedsWalkReach = true,
            )
        )
        assertEquals("kinematic+egress", assertIs<ParkingDecision.Confirmed>(decision).pathLabel)
    }

    @Test
    fun should_not_confirm_via_exit_window_when_displacement_is_vehicular() {
        // The vehicle-exit + window path also leans on the egress floor; a car-scale displacement
        // must not confirm it either (the car simply drove off a stop). No kinematic proof → blocked.
        val decision = evaluate(
            input(
                hadVehicleExit = true,
                hasEgressDisplacement = true,
                elapsedSinceHighMs = exitWindow,
                egressExceedsWalkReach = true,
            )
        )
        assertEquals(ParkingDecision.Rejected, decision)
    }

    // ── Vehicle-exit + window path ───────────────────────────────────────────────

    @Test
    fun should_confirm_via_exit_window_when_exit_and_egress_and_window_elapsed() {
        val decision = evaluate(
            input(hadVehicleExit = true, hasEgressDisplacement = true, elapsedSinceHighMs = exitWindow),
        )
        assertIs<ParkingDecision.Confirmed>(decision)
        assertEquals("vehicleExit+window+egress", decision.pathLabel)
    }

    @Test
    fun should_reject_when_exit_and_window_elapsed_but_no_egress() {
        // A spurious AR exit during a long traffic stop: window elapses, but the user never walked
        // away → no egress → discard, never confirm. [DET-C-01]
        val decision = evaluate(
            input(hadVehicleExit = true, hasEgressDisplacement = false, elapsedSinceHighMs = exitWindow),
        )
        assertEquals(ParkingDecision.Rejected, decision)
    }

    @Test
    fun should_stay_inconclusive_when_exit_and_egress_but_window_not_elapsed() {
        // Exit + egress but no steps and the 2-min window hasn't elapsed yet → keep waiting.
        val decision = evaluate(
            input(hadVehicleExit = true, hasEgressDisplacement = true, elapsedSinceHighMs = exitWindow - 1),
        )
        assertEquals(ParkingDecision.Inconclusive, decision)
    }

    @Test
    fun should_select_longer_window_when_no_vehicle_exit() {
        // No exit → slow window (5 min). At 2.5 min (past the exit window but not the slow one) a
        // no-exit candidate with no proof is still Inconclusive, proving the window selection.
        val decision = evaluate(input(hadVehicleExit = false, hasEgressDisplacement = true, elapsedSinceHighMs = exitWindow + 1))
        assertEquals(ParkingDecision.Inconclusive, decision)
        assertTrue(exitWindow + 1 < slowWindow, "sanity: 2-min+1ms is still under the 5-min slow window")
    }

    // ── No-proof paths ───────────────────────────────────────────────────────────

    @Test
    fun should_stay_inconclusive_when_no_proof_and_window_open() {
        assertEquals(ParkingDecision.Inconclusive, evaluate(input(elapsedSinceHighMs = 1_000L)))
    }

    @Test
    fun should_reject_when_no_proof_and_window_elapsed() {
        assertEquals(ParkingDecision.Rejected, evaluate(input(elapsedSinceHighMs = slowWindow)))
    }

    // ── Scooter mismatch guard ───────────────────────────────────────────────────

    @Test
    fun should_suppress_confirm_when_car_profile_on_sustained_slow_trip() {
        // CAR profile, 10-min trip never above 20 km/h, with steps+egress that would otherwise
        // confirm → mismatch suppresses it. Window still open → Inconclusive.
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                elapsedSinceHighMs = 1_000L,
                vehicleType = VehicleType.CAR,
                sessionDurationMs = 10 * 60_000L,
                maxSpeedKmh = 20f,
            ),
        )
        assertEquals(ParkingDecision.Inconclusive, decision)
    }

    @Test
    fun should_not_suppress_when_non_car_vehicle_even_if_slow() {
        // Same slow trip but not a CAR → mismatch guard doesn't apply → steps+egress confirm.
        val decision = evaluate(
            input(
                stepCount = 8,
                hasEgressDisplacement = true,
                vehicleType = VehicleType.MOTORCYCLE,
                sessionDurationMs = 10 * 60_000L,
                maxSpeedKmh = 20f,
            ),
        )
        assertIs<ParkingDecision.Confirmed>(decision)
    }
}

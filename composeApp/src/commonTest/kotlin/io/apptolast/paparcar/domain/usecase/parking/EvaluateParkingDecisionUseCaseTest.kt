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
    ) = ParkingDecisionInput(
        stepCount, hasEgressDisplacement, hadVehicleExit,
        elapsedSinceHighMs, vehicleType, sessionDurationMs, maxSpeedKmh,
    )

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

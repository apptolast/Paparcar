package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.detection.DetectionPhase
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DET-TIERS-001] Bluetooth-as-arbiter truth table: the deterministic paired-car edge SUPERSEDES a
 * running probabilistic coordinator session, it never scores. Disconnect confirms via BT; connect
 * while about to pin vetoes; anything else is a no-op.
 */
class EvaluateBtArbitrationUseCaseTest {

    private val useCase = EvaluateBtArbitrationUseCase()
    private val car = "vehicle-A"

    private fun evaluate(
        event: BtArbitrationEvent,
        running: Boolean = true,
        phase: DetectionPhase = DetectionPhase.Driving,
        btVehicleId: String? = car,
        coordinatorVehicleId: String? = car,
    ) = useCase(event, running, phase, btVehicleId, coordinatorVehicleId)

    // ── DISCONNECT ──────────────────────────────────────────────────────────

    @Test
    fun should_supersede_when_disconnectWithRunningSessionSameCar() {
        assertEquals(
            BtArbitrationVerdict.SupersedeWithBluetooth,
            evaluate(BtArbitrationEvent.DISCONNECT, phase = DetectionPhase.Candidate),
        )
    }

    @Test
    fun should_supersede_when_disconnectWhileDriving() {
        // A paired-MAC disconnect is authoritative even if the coordinator still thinks it's driving.
        assertEquals(
            BtArbitrationVerdict.SupersedeWithBluetooth,
            evaluate(BtArbitrationEvent.DISCONNECT, phase = DetectionPhase.Driving),
        )
    }

    @Test
    fun should_noOp_when_disconnectWithNoRunningSession() {
        // BT is primary and the coordinator is suppressed — the normal BT flow proceeds untouched.
        assertEquals(
            BtArbitrationVerdict.NoOp,
            evaluate(BtArbitrationEvent.DISCONNECT, running = false),
        )
    }

    @Test
    fun should_noOp_when_disconnectFromDifferentCar() {
        assertEquals(
            BtArbitrationVerdict.NoOp,
            evaluate(BtArbitrationEvent.DISCONNECT, btVehicleId = "vehicle-B", coordinatorVehicleId = car),
        )
    }

    @Test
    fun should_supersede_when_disconnectAndSessionOriginUnknown() {
        // Manual-start session has no resolved origin vehicle → trust the deterministic BT edge.
        assertEquals(
            BtArbitrationVerdict.SupersedeWithBluetooth,
            evaluate(BtArbitrationEvent.DISCONNECT, coordinatorVehicleId = null),
        )
    }

    // ── CONNECT ─────────────────────────────────────────────────────────────

    @Test
    fun should_veto_when_connectWhileCandidateSameCar() {
        assertEquals(
            BtArbitrationVerdict.VetoReturnToVehicle,
            evaluate(BtArbitrationEvent.CONNECT, phase = DetectionPhase.Candidate),
        )
    }

    @Test
    fun should_noOp_when_connectWhileDriving() {
        // Reconnecting mid-trip is consistent with driving — no pin to veto.
        assertEquals(
            BtArbitrationVerdict.NoOp,
            evaluate(BtArbitrationEvent.CONNECT, phase = DetectionPhase.Driving),
        )
    }

    @Test
    fun should_noOp_when_connectWithNoRunningSession() {
        assertEquals(
            BtArbitrationVerdict.NoOp,
            evaluate(BtArbitrationEvent.CONNECT, running = false, phase = DetectionPhase.Candidate),
        )
    }

    @Test
    fun should_noOp_when_connectCandidateFromDifferentCar() {
        assertEquals(
            BtArbitrationVerdict.NoOp,
            evaluate(
                BtArbitrationEvent.CONNECT,
                phase = DetectionPhase.Candidate,
                btVehicleId = "vehicle-B",
                coordinatorVehicleId = car,
            ),
        )
    }

    @Test
    fun should_veto_when_connectCandidateAndSessionOriginUnknown() {
        assertEquals(
            BtArbitrationVerdict.VetoReturnToVehicle,
            evaluate(BtArbitrationEvent.CONNECT, phase = DetectionPhase.Candidate, coordinatorVehicleId = null),
        )
    }
}

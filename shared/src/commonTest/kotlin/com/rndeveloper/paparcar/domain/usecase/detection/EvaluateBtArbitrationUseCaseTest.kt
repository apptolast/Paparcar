package com.rndeveloper.paparcar.domain.usecase.detection

import com.rndeveloper.paparcar.domain.detection.DetectionPhase
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DET-TIERS-001] Bluetooth-as-arbiter truth table: the deterministic paired-car edge SUPERSEDES a
 * running probabilistic coordinator session, it never scores. Disconnect confirms via BT; connect
 * while about to pin vetoes; anything else is a no-op.
 *
 * [DET-BT-WRONG-CAR-ABORT-001] A BT edge from a DIFFERENT own car refutes the session's
 * nominated-vehicle hypothesis (the phone cannot be in two cars): disconnect supersedes, connect
 * while driving yields — field 2026-08-10 double pin (Kamiq bt 19:50:29 + Focus steps+egress
 * 19:51:44) happened exactly because both used to be NoOp.
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
    fun should_supersede_when_disconnectFromDifferentOwnCar() {
        // Field 2026-08-10: session nominated the Focus (geofence exit of its parked fence) while
        // the user actually drove the Kamiq; the Kamiq's disconnect at the destination must abort
        // the session — the park belongs to the BT car, a coordinator pin would be a misattributed
        // duplicate. [DET-BT-WRONG-CAR-ABORT-001]
        assertEquals(
            BtArbitrationVerdict.SupersedeWithBluetooth,
            evaluate(BtArbitrationEvent.DISCONNECT, btVehicleId = "vehicle-B", coordinatorVehicleId = car),
        )
    }

    @Test
    fun should_supersede_when_disconnectFromDifferentOwnCarWhileCandidate() {
        assertEquals(
            BtArbitrationVerdict.SupersedeWithBluetooth,
            evaluate(
                BtArbitrationEvent.DISCONNECT,
                phase = DetectionPhase.Candidate,
                btVehicleId = "vehicle-B",
                coordinatorVehicleId = car,
            ),
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
    fun should_noOp_when_connectWhileDrivingSameCar() {
        // Reconnecting mid-trip to the SAME car is consistent with driving — no pin to veto.
        assertEquals(
            BtArbitrationVerdict.NoOp,
            evaluate(BtArbitrationEvent.CONNECT, phase = DetectionPhase.Driving),
        )
    }

    @Test
    fun should_yield_when_connectToDifferentOwnCarWhileDriving() {
        // Boarding a different own car mid-session (field 2026-08-10 19:44:18, BT enabled mid-drive)
        // proves the phone is in THAT car — the nominated-vehicle hypothesis is refuted; abort before
        // any pin forms. Bluetooth owns detection while connected. [DET-BT-WRONG-CAR-ABORT-001]
        assertEquals(
            BtArbitrationVerdict.YieldToConnectedCar,
            evaluate(
                BtArbitrationEvent.CONNECT,
                phase = DetectionPhase.Driving,
                btVehicleId = "vehicle-B",
                coordinatorVehicleId = car,
            ),
        )
    }

    @Test
    fun should_noOp_when_connectToDifferentOwnCarWhileCandidate() {
        // Park car A, walk to car B: the pending pin for A was earned with measured pre-connect
        // evidence — boarding B refutes the future, not the already-measured past. Let it land.
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
    fun should_noOp_when_connectWithNoRunningSession() {
        assertEquals(
            BtArbitrationVerdict.NoOp,
            evaluate(BtArbitrationEvent.CONNECT, running = false, phase = DetectionPhase.Candidate),
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

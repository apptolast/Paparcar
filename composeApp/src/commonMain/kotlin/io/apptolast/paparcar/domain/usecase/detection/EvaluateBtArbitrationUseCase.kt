package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.detection.DetectionPhase

/** Which physical Bluetooth edge fired from the PAIRED car. [DET-TIERS-001] */
enum class BtArbitrationEvent { CONNECT, DISCONNECT }

/**
 * The arbiter's ruling over an in-progress probabilistic Coordinator session when a deterministic
 * Bluetooth edge from the paired car fires. [DET-TIERS-001]
 *
 * Bluetooth NEVER enters the coordinator's scoring (hard project rule) — it does not add a signal,
 * it SUPERSEDES the whole session.
 */
sealed interface BtArbitrationVerdict {
    /** No running coordinator session to override, a different car, or an edge consistent with
     *  driving — leave the coordinator untouched; the normal Bluetooth flow proceeds. */
    data object NoOp : BtArbitrationVerdict

    /** DISCONNECT while a coordinator session runs: the user has left the paired car. Cancel the
     *  coordinator's probabilistic ladder/prompt and let the deterministic Bluetooth path confirm the
     *  park (reliabilityBluetooth) — the event nominates, the paired-MAC disconnect is the measured
     *  proof of egress. */
    data object SupersedeWithBluetooth : BtArbitrationVerdict

    /** CONNECT while a coordinator session is about to pin (Candidate/prompt): the user is back IN
     *  the car, so the tentative park is false. Discard the candidate/prompt and re-seal the anchor
     *  (asymmetric failure — better no pin than a phantom one). */
    data object VetoReturnToVehicle : BtArbitrationVerdict
}

/**
 * Pure arbiter — Bluetooth as the deterministic OVERRIDE of the probabilistic coordinator, never a
 * scoring signal. [DET-TIERS-001]
 *
 *  - a DISCONNECT while a session runs = the paired car's engine/link dropped → the user is leaving
 *    it → confirm via Bluetooth and cancel the coordinator's ladder;
 *  - a CONNECT while a session is about to pin (Candidate) = the user is back in the car → veto the
 *    pending pin;
 *  - a CONNECT during plain Driving is consistent with a trip and left alone.
 *
 * Vehicle guard: only arbitrates when the Bluetooth car matches the session's departing vehicle (or
 * that origin is unknown), so an edge from car A never vetoes a coordinator trip following car B.
 *
 * The prerequisite that makes the arbiter trustworthy — the BT speed gate, walk-away timeout and
 * tests of [EvaluateBtParkUseCase] — already landed with DET-AUDIT-002.
 */
class EvaluateBtArbitrationUseCase {

    operator fun invoke(
        event: BtArbitrationEvent,
        coordinatorRunning: Boolean,
        coordinatorPhase: DetectionPhase,
        btVehicleId: String?,
        coordinatorVehicleId: String?,
    ): BtArbitrationVerdict {
        // Nothing to supersede when no session is live.
        if (!coordinatorRunning) return BtArbitrationVerdict.NoOp
        // Don't let one car's Bluetooth edge touch a session that is following a different car.
        if (!sameVehicle(btVehicleId, coordinatorVehicleId)) return BtArbitrationVerdict.NoOp

        return when (event) {
            BtArbitrationEvent.DISCONNECT -> BtArbitrationVerdict.SupersedeWithBluetooth
            BtArbitrationEvent.CONNECT -> when (coordinatorPhase) {
                DetectionPhase.Candidate -> BtArbitrationVerdict.VetoReturnToVehicle
                DetectionPhase.Driving -> BtArbitrationVerdict.NoOp
            }
        }
    }

    /** Same car, or the session's origin vehicle is unknown (trust the deterministic paired-MAC
     *  edge, which is by definition about the physical car the user is in). */
    private fun sameVehicle(btVehicleId: String?, coordinatorVehicleId: String?): Boolean =
        coordinatorVehicleId == null || btVehicleId == null || btVehicleId == coordinatorVehicleId
}

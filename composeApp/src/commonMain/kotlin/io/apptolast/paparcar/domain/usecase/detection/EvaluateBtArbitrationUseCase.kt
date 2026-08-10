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
    /** No running coordinator session to override, or an edge consistent with the session — leave
     *  the coordinator untouched; the normal Bluetooth flow proceeds. */
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

    /** CONNECT to a DIFFERENT own paired car while the coordinator is still driving: the phone is
     *  provably inside that other car, so the session's nominated vehicle (a geofence-exit HYPOTHESIS
     *  — the fence only proves the PHONE left, not that THAT car moved) is refuted. Abort the session;
     *  Bluetooth owns detection while connected [DET-BT-CONNECTED-NOT-PAIRED-001]. Without this the
     *  coordinator confirms a phantom re-park of the wrong car at the destination (field 2026-08-10:
     *  double pin Kamiq bt + Focus steps+egress 75 s apart). [DET-BT-WRONG-CAR-ABORT-001] */
    data object YieldToConnectedCar : BtArbitrationVerdict
}

/**
 * Pure arbiter — Bluetooth as the deterministic OVERRIDE of the probabilistic coordinator, never a
 * scoring signal. [DET-TIERS-001]
 *
 *  - a DISCONNECT while a session runs = the paired car's engine/link dropped → the user is leaving
 *    it → confirm via Bluetooth and cancel the coordinator's ladder;
 *  - a CONNECT while a session is about to pin (Candidate) = the user is back in the car → veto the
 *    pending pin;
 *  - a CONNECT during plain Driving is consistent with a trip and left alone — unless it is a
 *    DIFFERENT own car (below).
 *
 * Vehicle identity [DET-BT-WRONG-CAR-ABORT-001]: every event reaching this arbiter is from one of
 * the user's OWN paired cars (the receiver drops unknown MACs), and the phone can only be in one
 * car. The session's `coordinatorVehicleId` is a NOMINATION HYPOTHESIS (a geofence exit only proves
 * the phone left the area); a BT edge from a different own car refutes it:
 *  - DISCONNECT from a different own car → the user was in THAT car and just left it. The park that
 *    is physically happening belongs to the BT car and the BT path will place it deterministically;
 *    a coordinator pin for the nominated car would always be a misattributed duplicate → supersede.
 *  - CONNECT to a different own car while Driving → the drive being measured from here on is the BT
 *    car's; abort before any pin forms (this kills the field bug at boarding time, minutes before
 *    the pin). While Candidate → NoOp: the pending pin is backed by measured pre-connect evidence
 *    (park car A, walk to car B); aborting could only lose a real park (asymmetric failure prefers
 *    acting on proof, and the proof here refutes the future, not the already-measured past).
 *  - Unknown origin (either id null) keeps trusting the deterministic paired-MAC edge as before.
 *
 * The prerequisite that makes the arbiter trustworthy — the BT speed gate, walk-away timeout and
 * tests of [EvaluateBtParkUseCase] — already landed with DET-AUDIT-002. The out-of-range disconnect
 * of a parked own car cannot reach the different-car branch with a live session: while connected to
 * it the coordinator is not allowed to arm [DET-BT-CONNECTED-NOT-PAIRED-001], and a connect that
 * happens mid-session yields the session first.
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

        // A different OWN car's edge refutes the session's nominated-vehicle hypothesis — the phone
        // cannot be in two cars. [DET-BT-WRONG-CAR-ABORT-001]
        if (isDifferentOwnCar(btVehicleId, coordinatorVehicleId)) {
            return when (event) {
                BtArbitrationEvent.DISCONNECT -> BtArbitrationVerdict.SupersedeWithBluetooth
                BtArbitrationEvent.CONNECT -> when (coordinatorPhase) {
                    DetectionPhase.Driving -> BtArbitrationVerdict.YieldToConnectedCar
                    // The pending pin was earned with pre-connect measured evidence — let it land.
                    DetectionPhase.Candidate -> BtArbitrationVerdict.NoOp
                }
            }
        }

        return when (event) {
            BtArbitrationEvent.DISCONNECT -> BtArbitrationVerdict.SupersedeWithBluetooth
            BtArbitrationEvent.CONNECT -> when (coordinatorPhase) {
                DetectionPhase.Candidate -> BtArbitrationVerdict.VetoReturnToVehicle
                DetectionPhase.Driving -> BtArbitrationVerdict.NoOp
            }
        }
    }

    /** Both identities known and distinct — the BT car is provably NOT the session's car. A null on
     *  either side means "origin unknown" → trust the deterministic paired-MAC edge (same-car path). */
    private fun isDifferentOwnCar(btVehicleId: String?, coordinatorVehicleId: String?): Boolean =
        btVehicleId != null && coordinatorVehicleId != null && btVehicleId != coordinatorVehicleId
}

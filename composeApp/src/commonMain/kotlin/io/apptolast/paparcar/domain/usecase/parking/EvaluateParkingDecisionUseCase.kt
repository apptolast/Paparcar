package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.VehicleType

/**
 * Outcome of [EvaluateParkingDecisionUseCase] for a stop that has reached the CANDIDATE phase.
 * Mirror of [DepartureDecision] — the parking half now speaks the same corroboration language as
 * the departure half. [DET-D-01]
 *
 * - [Confirmed]    — two independent signals agree (always including egress displacement, the one
 *                    signal impossible to fake at a traffic stop). Carries the path that decided and
 *                    the reliability to stamp on the saved session.
 * - [Rejected]     — the observation window expired without the egress conjunction → discard the
 *                    candidate (likely a queue / traffic stop).
 * - [Inconclusive] — keep the candidate open; the window has not elapsed and no proof has arrived.
 */
sealed interface ParkingDecision {
    data class Confirmed(val pathLabel: String, val reliability: Float) : ParkingDecision
    data object Rejected : ParkingDecision
    data object Inconclusive : ParkingDecision
    /** All confirm conditions hold, but the arm evidence is too weak to save silently
     *  (ENTER-only, falsifiable by bus/taxi) — ask the user instead. [DET-SOLID-001] */
    data class Prompt(val pathLabel: String) : ParkingDecision
}

/**
 * Pure inputs for one candidate-phase decision. Deliberately primitives (not the coordinator's
 * private state) so the decision is a pure function of corroboration signals — replayable from a
 * recorded trace without any coroutine / Flow / sensor machinery. [DET-D-02]
 */
data class ParkingDecisionInput(
    /** Pedestrian steps counted while stopped post-drive. */
    val stepCount: Int,
    /** Whether the current fix is ≥ `minEgressDisplacementMeters` from the pinned park anchor. */
    val hasEgressDisplacement: Boolean,
    /** Snapshot of `vehicleExitConfirmed` at candidate entry — selects the observation window. */
    val hadVehicleExit: Boolean,
    /** Wall-clock ms elapsed since the candidate reached HIGH confidence. */
    val elapsedSinceHighMs: Long,
    /** Active vehicle profile (for the scooter mismatch guard); null until locked. */
    val vehicleType: VehicleType?,
    /** Session wall-clock duration (for the mismatch guard). */
    val sessionDurationMs: Long,
    /** Session top speed in km/h (for the mismatch guard and the weak-evidence policy). */
    val maxSpeedKmh: Float,
    /** Arm-evidence persist label of the session (see [io.apptolast.paparcar.domain.detection.ArmEvidence]);
     *  null for legacy callers. Kept a flat string so the input stays replayable. [DET-SOLID-001] */
    val evidenceLabel: String? = null,
)

/**
 * The candidate-phase decision, extracted from `CoordinatorParkingDetector.evaluateCandidatePhase`
 * as a pure function so the 9-path precedence collapses into one testable place. [DET-D-02]
 *
 * **Invariant (DET-C-01):** egress displacement is mandatory for every [ParkingDecision.Confirmed].
 * STILL, dwell-time and AR-exit-time on their own never confirm — they only keep the candidate
 * [ParkingDecision.Inconclusive] until the window expires, then [ParkingDecision.Rejected].
 *
 * Behaviour is identical to the pre-extraction coordinator; the orchestrator still owns the side
 * effects (running the confirm, mutating the phase, posting notifications).
 */
class EvaluateParkingDecisionUseCase(private val config: ParkingDetectionConfig) {

    operator fun invoke(input: ParkingDecisionInput): ParkingDecision {
        val hasStepsProof = input.stepCount >= config.minStepsToConfirm && input.hasEgressDisplacement
        val window = if (input.hadVehicleExit)
            config.vehicleExitObservationWindowMs
        else
            config.confirmationObservationWindowMs
        val windowElapsed = input.elapsedSinceHighMs >= window

        // Scooter mismatch guard: a CAR profile on a sustained slow trip looks like a moped —
        // suppress auto-confirm and leave it to the user prompt. [BUG-SCOOTER-001]
        val isMismatch = input.vehicleType == VehicleType.CAR &&
            input.sessionDurationMs >= config.mismatchMinSessionDurationMs &&
            input.maxSpeedKmh <= config.mismatchMaxSpeedKmh

        val confirmNow = when {
            isMismatch -> false
            // [DET-C-01] Egress is mandatory for every path.
            !input.hasEgressDisplacement -> false
            hasStepsProof -> true
            windowElapsed && input.hadVehicleExit -> true
            else -> false
        }

        // [DET-SOLID-001] Weak-evidence policy: the arm's only vehicle proof is an AR ENTER
        // (falsifiable by bus/taxi) AND the session's own stream never witnessed driving speed —
        // all confirm conditions hold, but the save is not trustworthy enough to be silent.
        val sessionSawDriving = input.maxSpeedKmh >= config.minimumTripSpeedMps * KMH_PER_MPS
        val weakEvidenceOnly = config.autoConfirmRequiresStrongEvidence &&
            input.evidenceLabel == io.apptolast.paparcar.domain.detection.ArmEvidence.LABEL_VERIFIED_ENTER &&
            !sessionSawDriving

        val pathLabel = if (hasStepsProof) "steps+egress" else "vehicleExit+window+egress"
        return when {
            confirmNow && weakEvidenceOnly -> ParkingDecision.Prompt(pathLabel)
            confirmNow -> ParkingDecision.Confirmed(
                pathLabel = pathLabel,
                reliability = config.reliabilityVehicleExit,
            )
            windowElapsed -> ParkingDecision.Rejected
            else -> ParkingDecision.Inconclusive
        }
    }

    private companion object {
        const val KMH_PER_MPS = 3.6f
    }
}

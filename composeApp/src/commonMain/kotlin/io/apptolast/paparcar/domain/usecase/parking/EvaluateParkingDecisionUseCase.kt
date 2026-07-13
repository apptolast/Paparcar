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
    /** [DET-KINEMATIC-EGRESS-001] The frozen end-of-drive anchor has watched the phone WALK away:
     *  ≥ `kinematicEgressMinWalkFixes` quality pedestrian-band fixes since the freeze. GPS-measured
     *  egress for mute-step-counter hardware — the same inference the freeze already trusts to
     *  protect the anchor ("this movement is the person, not the car"), now allowed to confirm.
     *  Only counts when the session itself measured driving (a seeded arm whose stream never saw
     *  the trip must keep asking). */
    val hasKinematicEgress: Boolean = false,
    /** [DET-STEP-SPEED-GATE-001] Speed (m/s) of the most recent GPS fix. The evaluator only ever
     *  saw `maxSpeedKmh` (session PEAK), so it could confirm steps+egress while the car was still
     *  ROLLING — the in-motion false positive at Avenida de los Mástiles (field 2026-07-12). No
     *  path may auto-confirm while this is above the pedestrian ceiling (`egressStepMaxSpeedMps`). */
    val lastSpeedMps: Float = 0f,
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

        // [DET-SOLID-001] The session's own stream witnessed real driving speed. Gates both the
        // weak-evidence policy below and the kinematic egress proof: a seeded arm whose stream
        // never measured the trip has no business confirming silently by ANY path.
        val sessionSawDriving = input.maxSpeedKmh >= config.minimumTripSpeedMps * KMH_PER_MPS

        // [DET-KINEMATIC-EGRESS-001] GPS-measured walk away from the frozen end-of-drive anchor.
        // Steps outrank it (they fire earlier); this is the mute-counter peer. Requires measured
        // in-session driving — the freeze alone can mature on a seeded post-trip session whose
        // anchor is wherever the user's body stood.
        val hasKinematicProof = input.hasKinematicEgress && input.hasEgressDisplacement && sessionSawDriving

        // Scooter mismatch guard: a CAR profile on a sustained slow trip looks like a moped —
        // suppress auto-confirm and leave it to the user prompt. [BUG-SCOOTER-001]
        val isMismatch = input.vehicleType == VehicleType.CAR &&
            input.sessionDurationMs >= config.mismatchMinSessionDurationMs &&
            input.maxSpeedKmh <= config.mismatchMaxSpeedKmh

        // [DET-STEP-SPEED-GATE-001] The car is still ROLLING at the moment of decision (last fix
        // above the pedestrian ceiling). steps+egress is blind to instantaneous speed, so a phone
        // bouncing in stop-and-go traffic faked a confirm mid-route (FP Avenida de los Mástiles,
        // field 2026-07-12). A genuine park confirms while stationary or walking away — never
        // while rolling. Vetoes EVERY auto-confirm path; a walking user (< ceiling) is unaffected.
        val isRolling = input.lastSpeedMps > config.egressStepMaxSpeedMps

        val confirmNow = when {
            isRolling -> false
            isMismatch -> false
            // [DET-C-01] Egress is mandatory for every path.
            !input.hasEgressDisplacement -> false
            hasStepsProof -> true
            hasKinematicProof -> true
            windowElapsed && input.hadVehicleExit -> true
            else -> false
        }

        // [DET-SOLID-001] Weak-evidence policy: the arm's only vehicle proof is an AR ENTER
        // (falsifiable by bus/taxi) AND the session's own stream never witnessed driving speed —
        // all confirm conditions hold, but the save is not trustworthy enough to be silent.
        // `verified_late` (the departure worker's post-arm upgrade) is weak for the same reason:
        // its verdict can rest on the same ENTER fall-through, and it must never override a
        // prompt the policy already chose (field incident 2026-07-04: the late upgrade silently
        // saved a park the user had been ASKED about and never answered). A session that
        // witnessed real driving confirms silently regardless.
        val weakLabels = setOf(
            io.apptolast.paparcar.domain.detection.ArmEvidence.LABEL_VERIFIED_ENTER,
            io.apptolast.paparcar.domain.detection.ArmEvidence.LABEL_VERIFIED_LATE,
        )
        val weakEvidenceOnly = config.autoConfirmRequiresStrongEvidence &&
            input.evidenceLabel in weakLabels &&
            !sessionSawDriving

        // [DET-SOLID-001][C2] Human-powered profiles never auto-confirm: a bike/scooter crossing
        // 18 km/h once (downhill, sprint) makes the whole session look like a car to every
        // speed-based signal, and the mismatch guard (CAR-only, ≥8 min) cannot help. Always ask.
        // MOTORCYCLE is a real motor vehicle with its own geofence — keeps auto-confirm.
        val humanPowered = input.vehicleType == VehicleType.SCOOTER || input.vehicleType == VehicleType.BIKE

        val pathLabel = when {
            hasStepsProof -> "steps+egress"
            hasKinematicProof -> "kinematic+egress"
            else -> "vehicleExit+window+egress"
        }
        return when {
            confirmNow && (weakEvidenceOnly || humanPowered) -> ParkingDecision.Prompt(pathLabel)
            confirmNow -> ParkingDecision.Confirmed(
                pathLabel = pathLabel,
                reliability = if (pathLabel == "kinematic+egress") {
                    config.reliabilityKinematicEgress
                } else {
                    config.reliabilityVehicleExit
                },
            )
            // [DET-STEP-SPEED-GATE-001] Proofs present but the car is still rolling → this is a
            // traffic false positive (FP Avenida de los Mástiles), not a park. Reject the candidate
            // decisively rather than leave it open to re-confirm on the next moving fix.
            isRolling && (hasStepsProof || hasKinematicProof) -> ParkingDecision.Rejected
            windowElapsed -> ParkingDecision.Rejected
            else -> ParkingDecision.Inconclusive
        }
    }

    private companion object {
        const val KMH_PER_MPS = 3.6f
    }
}

package io.apptolast.paparcar.domain.detection.stages

import io.apptolast.paparcar.domain.detection.physics.SavedParkingShape
import io.apptolast.paparcar.domain.detection.state.DetectionSessionState
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.usecase.parking.EvaluateParkingDecisionUseCase
import io.apptolast.paparcar.domain.usecase.parking.ParkingDecision
import io.apptolast.paparcar.domain.usecase.parking.ParkingDecisionInput

/**
 * [DET-D-03] **Steps + egress: parked and walked away, with nothing left to wait for.**
 *
 * The user drove, stopped, took enough steps AND walked far enough from the parked car. That is
 * unambiguous on its own, so this lane skips the observation window entirely — `elapsedSinceHighMs`
 * is 0 and the egress proofs are what confirm.
 *
 * The AR `IN_VEHICLE_EXIT` used to be required here and is now a non-decisive hint: a field trace
 * (2026-06-26) showed the confirm waiting ~16 s for an EXIT while steps+egress were already
 * satisfied, and the requirement made detection fragile on hardware where EXIT is late or never
 * fires. The egress gate was always the decisive signal. [supersedes BUG-OPPO-LATE-CONFIRM]
 *
 * [DET-KINEMATIC-EGRESS-001] The kinematic signal is the mute-counter peer of the step proof: a
 * FROZEN anchor that has watched a sustained quality walk away from it. Same evidence, measured by
 * GPS instead of by the step sensor — which is the only way a device whose counter never reports
 * gets a fast confirm at all.
 *
 * [DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001] The gate reads the FRESH count: a discarded
 * candidate's steps stay on the record for every other reader, but they may not re-arm this lane.
 *
 * @param refinedParkLocation Where the pin actually goes — the anchor, possibly sharpened by the
 *   egress birth. Presented as a function for the same reason as [decisionInput]: its home is the
 *   anchor's territory and dragging it here would be deciding for stages that do not exist yet.
 */
class FastConfirmStage(
    private val evaluateParkingDecision: EvaluateParkingDecisionUseCase,
    private val decisionInput: (
        state: DetectionSessionState,
        location: GpsPoint,
        now: Long,
        elapsedSinceHighMs: Long,
        hadVehicleExit: Boolean,
        restCertified: Boolean,
    ) -> ParkingDecisionInput,
    private val refinedParkLocation: (DetectionSessionState, GpsPoint) -> GpsPoint,
) : SessionStage {

    override val stage = DetectionStage.FAST_CONFIRM

    override fun evaluate(
        state: DetectionSessionState,
        fix: GpsPoint,
        now: Long,
        stoppedDurationMs: Long,
        config: ParkingDetectionConfig,
    ): StageVerdict {
        if (!hasEgressProof(state, config)) return StageVerdict.Skip()

        val decision = evaluateParkingDecision(
            decisionInput(
                state, fix, now,
                /* elapsedSinceHighMs = */ 0L,
                state.egress.vehicleExitHint,
                // No stop has matured here — this lane runs on the egress proofs alone, so it may
                // not reach the terminal human-powered close: a cyclist paused at a light must not
                // be closed mid-ride. [DET-HUMAN-POWERED-EARLY-CLOSE-001]
                /* restCertified = */ false,
            ),
        )

        return when (decision) {
            is ParkingDecision.Confirmed -> StageVerdict.Handled(
                newState = state,
                effects = listOf(
                    DetectionEffect.Confirm(
                        shape = SavedParkingShape.ExactPin(
                            location = refinedParkLocation(state, fix),
                            reliability = decision.reliability,
                        ),
                        vehicleId = state.session.attributedVehicleId,
                        pathLabel = decision.pathLabel,
                    // [DET-C-02] Inferred: the grace window may still rule out an errand stop.
                    mayHold = true,
                    ),
                ),
                stopsIteration = true,
                notes = listOf(
                    "  ▶ ${decision.pathLabel} (steps=${state.egress.stepCount} " +
                        "kinematicFixes=${state.anchorTrust.kinematicEgressFixes}) → fast confirm, " +
                        "skipping slow path [DET-D-03][DET-KINEMATIC-EGRESS-001]",
                ),
            )

            is ParkingDecision.Prompt -> StageVerdict.Handled(
                newState = state,
                effects = listOf(
                    DetectionEffect.DegradeToPrompt(decision.pathLabel, decision.reason.key, fix),
                ),
                stopsIteration = true,
            )

            // Gated: fall through to the scoring lane, which is the next and last stage.
            else -> StageVerdict.Skip(
                listOf(
                    "  ⊘ steps+egress fast confirm gated ($decision) — " +
                        "anchorSet=${state.anchorTrust.anchor != null}, falling to scoring",
                ),
            )
        }
    }

    /**
     * Either witness of the egress walk opens this lane: counted steps, or the GPS-measured walk
     * away from a frozen anchor.
     *
     * A PREDICATE, not a verdict — it produces no `detectionPath`, no outcome and nothing the user
     * reads — so it lives inside the verdict it feeds rather than as a use case of its own
     * [DET-VERDICT-NOT-PREDICATE-001]. It moves up to shared ground the day a second stage needs it.
     */
    private fun hasEgressProof(state: DetectionSessionState, config: ParkingDetectionConfig): Boolean =
        state.freshStepCount >= config.minStepsToConfirm || hasKinematicEgress(state, config)

    /** [DET-KINEMATIC-EGRESS-001] The frozen anchor has watched a sustained quality walk away. */
    private fun hasKinematicEgress(state: DetectionSessionState, config: ParkingDetectionConfig): Boolean =
        state.anchorTrust.frozenByRest &&
            state.anchorTrust.anchor != null &&
            state.anchorTrust.kinematicEgressFixes >= config.kinematicEgressMinWalkFixes
}

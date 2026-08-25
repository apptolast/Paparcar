package io.apptolast.paparcar.domain.detection.stages

import io.apptolast.paparcar.domain.detection.state.ConfirmationPhase
import io.apptolast.paparcar.domain.detection.physics.SavedParkingShape
import io.apptolast.paparcar.domain.detection.state.DetectionSessionState
import io.apptolast.paparcar.domain.detection.state.hasEgressDisplacement
import io.apptolast.paparcar.domain.detection.state.refinedParkLocation
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.usecase.parking.EvaluateParkingDecisionUseCase
import io.apptolast.paparcar.domain.usecase.parking.ParkingDecision

/**
 * [DET-D-02] **The open candidate's own verdict**, judged on the real elapsed observation window.
 *
 * Once HIGH confidence has opened a candidate, this stage owns every fix until the window resolves —
 * which is why it ends the pass on ALL of its branches, including the inconclusive one. A candidate
 * in flight is not a state anything below it may re-decide.
 *
 * The decision itself is the same pure evaluator the fast lane uses; what differs is the elapsed
 * time it is judged with, and `restCertified = true` — in the candidate phase by construction, since
 * High confidence only ever arrives after the sustained-stop tier.
 *
 * ## Why the discard is an effect and not a new state
 *
 * The expiry does two things: fall back to the prompt still on screen (preserving `shownAt`, so the
 * response timeout keeps applying and the user can still tap it), and move the step freshness line.
 * The first is idempotent, the second is not — `candidateDiscarded()` stamps the line at wherever
 * the count stands when it runs, and a step counted between the snapshot and here belongs on the
 * near side of that line. So the stage ASKS for it and the executor applies it to the live state.
 * [DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001]
 */
class CandidateStage(
    private val evaluateParkingDecision: EvaluateParkingDecisionUseCase,
) : SessionStage {

    override val stage = DetectionStage.CANDIDATE

    override fun evaluate(
        state: DetectionSessionState,
        fix: GpsPoint,
        now: Long,
        stoppedDurationMs: Long,
        config: ParkingDetectionConfig,
    ): StageVerdict {
        val phase = state.confirmation.phase as? ConfirmationPhase.Candidate ?: return StageVerdict.Skip()

        val notes = mutableListOf<DiagnosticNote>()
        // [DET-A] Steps prove egress only when paired with displacement from the park anchor.
        if (state.egress.stepCount >= config.minStepsToConfirm && !state.hasEgressDisplacement(fix, config)) {
            notes += "  ⊘ CANDIDATE steps proof gated by EGRESS — " +
                "anchorSet=${state.anchorTrust.anchor != null}, " +
                "need ≥${config.minEgressDisplacementMeters}m walked from park anchor [DET-A]"
        }

        val elapsed = now - phase.highReachedAt
        val decision = evaluateParkingDecision(
            state.parkingDecisionInput(fix, now, elapsed, phase.hadVehicleExit, true, config),
        )
        notes += "  ⏳ CANDIDATE phase — elapsed=${elapsed}ms " +
            "steps=${state.egress.stepCount}/${config.minStepsToConfirm} → decision=$decision"

        // Every branch ends the pass: an open candidate is not re-decided by anything below it.
        return when (decision) {
            is ParkingDecision.Confirmed -> {
                // The refinement's own line used to be logged from inside the helper, which put it
                // AHEAD of everything this branch says. It is prepended for exactly that reason:
                // a note channel that reorders `parkdiag` is a behaviour change wearing a refactor.
                val pin = state.refinedParkLocation(fix, config)
                handled(
                    state,
                    listOfNotNull(pin.note) + notes + DiagnosticNote(
                        "  ▶ CANDIDATE confirmed via ${decision.pathLabel} — " +
                            "entering confirmParking(reliability=${decision.reliability})",
                    ),
                    DetectionEffect.Confirm(
                        shape = SavedParkingShape.ExactPin(pin.location, decision.reliability),
                        vehicleId = state.session.attributedVehicleId,
                        pathLabel = decision.pathLabel,
                        // [DET-C-02] Inferred: the grace window may still rule out an errand stop.
                        mayHold = true,
                    ),
                )
            }

            ParkingDecision.Rejected -> handled(
                state,
                notes + DiagnosticNote(
                    "  ⊘ CANDIDATE expired without egress proof — discarding, steps " +
                        "${state.egress.stepCount} kept but no longer fresh " +
                        "[BUG-GARAGE-COLA-001][DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001]",
                ),
                DetectionEffect.DiscardCandidate(phase.shownAt, fix),
            )

            is ParkingDecision.Prompt -> handled(
                state, notes,
                DetectionEffect.DegradeToPrompt(decision.pathLabel, decision.reason.key, fix),
            )

            // [DET-HUMAN-POWERED-EARLY-CLOSE-001] Terminal: nothing this candidate — or any next one
            // on the same stop — could produce is a car park. Reached when the human-powered evidence
            // lands AFTER the candidate opened: an AR `ON_BICYCLE` ENTER is delivered up to ~2 min
            // late, so the stop can mature before the ride is known to have been muscle-powered.
            ParkingDecision.CloseHumanPowered -> handled(
                state,
                notes + DiagnosticNote(
                    "  ⊘ human-powered ride at a matured stop — closing NOW instead of idling to " +
                        "the response timeout [DET-HUMAN-POWERED-EARLY-CLOSE-001]",
                ),
                DetectionEffect.CloseHumanPowered(state.session.attributedVehicleId, fix),
            )

            ParkingDecision.Inconclusive -> handled(state, notes)
        }
    }

    private fun handled(
        state: DetectionSessionState,
        notes: List<DiagnosticNote>,
        vararg effects: DetectionEffect,
    ) = StageVerdict.Handled(
        newState = state,
        effects = effects.toList(),
        stopsIteration = true,
        notes = notes,
    )
}

package io.apptolast.paparcar.domain.detection.stages

import io.apptolast.paparcar.domain.coordinator.ConfirmationPhase
import io.apptolast.paparcar.domain.detection.state.DetectionSessionState
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.ParkingSignals
import io.apptolast.paparcar.domain.usecase.parking.CalculateParkingConfidenceUseCase
import io.apptolast.paparcar.domain.usecase.parking.EvaluateParkingDecisionUseCase
import io.apptolast.paparcar.domain.usecase.parking.ParkingDecision
import io.apptolast.paparcar.domain.usecase.parking.ParkingDecisionInput
import io.apptolast.paparcar.domain.usecase.parking.UnattendedSaveReason

/**
 * [09 §4] The LAST stage of the precedence, and therefore **the first one moved** — everything above
 * it keeps seeing exactly what it expects while this one changes house.
 *
 * It scores the stop and advances the confirmation phase. It **never confirms**: HIGH confidence on
 * its own has never been allowed to plant a pin, which is the governing doctrine spelled out one
 * branch at a time — the event nominates, only measured movement confirms.
 *
 * The one thing it can do besides advancing is END the session, and only for one reason:
 * [DET-HUMAN-POWERED-EARLY-CLOSE-001] a muscle-powered ride at a matured rest is a complete verdict,
 * so there is nothing to wait for.
 *
 * ## Why the rest question is asked BEFORE the tier dispatch
 *
 * It used to live inside the HIGH branch, on the premise that "High IS the certified sustained stop,
 * its only route is the 5-minute tier". That is true of the scorer's SLOW path — and the fast path
 * pre-empts it: with an AR vehicle exit and 30 s stopped the scorer returns Medium and never looks
 * at the tiers, so High becomes unreachable for the rest of the session. A human-powered ride also
 * has its Low/Medium prompt SUPPRESSED, so such a session had no prompt (no response timeout), no
 * High (no close) and no candidate: **nothing could ever end it**. Field 2026-08-20: 102 minutes of
 * foreground service and 967 fixes, and the only exit it ever found was an unrelated walk clearing
 * the egress floor 79 minutes later. [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §B]
 *
 * The rest is certified by the same number the tier used — no new clock, read directly.
 *
 * @param decisionInput The `DetectionSessionState → ParkingDecisionInput` adapter, presented as a
 *   function. Its home is here in `stages/` [09 §C.4] because three stages share it, but it moves
 *   when the second of those three lands — dragging it now would mean deciding for stages that do
 *   not exist yet.
 * @param humanPowered Whether this ride was muscle-powered, presented for the same reason.
 */
class ConfidenceScoringStage(
    private val calculateParkingConfidence: CalculateParkingConfidenceUseCase,
    private val evaluateParkingDecision: EvaluateParkingDecisionUseCase,
    private val decisionInput: (
        state: DetectionSessionState,
        location: GpsPoint,
        now: Long,
        elapsedSinceHighMs: Long,
        hadVehicleExit: Boolean,
        restCertified: Boolean,
    ) -> ParkingDecisionInput,
    private val humanPowered: (DetectionSessionState, Long) -> Boolean,
) : SessionStage {

    override val stage = DetectionStage.CONFIDENCE_SCORING

    override fun evaluate(
        state: DetectionSessionState,
        fix: GpsPoint,
        now: Long,
        stoppedDurationMs: Long,
        config: ParkingDetectionConfig,
    ): StageVerdict {
        closingVerdict(state, fix, now, stoppedDurationMs, config)?.let { return it }

        val confidence = calculateParkingConfidence(
            ParkingSignals(
                speed = fix.speed,
                stoppedDurationMs = stoppedDurationMs,
                gpsAccuracy = fix.accuracy,
                activityExit = state.egress.vehicleExitHint,
            ),
        )

        val scored = "  ⚖ scoring=$confidence (signals: speed=${fix.speed} " +
            "stopped=${stoppedDurationMs}ms accuracy=${fix.accuracy} exit=${state.egress.vehicleExitHint})"

        return when (confidence) {
            is ParkingConfidence.NotYet -> StageVerdict.Skip(listOf(scored))
            is ParkingConfidence.Low, is ParkingConfidence.Medium ->
                advanceLowMedium(state, confidence, now, config).withNoteFirst(scored)
            is ParkingConfidence.High -> advanceHigh(state, confidence, now).withNoteFirst(scored)
        }
    }

    /**
     * The matured-rest question, asked off the measured stop clock before any tier dispatch so every
     * route to a matured rest passes through it exactly once.
     */
    private fun closingVerdict(
        state: DetectionSessionState,
        fix: GpsPoint,
        now: Long,
        stoppedDurationMs: Long,
        config: ParkingDetectionConfig,
    ): StageVerdict? {
        if (stoppedDurationMs < config.slowPath5MinMs) return null
        val verdict = evaluateParkingDecision(
            decisionInput(state, fix, now, 0L, state.egress.vehicleExitHint, true),
        )
        if (verdict != ParkingDecision.CloseHumanPowered) return null
        return StageVerdict.Handled(
            newState = state,
            notes = listOf(
                "  ⊘ human-powered ride at a matured stop — closing NOW instead of idling to the " +
                    "response timeout [DET-HUMAN-POWERED-EARLY-CLOSE-001]",
            ),
            effects = listOf(
                DetectionEffect.AskUser(
                    reasonKey = UnattendedSaveReason.HUMAN_POWERED.key,
                    vehicleId = state.session.attributedVehicleId,
                    at = fix,
                ),
            ),
            stopsIteration = true,
        )
    }

    /** [REFACTOR-200] Phase advancement via explicit transitions. */
    private fun advanceLowMedium(
        state: DetectionSessionState,
        confidence: ParkingConfidence,
        now: Long,
        config: ParkingDetectionConfig,
    ): StageVerdict = when (val phase = state.confirmation.phase) {
        is ConfirmationPhase.Idle -> StageVerdict.Handled(
            newState = state.copy(confirmation = state.confirmation.lowReached(now)),
            notes = listOf("  → phase: Idle → LowReached(firstReachedAt=$now) [BUG-DETECT-310502]"),
        )

        is ConfirmationPhase.LowReached -> {
            val hasExit = state.egress.vehicleExitHint
            val timeoutReached = (now - phase.firstReachedAt) >= config.lowNotifTimeoutMs
            when {
                // [DET-HUMAN-POWERED-EARLY-CLOSE-001] Suppressed, not deferred: the phase stays
                // LowReached (no `shownAt` claiming a prompt nobody saw), so if the veto lifts — an
                // AR `IN_VEHICLE` ENTER superseding the bicycle stamp — the very next fix shows the
                // prompt normally, its timeout still measured from `firstReachedAt`.
                humanPowered(state, now) -> StageVerdict.Skip(
                    listOf(
                        "  ⊘ Low/Medium notif suppressed — human-powered ride, the matured stop " +
                            "will close the session instead [DET-HUMAN-POWERED-EARLY-CLOSE-001]",
                    ),
                )
                hasExit || timeoutReached -> StageVerdict.Handled(
                    newState = state.copy(confirmation = state.confirmation.notified(now)),
                    effects = listOf(
                        DetectionEffect.NotifyPrompt(confidence),
                        DetectionEffect.RecordPromptShown(
                            pathLabel = "low_medium(" +
                                (if (hasExit) "exit=true" else "timeout=${now - phase.firstReachedAt}ms") +
                                ")",
                            confidence = confidence,
                        ),
                    ),
                    notes = listOf(
                        "  → showing parking-confirmation notif (Low/Medium, " +
                            (if (hasExit) "exit=true" else "timeout=${now - phase.firstReachedAt}ms") + ")",
                    ),
                )
                else -> StageVerdict.Skip(
                    listOf(
                        "  ⊘ Low/Medium notif suppressed — no vehicleExit, timeout in " +
                            "~${config.lowNotifTimeoutMs - (now - phase.firstReachedAt)}ms",
                    ),
                )
            }
        }

        // Already prompted; nothing to do on a Low/Medium re-evaluation.
        is ConfirmationPhase.Notified, is ConfirmationPhase.Candidate -> StageVerdict.Skip()
    }

    private fun advanceHigh(
        state: DetectionSessionState,
        confidence: ParkingConfidence,
        now: Long,
    ): StageVerdict = when (val phase = state.confirmation.phase) {
        // Prompt was never shown — fire it as part of this transition.
        is ConfirmationPhase.Idle, is ConfirmationPhase.LowReached -> StageVerdict.Handled(
            newState = state.copy(
                confirmation = state.confirmation.candidate(now, state.egress.vehicleExitHint, now),
            ),
            // The order is today's, and it is not the obvious one: the notification fires FIRST,
            // then the candidate marker, then the prompt marker.
            effects = listOf(
                DetectionEffect.NotifyPrompt(confidence),
                DetectionEffect.RecordCandidateOpened("from ${phase::class.simpleName}"),
                DetectionEffect.RecordPromptShown("high_candidate", confidence),
            ),
            notes = listOf(
                "  ▶ HIGH reached — entering CANDIDATE phase + showing notif, " +
                    "vehicleExit=${state.egress.vehicleExitHint}",
            ),
        )

        // Prompt already shown — preserve its instant so the response timeout keeps ticking from
        // when the user first saw a question. [BUG-STUCK-SESSION]
        is ConfirmationPhase.Notified -> StageVerdict.Handled(
            newState = state.copy(
                confirmation = state.confirmation.candidate(now, state.egress.vehicleExitHint, phase.shownAt),
            ),
            effects = listOf(DetectionEffect.RecordCandidateOpened("from Notified")),
            notes = listOf(
                "  ▶ HIGH reached after Notified(shownAt=${phase.shownAt}) — entering CANDIDATE " +
                    "phase (suppressing duplicate notif) [BUG-STUCK-SESSION]",
            ),
        )

        // Already in CANDIDATE — keep the original highReachedAt and shownAt so the observation
        // window does not reset on every subsequent High fix.
        is ConfirmationPhase.Candidate -> StageVerdict.Skip()
    }

    /** The scoring line is logged before the phase machine runs, so it goes in front. */
    private fun StageVerdict.withNoteFirst(note: String): StageVerdict = when (this) {
        is StageVerdict.Skip -> copy(notes = listOf(note) + notes)
        is StageVerdict.Handled -> copy(notes = listOf(note) + notes)
    }
}

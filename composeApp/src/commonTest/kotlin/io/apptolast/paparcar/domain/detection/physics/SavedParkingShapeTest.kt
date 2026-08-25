package io.apptolast.paparcar.domain.detection.physics

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.usecase.parking.HonestCloseDecision
import io.apptolast.paparcar.domain.usecase.parking.ParkingDecision
import io.apptolast.paparcar.domain.usecase.parking.PromptReason
import io.apptolast.paparcar.domain.usecase.parking.UnattendedParkingSave
import io.apptolast.paparcar.domain.usecase.parking.UnattendedSaveReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [07 §3.4.1] The census that makes [SavedParkingShape] worth introducing before anybody returns it.
 *
 * **Why a test and not production code.** Nothing adopts the type in this step — making the
 * verdicts return it changes their signatures and their tests, which belongs to the verdict phase.
 * But a type with no users is a fourth vocabulary waiting to happen, so the classification lives
 * here instead: every arm of the three existing sealeds is mapped through an EXHAUSTIVE `when`, and
 * Kotlin refuses to compile this file the moment somebody adds an arm to any of them. The
 * divergence cannot grow while the adoption waits.
 *
 * The mapping functions below also record what the CALLER supplies today, which is the part a
 * signature never shows: `SaveExact` has neither a point nor a reliability, `ApproximatePin` has no
 * reliability, `Confirmed` has no point. Every extra parameter here is a piece of context the
 * verdict reasoned about and then dropped on the floor for the caller to guess again.
 */
class SavedParkingShapeTest {

    private val anchor = GpsPoint(36.7, -4.4, accuracy = 8f, timestamp = 1_000L, speed = 0f)

    // ── The three vocabularies, classified ────────────────────────────────────

    /**
     * `EvaluateUnattendedParkingSaveUseCase`. Note `radiusFor`: this ladder emits raw
     * `doubtMeters` and the coordinator clamps it into a radius afterwards, which is exactly the
     * responsibility split [SavedParkingShape.BoundedZone] closes by carrying the final radius.
     */
    private fun shapeOf(
        decision: UnattendedParkingSave,
        anchor: GpsPoint,
        reliability: Float,
        radiusFor: (GpsPoint, Double) -> Float,
    ): SavedParkingShape = when (decision) {
        UnattendedParkingSave.SaveExact -> SavedParkingShape.ExactPin(anchor, reliability)
        is UnattendedParkingSave.SaveZone ->
            SavedParkingShape.BoundedZone(decision.center, radiusFor(decision.center, decision.doubtMeters))
        is UnattendedParkingSave.Ask -> SavedParkingShape.AskUser
    }

    /** `EvaluateHonestCloseUseCase`. This one already carries its radius; only the trust is implicit. */
    private fun shapeOf(decision: HonestCloseDecision, approximateReliability: Float): SavedParkingShape =
        when (decision) {
            is HonestCloseDecision.ApproximatePin ->
                SavedParkingShape.ExactPin(decision.location, approximateReliability)
            is HonestCloseDecision.ApproximateZone ->
                SavedParkingShape.BoundedZone(decision.center, decision.radiusMeters)
            HonestCloseDecision.KeepSilent -> SavedParkingShape.KeepSilent
        }

    /**
     * `EvaluateParkingDecisionUseCase` — the one that is NOT purely a shape, and the reason this
     * census exists. Three of its five arms answer a LIFECYCLE question, not an artifact question,
     * and `null` is the honest answer for them.
     */
    private fun shapeOf(decision: ParkingDecision, anchor: GpsPoint): SavedParkingShape? =
        when (decision) {
            is ParkingDecision.Confirmed -> SavedParkingShape.ExactPin(anchor, decision.reliability)
            is ParkingDecision.Prompt -> SavedParkingShape.AskUser
            // [DET-HUMAN-POWERED-EARLY-CLOSE-001] Terminal AND it nudges, so it is a shape today —
            // `closeHumanPoweredRide` fires `UNATTENDED_HUMAN_POWERED_NUDGE`. Its own KDoc says it
            // SHOULD become KeepSilent once DET-HANDOFF-NOT-MANUAL-001 §B stops committing a
            // departure without proof. Written down here so that change is a one-line diff with a
            // failing assert, not an archaeology exercise.
            ParkingDecision.CloseHumanPowered -> SavedParkingShape.AskUser
            ParkingDecision.Rejected -> null
            ParkingDecision.Inconclusive -> null
        }

    // ── Unattended timeout ────────────────────────────────────────────────────

    @Test
    fun should_map_the_unattended_ladder_onto_three_shapes() {
        val trusted = shapeOf(UnattendedParkingSave.SaveExact, anchor, reliability = 0.6f) { _, _ -> 0f }
        assertEquals(SavedParkingShape.ExactPin(anchor, 0.6f), trusted)

        val zone = shapeOf(
            UnattendedParkingSave.SaveZone(UnattendedSaveReason.GAP_ANCHOR, anchor, doubtMeters = 42.0),
            anchor,
            reliability = 0.6f,
        ) { _, doubt -> doubt.toFloat() }
        assertEquals(SavedParkingShape.BoundedZone(anchor, 42f), zone)

        val ask = shapeOf(
            UnattendedParkingSave.Ask(UnattendedSaveReason.NO_DRIVE),
            anchor,
            reliability = 0.6f,
        ) { _, _ -> 0f }
        assertEquals(SavedParkingShape.AskUser, ask)
    }

    /**
     * The reason never travels in the shape: two asks with different causes are the same shape, and
     * the cause stays in [UnattendedSaveReason] where the trace contract can keep quoting it.
     */
    @Test
    fun should_give_two_different_reasons_the_same_shape() {
        val noDrive = UnattendedParkingSave.Ask(UnattendedSaveReason.NO_DRIVE)
        val humanPowered = UnattendedParkingSave.Ask(UnattendedSaveReason.HUMAN_POWERED, distanceMeters = 310.0)
        assertEquals(
            shapeOf(noDrive, anchor, 0.6f) { _, _ -> 0f },
            shapeOf(humanPowered, anchor, 0.6f) { _, _ -> 0f },
        )
    }

    // ── Honest close ──────────────────────────────────────────────────────────

    @Test
    fun should_map_the_honest_close_ladder_onto_three_shapes() {
        assertEquals(
            SavedParkingShape.ExactPin(anchor, 0.3f),
            shapeOf(HonestCloseDecision.ApproximatePin(anchor), approximateReliability = 0.3f),
        )
        assertEquals(
            SavedParkingShape.BoundedZone(anchor, 75f),
            shapeOf(HonestCloseDecision.ApproximateZone(anchor, 75f), approximateReliability = 0.3f),
        )
        assertEquals(
            SavedParkingShape.KeepSilent,
            shapeOf(HonestCloseDecision.KeepSilent, approximateReliability = 0.3f),
        )
    }

    /**
     * The two ladders agree on the shape and disagree on nothing else: an approximate pin from the
     * abort and a trusted pin from the timeout are both [SavedParkingShape.ExactPin], separated by
     * the reliability they claim — which is precisely why the arm carries it.
     */
    @Test
    fun should_separate_the_two_pin_paths_by_reliability_and_not_by_type() {
        val fromTimeout = shapeOf(UnattendedParkingSave.SaveExact, anchor, reliability = 0.6f) { _, _ -> 0f }
        val fromAbort = shapeOf(HonestCloseDecision.ApproximatePin(anchor), approximateReliability = 0.3f)
        assertEquals(SavedParkingShape.ExactPin::class, fromTimeout::class)
        assertEquals(SavedParkingShape.ExactPin::class, fromAbort::class)
        assertEquals(0.6f, (fromTimeout as SavedParkingShape.ExactPin).reliability)
        assertEquals(0.3f, (fromAbort as SavedParkingShape.ExactPin).reliability)
    }

    // ── Candidate decision: where the line is drawn ───────────────────────────

    @Test
    fun should_map_only_the_terminal_arms_of_the_candidate_decision() {
        assertEquals(
            SavedParkingShape.ExactPin(anchor, 0.9f),
            shapeOf(ParkingDecision.Confirmed("steps_egress", 0.9f), anchor),
        )
        assertEquals(
            SavedParkingShape.AskUser,
            shapeOf(ParkingDecision.Prompt("ar_enter", PromptReason.WEAK_EVIDENCE), anchor),
        )
        assertEquals(
            SavedParkingShape.AskUser,
            shapeOf(ParkingDecision.CloseHumanPowered, anchor),
        )
    }

    /**
     * **The exclusion this file exists for.** `Rejected` and `Inconclusive` leave no artifact, and a
     * careless reading makes them [SavedParkingShape.KeepSilent]. They are not: silence is
     * *terminal, nothing saved*, while `Inconclusive` means *not yet, ask me on the next fix* and
     * `Rejected` discards ONE candidate while the stop stays alive. Flattening them into KeepSilent
     * would end sessions that are still working — a whole class of false negatives bought for a
     * tidier `when`.
     */
    @Test
    fun should_refuse_to_call_a_live_session_silent() {
        assertNull(shapeOf(ParkingDecision.Rejected, anchor))
        assertNull(shapeOf(ParkingDecision.Inconclusive, anchor))
    }
}

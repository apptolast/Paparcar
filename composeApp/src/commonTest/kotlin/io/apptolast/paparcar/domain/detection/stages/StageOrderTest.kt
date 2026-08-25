package io.apptolast.paparcar.domain.detection.stages

import io.apptolast.paparcar.domain.detection.physics.SavedParkingShape
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingConfidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [09 §4] **The order is the behaviour, so the order gets a test.**
 *
 * The loop evaluates ten branches and the first that applies wins. Until now that order was the
 * physical position of blocks inside a 700-line method: the compiler did not care, no test failed
 * when it changed, and the KDoc describing it was wrong.
 *
 * The order is now stated twice — once as the enum's declaration order, once as
 * [detectionStageOrder] — precisely so that permuting either one alone fails here. That is the
 * acceptance criterion the plan sets for P3.0.
 *
 * The order itself is not invented: it is the MEASURED order, established by
 * `StagePrecedenceCharacterizationTest` (P0.1), whose four discriminating tests each pin an adjacent
 * pair and were each verified to fail when that pair is permuted. This file names which test pins
 * which pair, so a future permutation lands you on the evidence instead of on an opinion.
 */
class StageOrderTest {

    /** The literal list, spelled out. Permuting [detectionStageOrder] fails here. */
    @Test
    fun should_run_the_stages_in_the_measured_order() {
        assertEquals(
            listOf(
                DetectionStage.HOLD_RESOLUTION,
                DetectionStage.FALSE_ENTER_ABORT,
                DetectionStage.NO_MOVEMENT_BUDGET,
                DetectionStage.VEHICLE_ATTRIBUTION,
                DetectionStage.USER_CONFIRM,
                DetectionStage.PRE_DRIVE_SKIP,
                DetectionStage.RESPONSE_TIMEOUT,
                DetectionStage.CANDIDATE,
                DetectionStage.FAST_CONFIRM,
                DetectionStage.CONFIDENCE_SCORING,
            ),
            detectionStageOrder,
        )
    }

    /**
     * The second statement of the same order. Permuting the ENUM alone — which the test above would
     * not notice, because it compares two lists of the same values — fails here.
     */
    @Test
    fun should_declare_the_same_order_in_the_enum_as_in_the_list() {
        assertEquals(DetectionStage.entries.toList(), detectionStageOrder)
    }

    /** No stage may be dropped from the order, and none may appear twice. */
    @Test
    fun should_place_every_stage_exactly_once() {
        assertEquals(DetectionStage.entries.size, detectionStageOrder.size)
        assertEquals(detectionStageOrder.toSet().size, detectionStageOrder.size)
    }

    /**
     * Every entry states WHY it outranks the one below it. A stage added without that sentence is a
     * stage inserted into a precedence nobody argued about — which is how the order became
     * undocumented and wrong in the first place.
     */
    @Test
    fun should_make_every_stage_say_why_it_outranks_the_next() {
        DetectionStage.entries.forEach { stage ->
            assertTrue(
                stage.outranksBecause.length > 20,
                "${stage.name} must state why it outranks the stage below it",
            )
        }
    }

    /**
     * The four pairs `StagePrecedenceCharacterizationTest` actually PROVES, each with a test
     * verified to fail when the pair is permuted. Recorded as adjacency claims so the day someone
     * reorders the list, the diff points at the test that refutes them.
     */
    @Test
    fun should_keep_the_four_pairs_that_have_a_discriminating_test_behind_them() {
        fun outranks(higher: DetectionStage, lower: DetectionStage) =
            detectionStageOrder.indexOf(higher) < detectionStageOrder.indexOf(lower)

        // should_plant_the_held_pin_not_the_answer_fix_when_the_user_says_yes_during_a_hold
        assertTrue(outranks(DetectionStage.HOLD_RESOLUTION, DetectionStage.USER_CONFIRM))
        // should_abort_the_false_enter_even_when_the_user_already_said_yes
        assertTrue(outranks(DetectionStage.FALSE_ENTER_ABORT, DetectionStage.USER_CONFIRM))
        // should_fold_the_no_movement_budget_even_when_the_user_already_said_yes
        assertTrue(outranks(DetectionStage.NO_MOVEMENT_BUDGET, DetectionStage.USER_CONFIRM))
        // should_resolve_the_vehicle_before_confirming_within_the_same_fix
        assertTrue(outranks(DetectionStage.VEHICLE_ATTRIBUTION, DetectionStage.FAST_CONFIRM))
    }
}

/**
 * The census that keeps [DetectionEffect] from being decoration while no stage emits one yet.
 *
 * Same technique as `SavedParkingShapeTest` in P1.10: nothing adopts the type in this step, so the
 * classification lives in a test, where an arm nobody has thought about shows up as a gap NOW rather
 * than as a surprise in P3.11.
 */
class StageScaffoldTest {

    private val here = GpsPoint(36.7, -4.4, accuracy = 8f, timestamp = 1_000L, speed = 0f)

    /**
     * Every I/O method the coordinator has today, mapped onto the effect that will replace it.
     *
     * Written as an exhaustive `when` over [DetectionEffect] so that adding an arm without saying
     * which of today's methods it stands for stops compiling.
     */
    private fun replacedMethod(effect: DetectionEffect): String = when (effect) {
        is DetectionEffect.Confirm -> "runConfirm / beginConfirm / saveUnattendedZone"
        is DetectionEffect.AskUser -> "nudgeUnattended"
        is DetectionEffect.NotifyPrompt -> "notifyParkingConfirmation"
        is DetectionEffect.DegradeToPrompt -> "degradeToPrompt (kept compound: it reads its own clock)"
        is DetectionEffect.DiscardCandidate -> "the Candidate(DISCARDED) branch of evaluateCandidatePhase"
        is DetectionEffect.SaveZone -> "saveUnattendedZone, with its fall-back-to-nudge"
        is DetectionEffect.SaveUnattended -> "the SaveExact branch of the response timeout"
        is DetectionEffect.CloseHumanPowered -> "closeHumanPoweredRide"
        is DetectionEffect.RecordPromptShown -> "the PROMPT_SHOWN Decision event"
        is DetectionEffect.RecordCandidateOpened -> "the Candidate(OPENED) event"
        DetectionEffect.DismissPrompt -> "notificationPort.dismiss"
        is DetectionEffect.ResolveVehicle -> "the vehicleRepository lookup inside the attribution branch"
        is DetectionEffect.EndSession -> "the completed = true / return@collect pairs"
    }

    @Test
    fun should_account_for_every_io_method_the_coordinator_performs_today() {
        val everyEffect = listOf(
            DetectionEffect.Confirm(SavedParkingShape.ExactPin(here, 0.9f), "veh-1", "steps+egress"),
            DetectionEffect.AskUser("no_drive", "veh-1", here),
            DetectionEffect.NotifyPrompt(ParkingConfidence.Low),
            DetectionEffect.DegradeToPrompt("ar_enter", "weak_evidence", here),
            DetectionEffect.DiscardCandidate(2_000L, here),
            DetectionEffect.SaveZone("gap_anchor", here, 42.0, "veh-1", here),
            DetectionEffect.SaveUnattended(SavedParkingShape.ExactPin(here, 0.4f), "veh-1"),
            DetectionEffect.CloseHumanPowered("veh-1", here),
            DetectionEffect.RecordPromptShown("high_candidate", ParkingConfidence.Low),
            DetectionEffect.RecordCandidateOpened("from Notified"),
            DetectionEffect.DismissPrompt,
            DetectionEffect.ResolveVehicle("veh-1"),
            DetectionEffect.EndSession("aborted_false_enter"),
        )
        everyEffect.forEach { assertTrue(replacedMethod(it).isNotEmpty(), "$it stands for nothing") }
    }

    /**
     * A stage decides; it does not act. `Handled` therefore defaults to no effects and to NOT ending
     * the pass — the two things that were implicit in whether a block happened to be followed by a
     * `return`.
     */
    @Test
    fun should_default_a_handled_verdict_to_deciding_nothing_further() {
        val verdict = StageVerdict.Handled(newState = io.apptolast.paparcar.domain.detection.state.DetectionSessionState())
        assertTrue(verdict.effects.isEmpty())
        assertTrue(verdict.notes.isEmpty())
        assertEquals(false, verdict.stopsIteration)
    }
}

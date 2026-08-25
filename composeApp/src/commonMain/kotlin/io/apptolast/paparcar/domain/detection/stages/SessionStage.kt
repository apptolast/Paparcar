package io.apptolast.paparcar.domain.detection.stages

import io.apptolast.paparcar.domain.detection.physics.SavedParkingShape
import io.apptolast.paparcar.domain.detection.state.DetectionSessionState
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig

/**
 * [09 §4] **The precedence, as code.**
 *
 * The detection loop evaluates ten branches per fix and the FIRST one that applies wins. That order
 * IS behaviour — permute two entries and a different pin gets planted — and today it is nothing but
 * **the physical position of the blocks inside a 700-line method**. The class KDoc that documents it
 * is wrong: it omits the hold, which runs first, and calls the user-confirm branch a short-circuit
 * when three branches outrank it [08 §10.3].
 *
 * This enum is that order, declared. It is the plan's very first artefact for Phase 3 and it moves
 * no branch yet: what it buys is that from here on the order is a value somebody has to edit on
 * purpose, next to the reason each entry outranks the one below it.
 *
 * ⚠️ The order below is the **measured** one, not the documented one. It was established by
 * `StagePrecedenceCharacterizationTest` (P0.1), which pins four of the adjacent pairs with tests
 * verified to fail when the pair is permuted — and which becomes `StageOrderTest` at the end of this
 * phase without a single assert being edited.
 */
enum class DetectionStage(val outranksBecause: String) {

    /**
     * A confirm held through its grace window resolves before anything else looks at this fix.
     * [DET-C-02][DET-CONFIRM-FRESHNESS-001]
     *
     * Pinned by `should_plant_the_held_pin_not_the_answer_fix_when_the_user_says_yes_during_a_hold`
     * and `should_swallow_the_fix_while_a_tentative_confirm_is_holding`.
     */
    HOLD_RESOLUTION("a held confirm owns the fix that would otherwise re-decide it"),

    /**
     * Steps before any driving mark the arming event as spurious, and that abort outranks even a
     * user's "Sí" — a tap cannot confirm a session that never had a car in it.
     *
     * Pinned by `should_abort_the_false_enter_even_when_the_user_already_said_yes`.
     */
    FALSE_ENTER_ABORT("a session that never drove cannot be confirmed by anyone, user included"),

    /**
     * The no-movement budget folds the session even with an answer pending, for the same reason.
     * [DET-ZOMBIE-PROBE-001][DET-JAM-WINDOW-001]
     *
     * Pinned by `should_fold_the_no_movement_budget_even_when_the_user_already_said_yes`.
     */
    NO_MOVEMENT_BUDGET("a session with nothing measured is over before it can be answered"),

    /**
     * Nothing may be saved before it is known WHOSE car it is — the save needs a vehicle id, so the
     * attribution has to have happened by the time any confirm runs, within the same fix.
     * [VEH-ACTIVE-FENCE-001][DET-BT-OWNERSHIP-001]
     *
     * Pinned by `should_resolve_the_vehicle_before_confirming_within_the_same_fix`.
     */
    VEHICLE_ATTRIBUTION("a park with no owner cannot be saved, so ownership is settled first"),

    /**
     * A tap outranks every heuristic below it: the user is the highest authority in the system.
     * [BUG-COORD-115][DET-CONFIRM-ANCHOR-001]
     */
    USER_CONFIRM("an explicit answer outranks every inference under it"),

    /** With no driving seen there is nothing for the decision stages to decide. */
    PRE_DRIVE_SKIP("no drive, no decision — the stages below all reason about a trip"),

    /** The user was asked and never answered. [DET-RECONCILE-001] */
    RESPONSE_TIMEOUT("an expired question is resolved before a new one is opened"),

    /** The candidate window's own verdict, judged on real elapsed time. */
    CANDIDATE("an open candidate is decided before a new confirm path is tried"),

    /** [DET-D-03] The steps+egress short-circuit, judged with elapsed = 0. */
    FAST_CONFIRM("a proof complete right now does not wait for a window"),

    /** Advances the confirmation phase only. HIGH confidence never auto-confirms by itself. */
    CONFIDENCE_SCORING("scoring is the fall-through: it decides nothing, it only advances");
}

/**
 * THE precedence. The order of this list is the order of the `collect`'s branches today, verified
 * against the running code in 02 §4 — hold FIRST.
 *
 * Kept as a separate declaration from the enum on purpose: two statements of the same order that
 * must agree, so permuting either one alone fails `StageOrderTest`.
 */
val detectionStageOrder: List<DetectionStage> = listOf(
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
)

/**
 * One branch of the detection loop, once it has a name and a file.
 *
 * Deliberately **pure**: a stage decides, it does not act. Everything it wants done comes back as a
 * [DetectionEffect] for the executor to run — including the one lookup that needs I/O
 * ([DetectionEffect.ResolveVehicle]). "No stage imports a repository" is the acceptance criterion of
 * P3.11 and will be enforced by an architecture test, the way this project already enforces its
 * other doctrines.
 */
interface SessionStage {
    /** Which entry of [detectionStageOrder] this stage is. */
    val stage: DetectionStage

    fun evaluate(
        state: DetectionSessionState,
        fix: GpsPoint,
        now: Long,
        config: ParkingDetectionConfig,
    ): StageVerdict
}

/** What a stage says about a fix. */
sealed interface StageVerdict {

    /** This stage does not apply to this fix: carry on down the list. */
    data object Skip : StageVerdict

    /**
     * This stage handled the fix.
     *
     * @param stopsIteration The `return@collect` of today's loop, made explicit. A stage that
     *   handles a fix does not necessarily end the pass — the false-ENTER abort does, the scoring
     *   stage does not — and until now the difference was whether the block happened to be followed
     *   by a `return`.
     */
    data class Handled(
        val newState: DetectionSessionState,
        val effects: List<DetectionEffect> = emptyList(),
        val stopsIteration: Boolean = false,
    ) : StageVerdict
}

/**
 * What a stage ASKS FOR, as data. The executor is the only place in the core that performs I/O
 * [09 §4].
 *
 * The point is not that side effects disappear — they cannot — but that a decision and its
 * consequence stop being the same statement. Today `runConfirm` both decides and saves, so there is
 * no way to assert what a branch WOULD do without letting it do it.
 *
 * ⚠️ Nothing emits these yet, on purpose: P3.0 is scaffolding and moves no branch. What keeps the
 * type from being decoration meanwhile is `StageScaffoldTest`, which maps every I/O method the
 * coordinator has today onto the effect that will replace it — so an arm nobody has thought about
 * shows up as a gap now rather than as a surprise in P3.11.
 */
sealed interface DetectionEffect {

    /** Save the park. Replaces `runConfirm` / `beginConfirm`'s tail. */
    data class Confirm(
        val shape: SavedParkingShape,
        val vehicleId: String?,
        val pathLabel: String,
    ) : DetectionEffect

    /**
     * Ask the user to mark the spot: no artifact is honest here. Replaces `nudgeUnattended`.
     *
     * @param reasonKey The verdict's OWN reason, carried verbatim — the trace vocabularies are a
     *   contract and are never unified [07 §3.4.1].
     */
    data class AskUser(
        val reasonKey: String,
        val vehicleId: String?,
        val distanceMeters: Double? = null,
    ) : DetectionEffect

    /** Put a confirmation prompt on screen. Replaces `notifyParkingConfirmation`. */
    data class Prompt(val pathLabel: String, val reasonKey: String?) : DetectionEffect

    /** Take a prompt off screen. Replaces the direct `notificationPort.dismiss` calls. */
    data object DismissPrompt : DetectionEffect

    /**
     * Resolve which vehicle this session belongs to. **The only effect that answers back**, and the
     * only I/O any stage needs: the stage decides in pure code with
     * `VehicleFenceOwnershipPolicy.resolveSessionVehicleId` and asks for the lookup, then the
     * executor re-enters through an atomic entrypoint. If this turns out to be awkward, the effect
     * is wrong — not the rule about repositories.
     */
    data class ResolveVehicle(val nominatingVehicleId: String?) : DetectionEffect

    /** End the session with a terminal outcome. Replaces the `completed = true; return@collect` pairs. */
    data class EndSession(val outcome: String) : DetectionEffect
}

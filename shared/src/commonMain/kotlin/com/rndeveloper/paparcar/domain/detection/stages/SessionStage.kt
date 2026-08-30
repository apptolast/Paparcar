package com.rndeveloper.paparcar.domain.detection.stages

import com.rndeveloper.paparcar.domain.detection.HoldAction
import com.rndeveloper.paparcar.domain.detection.physics.SavedParkingShape
import com.rndeveloper.paparcar.domain.model.ParkingConfidence
import com.rndeveloper.paparcar.domain.detection.state.DetectionSessionState
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig

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

    /**
     * @param stoppedDurationMs How long the CURRENT stop has lasted, or 0 while moving. Presented
     *   rather than derived: the loop measures it once per fix and several stages read it, and a
     *   stage recomputing it from the state would be a second clock.
     */
    fun evaluate(
        state: DetectionSessionState,
        fix: GpsPoint,
        now: Long,
        stoppedDurationMs: Long,
        config: ParkingDetectionConfig,
    ): StageVerdict
}

/**
 * What a stage says about a fix.
 *
 * ## Why both arms carry [notes]
 *
 * A third of what these branches do is EMIT DIAGNOSTICS, and a good part of that happens on the
 * paths where the branch decides to do nothing — "notif suppressed, timeout in ~4200ms" is a
 * `PARKDIAG` line produced by a branch that changes no state at all. The plan schedules the
 * diagnostics tap LAST (P3.12), after all ten stage moves; the very first stage move needs it,
 * because dropping those lines would change `parkdiag`, and `parkdiag` is the field-test instrument.
 *
 * So the notes channel landed here first in its smallest honest form — a list of strings the
 * orchestrator logged in order, keeping every line byte-identical. P3.13 gives it the type it was
 * always going to need, for the reason on [DiagnosticNote].
 */
sealed interface StageVerdict {

    /** Lines this stage wants in the trace, in order. */
    val notes: List<DiagnosticNote>

    /** This stage does not apply to this fix: carry on down the list. */
    data class Skip(override val notes: List<DiagnosticNote> = emptyList()) : StageVerdict

    /**
     * This stage handled the fix.
     *
     * ⚠️ **[newState] is only safe for changes that are IDEMPOTENT against a stale snapshot.** A
     * stage reasons about the state as it stood at the top of the iteration, and by the time its
     * verdict is applied the step collector may have counted a step. Assigning a phase is
     * idempotent — the same phase results whatever the counters did. A transition defined
     * RELATIVE to a counter is not: `egress.candidateDiscarded()` stamps the freshness line at
     * "wherever the count is NOW", and replaying it from a snapshot silently loses the steps taken
     * in between. Those belong in [effects], where the executor applies the transition to the live
     * state exactly as the branch did.
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
        override val notes: List<DiagnosticNote> = emptyList(),
    ) : StageVerdict
}

/**
 * A line a stage wants in the trace — and, when something READS it, the name to read it by.
 *
 * ## Why the channel is not `List<String>` any more
 *
 * Because one decision was being made out of it. The no-movement budget picks
 * `aborted_no_movement_jam` over `aborted_no_movement` from whether the extension was announced, and
 * the loop learned that by asking **whether the stage had emitted a note at all**
 * (`notes.isNotEmpty()`). A verdict keyed on the diagnostics channel is the same defect the tap's
 * KDoc names in `jamExtensionLogged` — an input to a decision wearing a logging name — and here it
 * was one level worse: not even a named flag, just the presence of *some* text.
 *
 * [claim] is the fix, and its scope is deliberately tiny. A note gets a name **only** when a
 * decision reads it. Everything else stays what it is: a line for the trace, said once, in order.
 * Naming all sixty would be inventing a vocabulary nobody consumes — and choosing which notes reach
 * the REMOTE trace is a separate decision with its own write budget [09 §7, P4.2].
 */
data class DiagnosticNote(val text: String, val claim: Claim? = null) {

    /** The notes a DECISION reads. One entry, and it should stay hard to add to. */
    enum class Claim {
        /** [DET-JAM-WINDOW-001] The extended no-movement budget was announced for this session.
         *  What makes the eventual fold `aborted_no_movement_jam` instead of `aborted_no_movement`,
         *  which is the instrument that sizes the jam cohort. */
        NO_MOVEMENT_BUDGET_EXTENDED,
    }
}

/** Plain trace lines, in order. */
fun notes(vararg texts: String): List<DiagnosticNote> = texts.map { DiagnosticNote(it) }

/**
 * Sugar for the reducers that accumulate their lines as they go.
 *
 * Deliberately only for `+=` on a mutable list. The `List + element` counterpart was written and
 * then removed: it loses to the stdlib's own `Collection<T>.plus`, which happily infers `T = Any`
 * and produces a `List<Any>` that only fails LATER, at the call that consumes it. A sugar that can
 * silently pick the wrong overload is worse than no sugar.
 */
operator fun MutableList<DiagnosticNote>.plusAssign(text: String) {
    add(DiagnosticNote(text))
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

    /**
     * Save the park.
     *
     * @param mayHold [DET-C-02] Whether this confirm may wait out the post-confirm grace window
     *   before it is final. An INFERRED confirm may — the window is there to rule out an errand stop
     *   — but an ANSWERED one may not: the user already told us, and there is nothing a grace window
     *   could learn that outranks that. It is a property of the DECISION, not of the executor;
     *   before this it was the difference between calling `beginConfirm` and calling `runConfirm`,
     *   which is exactly the kind of thing a call site gets to forget.
     */
    data class Confirm(
        val shape: SavedParkingShape,
        val vehicleId: String?,
        val pathLabel: String,
        val mayHold: Boolean,
    ) : DetectionEffect

    /**
     * Ask the user to mark the spot: no artifact is honest here. Replaces `nudgeUnattended` (and
     * `closeHumanPoweredRide`, which is a nudge with a log line in front of it).
     *
     * @param reasonKey The verdict's OWN reason, carried verbatim — the trace vocabularies are a
     *   contract and are never unified [07 §3.4.1].
     * @param at Where the user is being asked about. The nudge stamps it into the trace.
     */
    data class AskUser(
        val reasonKey: String,
        val vehicleId: String?,
        val at: GpsPoint,
        val distanceMeters: Double? = null,
    ) : DetectionEffect

    /**
     * Put a confirmation prompt on screen.
     *
     * ⚠️ Deliberately SEPARATE from [RecordPromptShown], which the P3.0 scaffold had as one arm.
     * They are not one thing: on the HIGH lane the notification fires, then the candidate marker,
     * then the prompt marker — a third event sits BETWEEN the action and its record. Fusing them
     * reorders two events that today have a defined order, which is the sort of "invisible" change
     * a refactor is not allowed to make.
     */
    /** [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] [at] is WHERE the question is about — the
     *  witnessed car stop, or the posting fix when this session has no anchor yet. It travels on
     *  the effect rather than being re-derived by the executor, so the marker the user sees and
     *  the state that raised the question can never point at two different places. */
    data class NotifyPrompt(val confidence: ParkingConfidence, val at: GpsPoint) : DetectionEffect

    /**
     * Stamp the prompt's instant into the remote trace. [DET-FROZEN-COUNTER-001] made this
     * mandatory: the 2026-07-25 Redmi prompt was invisible in forensics and the 15-minute window it
     * opened could only be inferred backwards from the timeout.
     */
    data class RecordPromptShown(val pathLabel: String, val confidence: ParkingConfidence) : DetectionEffect

    /**
     * [DET-JAM-WINDOW-001] The extended no-movement budget ran and the session folded anyway.
     *
     * Diagnostics only, and the instrument is the point: field data has to SIZE this cohort — a jam
     * that never cleared, or a crawl into a re-park? — before anyone decides whether it deserves a
     * nudge. The 21-08 sweep over 1,359 sessions found it EMPTY, so the question is still open.
     */
    data class RecordJamFold(
        val recentCreepMeters: Double,
        val rawPeakMps: Float,
        val at: GpsPoint,
    ) : DetectionEffect

    /** The candidate window opened. Diagnostics only — but a side effect all the same, and the tap
     *  absorbs it in P3.12. */
    data class RecordCandidateOpened(val fromPhase: String) : DetectionEffect

    /**
     * [DET-SOLID-001] A save the guards refused becomes a QUESTION.
     *
     * Kept as ONE compound effect rather than split into dismiss + notify + record + state change,
     * because `degradeToPrompt` reads its own clock for the prompt instant — splitting it would
     * either move that read or invent a second one, and both change what the trace says.
     */
    data class DegradeToPrompt(
        val pathLabel: String,
        val reasonKey: String,
        val at: GpsPoint,
    ) : DetectionEffect

    /**
     * [DET-WALK-ENTERED-ANCHOR-ZONE-001] Save the park as an AREA: the anchor is doubtful but the
     * doubt is BOUNDED, so the park is kept and only its precision degrades.
     *
     * Compound on purpose. If the zone save is refused or fails, the branch falls back to the ask
     * that its own reason names — the user still gets the offer instead of silence — and that
     * fallback is a property of executing the save, not a second decision the stage could make
     * without knowing whether the first one worked.
     */
    data class SaveZone(
        val reasonKey: String,
        val center: GpsPoint,
        val doubtMeters: Double,
        val vehicleId: String?,
        val at: GpsPoint,
    ) : DetectionEffect

    /**
     * [DET-RECONCILE-001] The user never answered, and every anchor taint came back clean: save at
     * the pinned anchor with low reliability so nothing community-facing trusts it on its own.
     *
     * Compound for the same reason as [SaveZone]: if a guard degrades this save to yet another
     * prompt, the session ends anyway — the user already ignored one for the full window, and
     * ending is the only non-looping exit. [BUG-STUCK-SESSION]
     */
    data class SaveUnattended(val shape: SavedParkingShape, val vehicleId: String?) : DetectionEffect

    /**
     * [BUG-COORD-105][DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001] The candidate window expired
     * without its egress proof: fall back to the prompt that is still on screen and move the
     * freshness line.
     *
     * An EFFECT rather than a `newState`, because moving that line is defined relative to the step
     * count as it stands when it is applied — see the warning on [StageVerdict.Handled].
     */
    data class DiscardCandidate(val shownAt: Long, val at: GpsPoint) : DetectionEffect

    /**
     * [DET-HUMAN-POWERED-EARLY-CLOSE-001] A muscle-powered ride at a matured rest: nudge and END.
     *
     * Distinct from [AskUser] even though it nudges, because it also ends the SESSION — and the two
     * stages that reach it did so through different plumbing (one returned `true`, the other relied
     * on its call site ending the session for every terminal branch). One effect, one answer.
     */
    data class CloseHumanPowered(val vehicleId: String?, val at: GpsPoint) : DetectionEffect

    /**
     * [DET-C-02] Drop a held confirm and say WHY, in one move.
     *
     * The two halves always travel together and once did not: [DET-HOLD-BRANCHES-MUST-SPEAK-001]
     * exists because one discard branch spoke and its sibling was mute, which made two different
     * outcomes indistinguishable from outside. Binding them into one effect is how that stays fixed.
     */
    data class DiscardHold(
        val action: HoldAction,
        val heldMs: Long,
        val pathLabel: String,
        val at: GpsPoint,
    ) : DetectionEffect

    /** [DET-C-02] The hold resolved into a save. Stamped BEFORE the save and against the HELD pin —
     *  the save may fail, and the trace has to say the hold resolved either way. */
    data class RecordHoldSettled(
        val heldMs: Long,
        val pathLabel: String,
        val at: GpsPoint,
    ) : DetectionEffect

    /** Take a prompt off screen. Replaces the direct `notificationPort.dismiss` calls. */
    data object DismissPrompt : DetectionEffect

    /**
     * Resolve which vehicle this session belongs to — **the only I/O any stage needs**.
     *
     * ⚠️ The scaffold originally described this as "the stage decides in pure code and asks for the
     * lookup". It cannot: the policy needs the lookup's ANSWER to decide — which vehicle is active,
     * and whether the nominating one is Bluetooth-paired. The sequence is ask → decide.
     *
     * So this effect asks for FACTS instead of announcing a verdict, and the decision stays in
     * `VehicleFenceOwnershipPolicy.resolveSessionVehicleId`, which was already a pure function. The
     * executor fetches, calls that policy and applies the result atomically. Following the plan's
     * own warning — *if this gets awkward the effect is wrong, not the rule* — is what produced the
     * corrected shape.
     */
    data class ResolveVehicle(val nominatingVehicleId: String?) : DetectionEffect

    /** End the session with a terminal outcome. Replaces the `completed = true; return@collect` pairs. */
    data class EndSession(val outcome: String) : DetectionEffect
}

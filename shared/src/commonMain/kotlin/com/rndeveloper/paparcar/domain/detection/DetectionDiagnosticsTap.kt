package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.model.GpsPoint
import kotlin.concurrent.Volatile

/**
 * The `parkdiag` prefix every actor inside one detection pass logs under, so a fix's whole story
 * greps as one block. Shared rather than repeated: the fix reducer and the orchestrator are two
 * files now and a tag that drifts splits a trace in half.
 */
const val PARKDIAG_COORD = "PARKDIAG/Coord"

/**
 * [09 §7] **The single emitter.** Every remote diagnostics event a detection session produces leaves
 * through here, under the session id this tap is holding.
 *
 * ## What it is for
 *
 * Not tidiness: a trace that only exists while the phone is tethered to a PC is no trail at all —
 * nobody drives cabled, and the logcat ring had already rotated past the decision the first time
 * anyone went looking for it. So the rule is that a branch which can decide a session must leave
 * something in the REMOTE trace, and a single door is what makes "did this branch speak?" a question
 * with one place to look.
 *
 * Between sessions it holds no id and drops what it is given, which is the same behaviour the
 * coordinator's `logDetection` had: an event with no session to belong to is unreachable in
 * Firestore anyway, because retention finds sessions by their parent document.
 *
 * ## The dedups it owns, and the one it does NOT
 *
 * A marker that exists ONLY to keep one line in the trace instead of one per fix is diagnostics
 * bookkeeping, and it belongs here. Three NAMED forms, because the collector's loose `var`s were
 * three DIFFERENT dedups wearing the same disguise [DET-EDGE-MARKERS-TO-THE-TAP-001]:
 *
 * - **[latchOnce]** — one line per session, ever. The form the tap was born with.
 * - **[risingEdge]** — one line per RISE of a boolean signal, re-armed when the signal falls:
 *   a departure per drive, not one per session. `latchOnce` here would swallow the second exit.
 * - **[valueChanged]** — one line per CHANGE of a stamp, including from the empty seed: a stamp
 *   INHERITED from before the session logs on the first fix with its true age, and a second
 *   boarding re-emits. `latchOnce` here would swallow both.
 *
 * ⚠️ **`jamExtensionLogged` is NOT one of those, however much its name says it is.** It reads like a
 * log dedup and it is a VERDICT INPUT: the no-movement budget picks
 * `aborted_no_movement_jam` over `aborted_no_movement` from it, which is the distinct outcome
 * [DET-JAM-WINDOW-001] left as an instrument to size that cohort. Moving it in here would bury a
 * decision inside the diagnostics, so it stays with the loop that decides. The name is the trap; it
 * is left alone because renaming it changes nothing observable and this step changes nothing at all.
 */
class DetectionDiagnosticsTap(
    private val logger: DetectionEventLogger,
) {

    @Volatile
    private var sessionId: String? = null

    private val latches = mutableSetOf<Latch>()
    private val risenEdges = mutableSetOf<Edge>()
    private val valueMarks = mutableMapOf<ValueMark, Long>()

    /** A one-per-session marker. Named so the reason it exists survives the next edit. */
    enum class Latch {
        /**
         * [DET-MOTOR-PROOF-001] The pedal-cadence veto held for the first time. A latch and not an
         * equality on purpose: the previous edge fired on the step that made the count EQUAL the
         * threshold, so a session whose second distinct fix arrived later satisfied the verdict with
         * no line at all. **A veto that can decide a session silently is the defect.**
         */
        PEDAL_CADENCE,

        /**
         * [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §A/§C] The motor band crossed the sustained-drive
         * proof for the first time — the refutation that outranks both AR stamps. If the band ever
         * refutes a ride that really was muscle, this is the line that will say so.
         */
        MOTOR_WITNESSED,

        /**
         * [DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001] Its displacement counterpart: the
         * sustained displacement rate crossed motor proof. The 2026-08-26 session is the argument —
         * the refutation that failed left no remote mark and the parkdiag had to come off by cable.
         */
        MOTOR_WITNESSED_BY_DISPLACEMENT,
    }

    /**
     * A re-armable boolean edge: [risingEdge] answers true on each LOW→HIGH transition and re-arms
     * when the signal falls, so a hint that clears (driving away) and sets again logs again.
     */
    enum class Edge {
        /** [DET-LOG-04] AR `IN_VEHICLE EXIT`, edge-logged from the state flip fed by onVehicleExit:
         *  one departure per drive, not one per fix and not one per session. */
        VEHICLE_EXIT,
    }

    /**
     * A stamp deduplicated by VALUE: [valueChanged] answers true whenever the observed stamp
     * differs from the last one emitted — including from the empty seed, on purpose
     * [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §C]: the AR evidence state is a singleton only reset
     * when a session ENDS, so a stamp delivered between sessions rides into the next one, and
     * seeding from "nothing seen" is what gets that inherited stamp logged on the first fix with
     * its true age. That inheritance is the hardest kind of veto to explain after the fact.
     */
    enum class ValueMark {
        /** AR `ON_BICYCLE ENTER` true-time stamp — the input of the human-powered veto. */
        BICYCLE_RIDE_STAMP,

        /** AR `IN_VEHICLE ENTER` true-time stamp — the boarding that should supersede the veto,
         *  which is precisely the comparison the verdict makes. */
        VEHICLE_RIDE_STAMP,
    }

    /** A session begins: everything emitted from now on belongs to it, and every marker form
     *  starts clean — a marker never leaks from one session into the next. */
    fun open(sessionId: String) {
        this.sessionId = sessionId
        latches.clear()
        risenEdges.clear()
        valueMarks.clear()
    }

    /** A session ends. Emissions after this are dropped — they belong to nobody. */
    fun close() {
        sessionId = null
    }

    /** The one door. [build] is only invoked when there is a session to attribute the event to. */
    suspend fun emit(build: (sessionId: String) -> DetectionEvent) {
        val sid = sessionId ?: return
        logger.log(build(sid))
    }

    /**
     * [DET-HOLD-BRANCHES-MUST-SPEAK-001] Every exit of the post-confirm hold says what it did.
     *
     * The lane earned its own method because it earned its own ticket: six of its seven exits were
     * local-log only, and a branch that says nothing cannot be discriminated by any test — which is
     * why `DET-CONFIRM-BRANCH-ORDER-MUST-BE-TESTABLE-001` could not write a single one of the three
     * precedence tests it set out to write.
     */
    suspend fun hold(
        action: HoldAction,
        atMs: Long,
        heldMs: Long? = null,
        pathLabel: String? = null,
        location: GpsPoint? = null,
    ) {
        emit { sid ->
            DetectionEvent.Hold(
                sid, atMs, action = action, heldMs = heldMs, pathLabel = pathLabel, location = location,
            )
        }
    }

    /**
     * Claim a one-per-session marker.
     *
     * @return true the FIRST time it is claimed in this session, false afterwards. Cleared by
     *   [open], so a marker never leaks from one session into the next — which is the bug this
     *   replaces a loose `var` to avoid.
     */
    fun latchOnce(latch: Latch): Boolean = latches.add(latch)

    /**
     * Observe a boolean signal and claim its rising edge.
     *
     * @return true exactly when the signal is high and was not high on the previous observation.
     *   A low observation re-arms the edge, so the next rise claims again.
     */
    fun risingEdge(edge: Edge, high: Boolean): Boolean =
        if (high) risenEdges.add(edge) else { risenEdges.remove(edge); false }

    /**
     * Observe a stamp and claim it if it CHANGED since the last claimed value.
     *
     * @return true when [value] differs from the last claimed value for [mark] — including the
     *   first observation of the session, because the seed is "nothing seen" (see [ValueMark]).
     */
    fun valueChanged(mark: ValueMark, value: Long): Boolean =
        if (valueMarks[mark] != value) {
            valueMarks[mark] = value
            true
        } else {
            false
        }
}

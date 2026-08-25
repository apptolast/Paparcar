package io.apptolast.paparcar.domain.detection.state

import io.apptolast.paparcar.domain.detection.state.ConfirmationPhase
import io.apptolast.paparcar.domain.detection.state.promptShownAt
import io.apptolast.paparcar.domain.model.GpsPoint

/**
 * [DET-C-02] A confirmed-but-HELD parking decision, waiting out the `confirmHoldMs` grace window.
 *
 * Captured at the egress confirm so the saved location stays pinned to the parked-car position even
 * if the user keeps walking during the hold.
 *
 * ⛔ **This type is compared by IDENTITY** (`===`), by the hold watchdog in the coordinator: the
 * watchdog fires after `confirmHoldMs` and asks "is the hold I was launched for still the hold that
 * is standing?". Giving this class a mutable-looking field that the fix loop updates — a fix
 * counter, a refreshed location — makes every fix produce a NEW instance, which cancels and
 * restarts the watchdog on every fix and means the hold never settles. The temptation is real
 * because a counter here would be useful telemetry. Do not.
 */
data class PendingConfirm(
    val location: GpsPoint,
    val reliability: Float,
    val vehicleId: String?,
    val pathLabel: String,
    val confirmedAt: Long,
)

/**
 * [09 §5] **What the session has told the user, and what it is holding** — the second sub-state.
 *
 * It owns the whole prompt/confirm lifecycle: which [ConfirmationPhase] the current stop is in,
 * whether a confirm is being held through its grace window, and whether the user has answered "Sí".
 *
 * ## What this step fixes
 *
 * The three fields were flat neighbours of thirty others, and their combinations were only valid by
 * convention. `REFACTOR-200` had already made the four legacy timestamp flags unrepresentable in an
 * invalid form by folding them into [ConfirmationPhase]; this does the same one level up, for the
 * three values that describe the same conversation.
 *
 * The sharper part is the hold. `pendingConfirm = null` was written at three call sites that mean
 * three different things:
 *
 *  - the held pin went STALE (the position outran the steps) — discard, keep detecting;
 *  - the user drove off mid-hold — discard, keep detecting;
 *  - the save was refused as an implausible repark — discard **and re-open the prompt**.
 *
 * Read as `copy(pendingConfirm = null)` the first two are indistinguishable from the third, and the
 * third's second half (`phase = Notified`) looks like an unrelated line that happens to sit in the
 * same `copy`. [discardingHold] and [degradedToPrompt] say which is which.
 *
 * @property phase Where the current stop sits in the prompt lifecycle. Reset to `Idle` whenever the
 *   vehicle drives away, so a new stop starts clean.
 * @property pendingConfirm A confirm held through its grace window, or null. While non-null the
 *   session is "tentatively parked": it stays alive so that resuming driving before the window
 *   elapses discards it and re-anchors at the real spot. See the ⛔ note on [PendingConfirm].
 * @property userConfirmed The user answered "Sí". The highest authority in the system: it
 *   short-circuits every guard and outranks the hold's own staleness check.
 */
data class ConfirmationLifecycle(
    val phase: ConfirmationPhase = ConfirmationPhase.Idle,
    val pendingConfirm: PendingConfirm? = null,
    val userConfirmed: Boolean = false,
) {

    /** Epoch-ms when a prompt was first put on screen in this stop, or null if none is showing. */
    val promptShownAt: Long? get() = phase.promptShownAt

    // ── Phase transitions ─────────────────────────────────────────────────────

    /** Low/Medium confidence first seen, before any prompt. */
    fun lowReached(now: Long): ConfirmationLifecycle =
        copy(phase = ConfirmationPhase.LowReached(now))

    /** A prompt is now on screen; [shownAt] opens the response-timeout window. */
    fun notified(shownAt: Long): ConfirmationLifecycle =
        copy(phase = ConfirmationPhase.Notified(shownAt))

    /**
     * HIGH confidence: the candidate observation window opens.
     *
     * [shownAt] is passed rather than defaulted to `now` because a prompt shown at Low/Medium keeps
     * its ORIGINAL instant — the response timeout ticks from when the user first saw a question,
     * not from when the session got more confident about it.
     */
    fun candidate(highReachedAt: Long, hadVehicleExit: Boolean, shownAt: Long): ConfirmationLifecycle =
        copy(phase = ConfirmationPhase.Candidate(highReachedAt, hadVehicleExit, shownAt))

    /** The stop is over (the vehicle drove away): the conversation starts from scratch. */
    fun stopEnded(): ConfirmationLifecycle = copy(phase = ConfirmationPhase.Idle)

    // ── The hold ──────────────────────────────────────────────────────────────

    /** [DET-C-02] Open the grace window on a confirm the evidence already earned. */
    fun holding(pending: PendingConfirm): ConfirmationLifecycle = copy(pendingConfirm = pending)

    /**
     * Drop the held confirm and keep detecting toward the real park — the held pin went stale, or
     * the user drove off mid-hold. The prompt state is untouched: nothing was said to the user.
     */
    fun discardingHold(): ConfirmationLifecycle = copy(pendingConfirm = null)

    /**
     * [DET-SOLID-001] The save was refused as an implausible repark, so the silent confirm becomes
     * a QUESTION: the hold is dropped and a prompt goes back on screen in the same move. Distinct
     * from [discardingHold] precisely because the user is now being asked something.
     */
    fun degradedToPrompt(shownAt: Long): ConfirmationLifecycle =
        copy(pendingConfirm = null, phase = ConfirmationPhase.Notified(shownAt))

    // ── The user ──────────────────────────────────────────────────────────────

    /** The user answered "Sí". */
    fun userSaidYes(): ConfirmationLifecycle = copy(userConfirmed = true)
}

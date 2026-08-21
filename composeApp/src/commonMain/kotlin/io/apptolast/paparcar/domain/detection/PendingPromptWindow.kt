package io.apptolast.paparcar.domain.detection

/**
 * [DET-ASK-STATE-001] Durable record of an OPEN "did you park?" question.
 *
 * Field 2026-07-25/26: the prompt at 00:35 existed ONLY as a system notification. Opening the app
 * inside its 15-minute window showed "following your trip" — the one thing the user could do
 * (answer) was invisible, so the window timed out and degraded. The question must be app state,
 * exactly as [PendingParkNudge] made the "where did you leave your car?" ask app state.
 *
 * Note what this record is NOT: a mirror of whether the notification is visible. The tray card can
 * be gone — swiped, or auto-cancelled by a tap — while the question is still owed. It tracks the
 * QUESTION; see [isPromptWindowOpen].
 *
 * **Why this can be derived from a single slot.** The prompt lives on notification channel
 * [io.apptolast.paparcar.domain.notification.AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID],
 * which admits exactly two verbs: `showParkingConfirmation` opens the question, and any other
 * operation on that id (`dismiss`, or the `showParkingSavedConfirm` morph into "parked + revert")
 * closes it. So the window is open **iff the last operation on that channel was the prompt post** —
 * no signalling from the coordinator, and no list of close sites to keep in sync by hand. The
 * notification adapter writes it at the same choke point it posts, mirroring [PendingParkNudge].
 */
data class PendingPromptWindow(
    /** Epoch-ms the prompt was posted — the window's own age, and the anchor for [isPromptWindowOpen]. */
    val shownAtMs: Long,
    /** The vehicle name the NOTIFICATION used, carried verbatim so the in-app row and the tray can
     *  never word the same question differently. Null when the asking path had no name. */
    val vehicleName: String? = null,
)

/**
 * Pure visibility rule for the Home question row: a persisted window is open while it is still
 * answerable.
 *
 * **Making the tray card go away is NOT an answer, and must not close the question.** Two ways of
 * silencing the notification reach no code at all, by design:
 *  - the user SWIPES it away — there is deliberately no `setDeleteIntent` on this channel;
 *  - the user TAPS the body, which opens the app and, via `setAutoCancel`, removes the card.
 *
 * In both cases the slot stays written and the row keeps asking, which is the whole point of the
 * ticket: the tap case is precisely "user lands in Home with the question still owed". Anyone
 * tempted to wire a delete-intent that clears the window would be deleting the feature — silencing
 * a notification is not deciding where the car is. (A process kill mid-window behaves the same and
 * is equally correct: the notification survives the kill and its buttons still work.)
 *
 * The ONE thing that closes an unanswered question is time, and the deadline is not a new number —
 * it is the prompt's own
 * [io.apptolast.paparcar.domain.model.ParkingDetectionConfig.confirmationResponseTimeoutMs]. Past
 * it the coordinator has already stopped waiting and emitted its own verdict (an unattended save,
 * or a degrade to the mark-parking nudge, which then takes over the row as its own pending ask), so
 * the row would be offering an answer nobody is listening for.
 *
 * A negative age (device clock moved backwards) reads CLOSED — the same distrust of the clock the
 * detection evaluators apply: an unmeasurable age is never taken as evidence the question is live.
 */
fun isPromptWindowOpen(
    window: PendingPromptWindow?,
    nowMs: Long,
    timeoutMs: Long,
): Boolean {
    if (window == null) return false
    val age = nowMs - window.shownAtMs
    return age in 0..timeoutMs
}

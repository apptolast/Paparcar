package com.rndeveloper.paparcar.domain.detection.ports

/**
 * Starts automatic parking detection on demand — backs the cold-start "I'm driving" affordance. [DET-G-01b]
 *
 * Detection normally arms itself when the user's parking geofence is exited. On a true cold start
 * (no parking ever marked → no geofence) a user who opens the app while **already driving** has no
 * automatic trigger. This kicks off the Coordinator tracking job right now: it follows the trip and,
 * when the user parks, the egress detector confirms the spot and creates the first geofence —
 * bootstrapping the automatic loop. No-op where automatic detection isn't available yet (iOS).
 */
interface ManualParkingDetection {
    /** Begin tracking the current trip immediately. Safe to call repeatedly (the service is idempotent). */
    fun start()

    /**
     * Cancel the in-progress tracking session. Called when the user MARKS A PARK MANUALLY — the trip
     * is over, so any coordinator session still running must not (a) plant a late auto-confirm that
     * overwrites the user's manual pin, nor (b) leave a transient orphan geofence. Safe to call when
     * nothing is running (the service no-ops). [DET-MANUAL-CANCEL-001] No-op on iOS.
     */
    fun stop()

    /**
     * [DET-STOP-BUTTON-001] The user tapped "Stop detection" on the live session. Deliberately a
     * separate command from [stop]: that one means "I already parked, here" (the manual pin owns
     * the trip's ending); this one means "this trip is not mine — forget it". It ends the session
     * with its own terminal outcome, plants nothing, and opens a quiet period in which the
     * automatic nominators may not re-arm (`UserStopQuietPeriod.kt`). No-op on iOS.
     */
    fun stopByUser()

    /**
     * [DET-ASK-STATE-001] The user answered the "did you park?" question **from the Home row**
     * instead of the tray notification.
     *
     * Deliberately routed through the SAME service actions the notification's buttons fire
     * (`ACTION_PARKING_CONFIRMED` / `ACTION_PARKING_DENIED`), not through a second path into the
     * coordinator: the intake serialises both [DET-INTAKE-001], so answering twice — once in the
     * app, once in the tray — is idempotent, and the coordinator hooks behind them already dismiss
     * the notification, which is what closes the window everywhere at once.
     *
     * A tap with no live session is a stale answer; the service's existing epilogue handles it.
     * No-op on iOS.
     *
     * @param parked true = "yes, I parked", false = "no, still driving".
     */
    fun answerPrompt(parked: Boolean)
}

package com.rndeveloper.paparcar.domain.detection.state

import com.rndeveloper.paparcar.domain.usecase.parking.PromptReason

/**
 * [REFACTOR-200: explicit state machine replacing the implicit 4-flag encoding]
 *
 * Confirmation lifecycle phase for the current stop in the parking-detection loop.
 *
 * Replaces the legacy fields `lowFirstReachedAt`, `confirmationNotificationShownAt`,
 * `highConfidenceReachedAt`, and `highCandidateHadVehicleExit` (kept in
 * [com.rndeveloper.paparcar.domain.detection.CoordinatorParkingDetector]'s state until
 * 2026-06-08). The implicit triple permitted invalid combinations (e.g. a candidate set
 * while no notification was shown) for a one-instruction window between
 * [kotlinx.coroutines.flow.MutableStateFlow.update] calls; the sealed interface makes those
 * combinations unrepresentable.
 *
 * **Allowed transitions** within a single stop:
 * - `Idle  → LowReached` — first Low/Medium confidence sample, before any notification.
 * - `Idle  → Candidate`  — first High confidence sample with no prior Low/Medium (rare, e.g.
 *   when STILL + vehicleExit arrive together on a long stop).
 * - `LowReached → Notified` — exit/still arrived OR
 *   [com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig.lowNotifTimeoutMs] elapsed.
 * - `LowReached → Candidate` — High confidence reached before the Low/Medium notification
 *   was shown (a fast climb). The notification fires as part of the transition.
 * - `Notified  → Candidate`  — High reached after a Low/Medium notification; `shownAt` is
 *   preserved so the [confirmationResponseTimeoutMs] window keeps ticking from the original
 *   prompt instant, not from the candidate entry.
 *
 * **Reset to [Idle]** whenever the vehicle drives away (the `isDriving` branch in
 * `updateStopTracking`) so a new stop starts from a clean slate.
 *
 * **Why `shownAt` lives on both `Notified` and `Candidate`.** The user's response timeout
 * applies once *any* confirmation prompt is on-screen; both phases imply "prompt visible".
 * Encoding it on both avoids a separate "promptVisibleAt" field that would have to be
 * synced manually.
 */
sealed interface ConfirmationPhase {

    /** No confidence has been scored in the current stop, or the vehicle just drove away. */
    data object Idle : ConfirmationPhase

    /**
     * Low or Medium confidence first observed at [firstReachedAt] (epoch-ms). No notification
     * has been shown yet — the coordinator is waiting for an `activityExit`/`activityStill`
     * signal OR for the `lowNotifTimeoutMs` fallback to elapse.
     */
    data class LowReached(val firstReachedAt: Long) : ConfirmationPhase

    /**
     * A Low/Medium confirmation prompt has been shown to the user at [shownAt] (epoch-ms).
     * Used to drive the response-timeout abort.
     *
     * @param degradedFrom WHY this question exists, or null when it is the ordinary Low/Medium
     *   prompt. See [ConfirmationPhase.promptDegradedFrom].
     *
     *   ⛔ **No default, deliberately** [DET-A-DEGRADED-PROMPT-MUST-NOT-AUTO-CONFIRM-001]. `null`
     *   is the PERMISSIVE answer — it is what lets the response timeout plant a pin — so a future
     *   producer must not be able to buy it by saying nothing. That is the same accident
     *   [UnattendedSaveInput] spells out at length, and it is why both producers of a degraded
     *   prompt had to be found and changed by the compiler rather than by memory.
     */
    data class Notified(
        val shownAt: Long,
        val degradedFrom: PromptReason?,
    ) : ConfirmationPhase

    /**
     * High confidence reached at [highReachedAt] (epoch-ms). The user prompt has been
     * displayed since [shownAt] (which may pre-date [highReachedAt] if the user was
     * first prompted at Low/Medium). [hadVehicleExit] is a snapshot of
     * `vehicleExitConfirmed` at entry to the candidate phase and determines which
     * observation window applies for auto-confirmation.
     */
    data class Candidate(
        val highReachedAt: Long,
        val hadVehicleExit: Boolean,
        val shownAt: Long,
        val degradedFrom: PromptReason?,
    ) : ConfirmationPhase
}

/**
 * Epoch-ms when the confirmation prompt was first shown to the user in this stop, or null
 * if no prompt is on-screen. Used by the response-timeout abort guard.
 */
internal val ConfirmationPhase.promptShownAt: Long?
    get() = when (this) {
        is ConfirmationPhase.Notified -> shownAt
        is ConfirmationPhase.Candidate -> shownAt
        else -> null
    }

/**
 * [DET-A-DEGRADED-PROMPT-MUST-NOT-AUTO-CONFIRM-001] **Why the question on screen was asked** — null
 * for the ordinary Low/Medium prompt, the [PromptReason] when a confirm DEGRADED into it.
 *
 * The companion of [promptShownAt], and the field that was missing. The response timeout reads the
 * *when* and used to have no way to ask the *what kind*, so it treated both questions alike:
 *
 * - The ordinary prompt satisfies [DET-RECONCILE-001]'s premise — a real trip, a real stop and a
 *   vehicle-exit signal already happened, and the only missing piece is a human tap. Saving it on
 *   silence is right, and stays unchanged.
 * - A DEGRADED prompt exists for the opposite reason: a confirm path reached its verdict and the
 *   evidence was refused. Silence adds no evidence, so it may not buy back the one thing the app
 *   had just declined to assert — the EXACT point. Bounded verdicts (every zone) are untouched.
 *
 * Field 2026-10-05, Oppo session `1791123836936`: `CONFIRM_DEGRADED_PROMPT/weak_evidence` at
 * 16:25:49, then `CONFIRMED/unattended_timeout` at 16:40:51 with nobody having answered — an exact
 * pin in a pedestrian street the car cannot enter, on a "drive" of two cold-start fixes, while 1192
 * STEP events said the user was walking.
 */
internal val ConfirmationPhase.promptDegradedFrom: PromptReason?
    get() = when (this) {
        is ConfirmationPhase.Notified -> degradedFrom
        is ConfirmationPhase.Candidate -> degradedFrom
        else -> null
    }

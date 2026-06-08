package io.apptolast.paparcar.domain.coordinator

/**
 * [REFACTOR-200: explicit state machine replacing the implicit 4-flag encoding]
 *
 * Confirmation lifecycle phase for the current stop in the parking-detection loop.
 *
 * Replaces the legacy fields `lowFirstReachedAt`, `confirmationNotificationShownAt`,
 * `highConfidenceReachedAt`, and `highCandidateHadVehicleExit` (kept in
 * [io.apptolast.paparcar.domain.coordinator.ParkingDetectionCoordinator]'s state until
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
 *   [io.apptolast.paparcar.domain.model.ParkingDetectionConfig.lowNotifTimeoutMs] elapsed.
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
     */
    data class Notified(val shownAt: Long) : ConfirmationPhase

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

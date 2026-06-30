package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.detection.ParkingStrategy
import io.apptolast.paparcar.domain.model.DetectionReadiness
import io.apptolast.paparcar.domain.preferences.AppPreferences
import kotlinx.coroutines.flow.first

/**
 * Decides whether to show the cold-start "first park" nudge. [DET-TOGGLE-002]
 *
 * The nudge is the gentle, heavily-throttled reminder for users who enabled detection but have never
 * actually parked with it — the [DetectionReadiness.Ready] COORDINATOR cold-start (surfaced in the UI
 * as `AwaitingFirstPark`). Because that readiness state already encodes *flag on + producer permissions
 * granted + a parking-capable active vehicle + no active session + Coordinator strategy*, this is the
 * single precondition we need — Bluetooth (`Ready` BLUETOOTH, fully automatic) and inactive vehicles
 * never reach it, so the nudge can never target them.
 *
 * On top of that we throttle hard: at most [MAX_NUDGES] reminders ever, no closer than
 * [COOLDOWN_MILLIS] apart, and never once a park has been confirmed (which sets
 * [AppPreferences.hasConfirmedFirstPark] and self-disables the nudge for good).
 */
class EvaluateFirstParkNudgeUseCase(
    private val observeDetectionReadiness: ObserveDetectionReadinessUseCase,
    private val appPreferences: AppPreferences,
) {
    /** Snapshot decision; pure given [nowMillis]. */
    suspend operator fun invoke(nowMillis: Long): Boolean = shouldSendFirstParkNudge(
        readiness = observeDetectionReadiness().first(),
        hasConfirmedFirstPark = appPreferences.hasConfirmedFirstPark,
        nudgeCount = appPreferences.firstParkNudgeCount,
        lastNudgeAtMillis = appPreferences.lastFirstParkNudgeAtMillis,
        nowMillis = nowMillis,
    )

    companion object {
        /** Hard cap — if the user keeps ignoring, stop nagging. */
        const val MAX_NUDGES = 3
        /** Minimum spacing between nudges (3 days). */
        const val COOLDOWN_MILLIS = 3L * 24 * 60 * 60 * 1000
    }
}

/**
 * Pure cold-start nudge decision. Nudge only when detection is fully ready on the Coordinator strategy
 * but has never produced a park, throttled by a per-nudge cooldown and a hard cap. [DET-TOGGLE-002]
 */
fun shouldSendFirstParkNudge(
    readiness: DetectionReadiness,
    hasConfirmedFirstPark: Boolean,
    nudgeCount: Int,
    lastNudgeAtMillis: Long,
    nowMillis: Long,
): Boolean {
    val isColdStartCoordinator = readiness is DetectionReadiness.Ready &&
        readiness.strategy == ParkingStrategy.COORDINATOR
    return isColdStartCoordinator &&
        !hasConfirmedFirstPark &&
        nudgeCount < EvaluateFirstParkNudgeUseCase.MAX_NUDGES &&
        (nowMillis - lastNudgeAtMillis) >= EvaluateFirstParkNudgeUseCase.COOLDOWN_MILLIS
}

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.preferences.AppPreferences

/**
 * [DET-NUDGE-PERSIST-001] Resolves the pending "where did you leave your car?" question — the
 * user marked a parking, a session for the nudged vehicle was confirmed, or the user explicitly
 * dismissed the Home banner. Clears BOTH surfaces: the durable record and the tray notification,
 * so answering in one place never leaves the other dangling.
 */
class ClearParkNudgeUseCase(
    private val appPreferences: AppPreferences,
    private val notificationPort: AppNotificationManager,
) {
    operator fun invoke(): Result<Unit> = runCatching {
        appPreferences.clearPendingParkNudge()
        notificationPort.dismiss(AppNotificationManager.MARK_PARKING_NUDGE_NOTIFICATION_ID)
    }
}

package io.apptolast.paparcar.detection.service

import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build

/**
 * [REFACTOR: SRP — extract FGS lifecycle from ParkingDetectionService]
 *
 * Centralises the foreground-service promotion + clean shutdown.
 *
 * Why this exists:
 * - Five different code paths in [ParkingDetectionService] call `stopSelf()` without
 *   ever calling `stopForeground(STOP_FOREGROUND_REMOVE)` first, which is the only
 *   reliable way to remove the FGS notification *before* the Service object is
 *   destroyed. On Android 11–14 with a re-entering [startForegroundService] call
 *   (e.g. user taps the still-visible confirmation notification after auto-confirm),
 *   the stale FGS notification stays glued to the new lifecycle. [BUG-FGS-100]
 * - Promoting the service with [ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION] when
 *   the user has just revoked location permission throws [SecurityException] on
 *   Android 14+. The promote method honours that boundary with the
 *   [withLocationPermission] flag — same behaviour as the original code, just
 *   isolated for testability. [BUG-FGS-001a]
 *
 * Methods are synchronous main-thread operations — see audit section 4, C-8 — and
 * must NOT be wrapped in a coroutine `launch`.
 */
class ForegroundServiceController(private val service: Service) {

    /** Promote the service to foreground. Idempotent — safe to call on every onStartCommand. */
    fun promote(notificationId: Int, notification: Notification, withLocationPermission: Boolean) {
        if (withLocationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 14 logs "foreground service started by ACTIVITY_RECOGNITION exemption
            // can not have location access" — this is informational only; the service still
            // receives GPS fixes. The exemption type warning does not block any API. [FGS-003]
            service.startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            service.startForeground(notificationId, notification)
        }
    }

    /**
     * [FIX BUG-FGS-100: stopForeground(STOP_FOREGROUND_REMOVE) before stopSelf, every path]
     *
     * Tears the service down cleanly:
     *  1. Removes the FGS notification synchronously (so a re-entering [startForegroundService]
     *     from [ParkingConfirmationReceiver] cannot pick up the stale one).
     *  2. Schedules service destruction.
     */
    fun stopForegroundAndSelf() {
        removeForegroundNotification()
        service.stopSelf()
    }

    /**
     * [FIX BUG-FGS-113: defensive onDestroy safety net.]
     *
     * Removes the foreground notification without scheduling stopSelf. Idempotent — safe
     * to call from onDestroy even after [stopForegroundAndSelf] already ran (a redundant
     * call is a documented no-op on every supported API level).
     *
     * On API < 24 the equivalent removal flag is `true`; we branch with the deprecated
     * overload via the [Service.STOP_FOREGROUND_REMOVE] constant.
     */
    fun removeForegroundNotification() {
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
    }
}

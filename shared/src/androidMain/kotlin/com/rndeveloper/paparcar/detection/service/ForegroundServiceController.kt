package com.rndeveloper.paparcar.detection.service

import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import com.rndeveloper.paparcar.domain.util.PaparcarLogger

/**
 * Outcome of a foreground promotion. [ForegroundServiceController.promote] returns this instead of
 * throwing: on Android 14+ the promotion is a permission-checked IPC call that can legitimately be
 * refused, and a refusal must degrade to "no detection this time", never to a dead app.
 * [DET-A-LOCATION-FGS-MUST-NOT-KILL-THE-APP-001]
 */
enum class ForegroundPromotion {
    /** The service is in the foreground; location work may proceed. */
    PROMOTED,

    /** No runtime location permission. Nothing to retry — the user has to grant it first. */
    REFUSED_MISSING_LOCATION_PERMISSION,

    /** The app was not in a state allowed to start a foreground service (Android 12+). */
    REFUSED_NOT_FOREGROUND_ELIGIBLE,
    ;

    val isPromoted: Boolean get() = this == PROMOTED
}

/**
 * [REFACTOR: SRP — extract FGS lifecycle from CoordinatorDetectionService]
 *
 * Centralises the foreground-service promotion + clean shutdown.
 *
 * Why this exists:
 * - Five different code paths in [CoordinatorDetectionService] call `stopSelf()` without
 *   ever calling `stopForeground(STOP_FOREGROUND_REMOVE)` first, which is the only
 *   reliable way to remove the FGS notification *before* the Service object is
 *   destroyed. On Android 11–14 with a re-entering [Service.startForegroundService] call
 *   (e.g. user taps the still-visible confirmation notification after auto-confirm),
 *   the stale FGS notification stays glued to the new lifecycle. [BUG-FGS-100]
 * - Promoting to foreground can be REFUSED by the system, and a refusal used to be an uncaught
 *   exception on the main thread — i.e. a fatal crash. It is now a return value. See below.
 *
 * ## Why the permission pre-check was never the protection it claimed to be
 * [DET-A-LOCATION-FGS-MUST-NOT-KILL-THE-APP-001]
 *
 * The original guard passed [hasLocationPermission] and, when false, fell back to the two-argument
 * `startForeground(id, notification)` believing that dropped the `location` type. It does not. From
 * Android 14 the two-argument overload promotes with **the types declared in the manifest**, and
 * every service that reaches this class declares `android:foregroundServiceType="location"`. So the
 * "safe" branch asked for exactly the type it was trying to avoid, and `ActiveServices
 * .validateForegroundServiceType` threw [SecurityException] from the untyped call site — measured in
 * production on `ForegroundServiceController.kt:43`, the `else` branch, twice in five seconds.
 *
 * Nor can a pre-check ever be sufficient: the system's own refusal message ends with *"and the app
 * must be in the eligible state/exemptions to access the foreground only permission"*. Eligibility
 * is process state at the instant of the IPC — no `checkSelfPermission` can predict it. Holding the
 * permission is necessary, not sufficient, which leaves catching as the only complete answer.
 *
 * So [hasLocationPermission] no longer pretends to be a guard. It selects the honest, explicit type
 * when we do hold the permission, and the refusal path below covers every case where the system says
 * no — whichever overload asked.
 *
 * Methods are synchronous main-thread operations — see audit section 4, C-8 — and
 * must NOT be wrapped in a coroutine `launch`.
 */
class ForegroundServiceController(
    private val service: Service,
    /**
     * The raw platform promotion, isolated as the single place that talks to the system.
     *
     * [ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST] is not a sentinel invented here — it is the
     * platform's own name for what the two-argument overload does, and naming it is the whole point:
     * the untyped call promotes with the MANIFEST's types, which for every service using this class
     * means `location`. Overridden in tests because no Robolectric shadow raises the refusals this
     * class exists to absorb. [DET-A-LOCATION-FGS-MUST-NOT-KILL-THE-APP-001]
     */
    private val startForeground: (id: Int, notification: Notification, type: Int) -> Unit =
        { id, notification, type ->
            if (type == ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST) {
                service.startForeground(id, notification)
            } else {
                service.startForeground(id, notification, type)
            }
        },
) {

    /**
     * Promote the service to foreground. Idempotent — safe to call on every onStartCommand.
     *
     * **Never throws.** A caller that gets anything other than [ForegroundPromotion.PROMOTED] still
     * owes the system a teardown: a service started with `startForegroundService()` that never
     * reaches the foreground is killed with `ForegroundServiceDidNotStartInTimeException` after
     * ~5 s, so stop it (`stopForegroundAndSelf`) on the refusal path rather than leaving it hanging.
     */
    fun promote(
        notificationId: Int,
        notification: Notification,
        hasLocationPermission: Boolean,
    ): ForegroundPromotion = try {
        val type = if (hasLocationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 14 logs "foreground service started by ACTIVITY_RECOGNITION exemption
            // can not have location access" — this is informational only; the service still
            // receives GPS fixes. The exemption type warning does not block any API. [FGS-003]
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            // NOT a type-free fallback — see the class doc. Below Q there is no typed overload, and
            // from API 34 this inherits the manifest's `location` type anyway, so without the runtime
            // permission it is EXPECTED to be refused. That refusal is the catch below, not a crash.
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        }
        startForeground(notificationId, notification, type)
        ForegroundPromotion.PROMOTED
    } catch (e: SecurityException) {
        // Android 14+: the declared type's prerequisite permission is missing or the process is not
        // in an eligible state for a foreground-only permission.
        PaparcarLogger.w(DIAG, "  ✗ FGS promote refused — location permission missing or not eligible", e)
        ForegroundPromotion.REFUSED_MISSING_LOCATION_PERMISSION
    } catch (e: IllegalStateException) {
        // Android 12+: ForegroundServiceStartNotAllowedException (an IllegalStateException) — the
        // start itself was not foreground-eligible, regardless of type.
        PaparcarLogger.w(DIAG, "  ✗ FGS promote refused — start not foreground-eligible", e)
        ForegroundPromotion.REFUSED_NOT_FOREGROUND_ELIGIBLE
    }

    /**
     * [FIX BUG-FGS-100: stopForeground(STOP_FOREGROUND_REMOVE) before stopSelf, every path]
     *
     * Tears the service down cleanly:
     *  1. Removes the FGS notification synchronously (so a re-entering `startForegroundService`
     *     from `ParkingConfirmationReceiver` cannot pick up the stale one).
     *  2. Schedules service destruction.
     *
     * UNCONDITIONAL — for services whose start commands cannot race each other (BT). A service
     * with multiple independent trigger intents must use the yielding overload below.
     */
    fun stopForegroundAndSelf() {
        removeForegroundNotification()
        service.stopSelf()
    }

    /**
     * [DET-INTAKE-001] Yielding teardown: honoured by the framework only when [startId] is the
     * MOST RECENT start command delivered to the service — a newer intent already in flight
     * vetoes the stop automatically. The argless overload killed the on-time GEOFENCE_EXIT of
     * field 2026-07-11 00:38: an AR TickOnly verdict called stopSelf() 10 ms after the real EXIT
     * intent was delivered, and onDestroy cancelled its handling mid-classification.
     *
     * The notification is removed only when the stop is accepted; a vetoed stop means a newer
     * command owns the (already re-promoted) foreground state.
     *
     * @return whether the framework accepted the stop.
     */
    fun stopForegroundAndSelf(startId: Int): Boolean {
        val stopping = service.stopSelfResult(startId)
        if (stopping) removeForegroundNotification()
        return stopping
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

    private companion object {
        const val DIAG = "PARKDIAG/FGS"
    }
}

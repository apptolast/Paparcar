package io.apptolast.paparcar.bluetooth

import android.content.Intent
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.detection.service.ForegroundServiceController
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.notification.ForegroundNotificationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * ForegroundService that owns the long-running scope for [BluetoothParkingDetector].
 *
 * **Why a dedicated Service?**
 * BT parking detection can take up to ~5 minutes: 30 s debounce + 60 s GPS sampling +
 * the time the user needs to walk ≥ 30 m. Android will kill a background process in that
 * window. A ForegroundService with `foregroundServiceType="location"` keeps the process
 * alive and grants continuous GPS access.
 *
 * **Lifecycle contract:**
 * - [ACTION_BT_DISCONNECTED] — started via `startForegroundService()` from
 *   [BluetoothConnectionReceiver]. Calls `fgs.promote()`, launches detection in
 *   [lifecycleScope], and tears down via `fgs.stopForegroundAndSelf()` when detection
 *   completes.
 * - [ACTION_BT_CONNECTED] — started via `startService()` (NOT foreground; work is instant).
 *   Cancels any active detection job and tears down. If the Service is already running
 *   as foreground (detection active), this `onStartCommand` is called on the same
 *   instance — the job is cancelled and the service stops cleanly.
 *
 * **Cancellation and the BT debounce:**
 * [BluetoothParkingDetector.detectParking] starts with a [kotlinx.coroutines.delay]. If BT
 * reconnects before the debounce expires, the [ACTION_BT_CONNECTED] handler cancels
 * [detectionJob] — [delay] is a cancellation point, so the coroutine aborts cooperatively
 * without any explicit "reconnect" flag inside the detector. [BT-REFACTOR-FGS-001]
 *
 * **START_NOT_STICKY:**
 * In-memory state (GPS parking fix, detection job) cannot survive a process kill.
 * A sticky restart with a null intent would find no vehicleId or fix coordinates and would
 * be meaningless — better to miss one detection event than to act on invalid state.
 *
 * **Notification ID:**
 * Uses [AppNotificationManager.BT_DETECTION_NOTIFICATION_ID] (1003) — distinct from
 * [AppNotificationManager.DETECTION_NOTIFICATION_ID] (1001) used by [CoordinatorDetectionService]
 * so both services can run their notifications independently without overwriting each other.
 *
 * **Refactor 2026-06-08 [BT-BUG-100..105 + BT-REFACTOR-200]:**
 *  - Reuses [ForegroundServiceController] so every teardown path goes through
 *    `stopForeground(STOP_FOREGROUND_REMOVE)` (no more leaked FGS notification).
 *  - `thisJob === detectionJob` guard prevents a superseded job from killing a
 *    replacement coordinator (`DETECT-SERVICE-RACE-001` ported from CoordinatorDetectionService).
 *  - Vehicle-name fetch moved INSIDE the detection job (no more side-launch race with
 *    `updateDetectionVehicle.notify` re-posting the notification after teardown).
 *  - Vehicle name resolved by `vehicleId` (the actually disconnected vehicle), not by
 *    `observeActiveVehicle` (which returns the user's *default* in multi-vehicle setups).
 *  - `onDestroy` safety net removes the FGS notification defensively.
 */
class BluetoothDetectionService : LifecycleService() {

    private val foregroundNotificationProvider: ForegroundNotificationProvider by inject()
    private val notificationPort: AppNotificationManager by inject()
    private val detector: BluetoothParkingDetector by inject()
    private val vehicleRepository: VehicleRepository by inject()
    private val authRepository: AuthRepository by inject()

    // [BT-REFACTOR-200] one controller per Service instance, centralises FGS lifecycle.
    private val fgs by lazy { ForegroundServiceController(this) }

    // Main-thread-only — lifecycleScope's default dispatcher is Main.immediate.
    @Volatile private var detectionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        PaparcarLogger.d(DIAG, "▶ Service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        PaparcarLogger.d(DIAG, "▶ onStartCommand action=${intent?.action}")

        when (intent?.action) {
            ACTION_BT_DISCONNECTED -> handleDisconnected(intent)
            ACTION_BT_CONNECTED -> handleConnected(intent)
            null -> {
                // START_NOT_STICKY means this should never be delivered, but guard defensively.
                PaparcarLogger.w(DIAG, "  ✗ null intent (unexpected restart) — stopForegroundAndSelf")
                fgs.stopForegroundAndSelf() // [FIX BT-BUG-100]
            }
        }

        return START_NOT_STICKY
    }

    private fun handleDisconnected(intent: Intent) {
        val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
        val vehicleId = intent.getStringExtra(EXTRA_VEHICLE_ID)
        if (deviceAddress.isNullOrBlank() || vehicleId.isNullOrBlank()) {
            PaparcarLogger.w(DIAG, "  ✗ BT_DISCONNECTED — missing extras (deviceAddress=$deviceAddress, vehicleId=$vehicleId), stop")
            // startForegroundCompat has NOT been called yet — plain stopSelf is fine, but
            // route through fgs for consistency. The internal stopForeground is a no-op
            // because we never promoted. [FIX BT-BUG-100]
            fgs.stopForegroundAndSelf()
            return
        }

        // Must promote before any suspending work — Android 8+ enforces the 5-second
        // window from startForegroundService(). [BUG-FGS-001]
        fgs.promote(
            notificationId = AppNotificationManager.BT_DETECTION_NOTIFICATION_ID,
            notification = foregroundNotificationProvider.buildDetectionNotification(),
            withLocationPermission = true,
        )
        PaparcarLogger.d(DIAG, "  ✓ startForeground done (notif ${AppNotificationManager.BT_DETECTION_NOTIFICATION_ID})")

        PaparcarLogger.d(DIAG, "  → BT_DISCONNECTED device=$deviceAddress vehicle=$vehicleId — launching detector")
        detectionJob?.cancel()
        detectionJob = lifecycleScope.launch {
            val thisJob = coroutineContext[Job]

            // [FIX BT-BUG-103 + BT-BUG-104] Resolve the vehicle name INSIDE the detection job
            // (same lifetime as detectionJob, no side-launch race) AND by the vehicleId that
            // actually disconnected (not by observeActiveVehicle, which would return the
            // user's default vehicle in multi-vehicle setups).
            runCatching {
                val userId = authRepository.getCurrentSession()?.userId
                val name = userId
                    ?.let { vehicleRepository.getVehicleById(it, vehicleId) }
                    ?.let { it.displayName(fallback = "").takeIf { n -> n.isNotBlank() } }
                if (name != null) {
                    notificationPort.updateDetectionVehicle(
                        name,
                        AppNotificationManager.BT_DETECTION_NOTIFICATION_ID,
                    )
                }
            }.onFailure { e ->
                PaparcarLogger.w(DIAG, "    ⚠ vehicle-name fetch failed: ${e.message}")
            }

            try {
                detector.detectParking(deviceAddress, vehicleId)
                PaparcarLogger.d(DIAG, "  ✓ detectParking returned normally")
            } catch (e: CancellationException) {
                PaparcarLogger.d(DIAG, "  ✗ detection cancelled (BT reconnect or destroy)")
                throw e
            } catch (e: Exception) {
                PaparcarLogger.e(DIAG, "  ✗ detection error", e)
                notificationPort.showDebug("BT detection error: ${e.message}")
            } finally {
                // [FIX BT-BUG-101] Race guard: skip teardown when this job has been
                // superseded by a newer detection job (e.g. a new BT_DISCONNECTED arrived
                // while we were finishing). Without this, the older job would tear down
                // the FGS that the replacement just promoted. Mirrors `DETECT-SERVICE-RACE-001`
                // from CoordinatorDetectionService.
                if (detectionJob === thisJob) {
                    PaparcarLogger.d(DIAG, "  ■ detection finished — stopForegroundAndSelf()")
                    fgs.stopForegroundAndSelf() // [FIX BT-BUG-100]
                } else {
                    PaparcarLogger.d(DIAG, "  ■ detection finished — superseded by newer job, skipping stop")
                }
            }
        }
    }

    private fun handleConnected(intent: Intent) {
        val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: ""
        PaparcarLogger.d(DIAG, "  → BT_CONNECTED device=$deviceAddress — cancelling detection, stopForegroundAndSelf()")
        detectionJob?.cancel()
        detectionJob = null
        fgs.stopForegroundAndSelf() // [FIX BT-BUG-100]
    }

    override fun onDestroy() {
        PaparcarLogger.d(DIAG, "■ Service onDestroy — cancelling detectionJob")
        detectionJob?.cancel()
        // [BT-REFACTOR-200] Defensive safety net (ports BUG-FGS-113 fix). Idempotent: a
        // redundant stopForeground after the notification is already gone is a documented
        // no-op on every Android version we ship to.
        runCatching { fgs.removeForegroundNotification() }
            .onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ onDestroy stopForeground failed: ${e.message}") }
        super.onDestroy()
        PaparcarLogger.d(DIAG, "■ Service onDestroy DONE")
    }

    companion object {
        const val ACTION_BT_DISCONNECTED = "io.apptolast.paparcar.ACTION_BT_DISCONNECTED"
        const val ACTION_BT_CONNECTED = "io.apptolast.paparcar.ACTION_BT_CONNECTED"
        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        const val EXTRA_VEHICLE_ID = "extra_vehicle_id"
        private const val DIAG = "BTDIAG/Service"
    }
}

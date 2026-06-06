package io.apptolast.paparcar.bluetooth

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.notification.ForegroundNotificationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
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
 *   [BluetoothConnectionReceiver]. Calls `startForeground()`, launches detection in
 *   [lifecycleScope], and calls `stopSelf()` when detection completes (success or failure).
 * - [ACTION_BT_CONNECTED] — started via `startService()` (NOT foreground; work is instant).
 *   Cancels any active detection job and calls `stopSelf()`. If the Service is already
 *   running as foreground (detection active), this `onStartCommand` is called on the same
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
 * [AppNotificationManager.DETECTION_NOTIFICATION_ID] (1001) used by [ParkingDetectionService]
 * so both services can run their notifications independently without overwriting each other.
 */
class BluetoothDetectionService : LifecycleService() {

    private val foregroundNotificationProvider: ForegroundNotificationProvider by inject()
    private val notificationPort: AppNotificationManager by inject()
    private val detector: BluetoothParkingDetector by inject()
    private val vehicleRepository: VehicleRepository by inject()

    private var detectionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        PaparcarLogger.d(DIAG, "▶ Service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        PaparcarLogger.d(DIAG, "▶ onStartCommand action=${intent?.action}")

        when (intent?.action) {
            ACTION_BT_DISCONNECTED -> {
                val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                val vehicleId = intent.getStringExtra(EXTRA_VEHICLE_ID)
                if (deviceAddress == null || vehicleId == null) {
                    PaparcarLogger.w(DIAG, "  ✗ BT_DISCONNECTED — missing extras, stopSelf")
                    stopSelf()
                    return START_NOT_STICKY
                }

                // Must call startForeground() before any suspending work — Android 8+ enforces
                // the 5-second window from startForegroundService(). [BUG-FGS-001]
                startForegroundCompat()

                lifecycleScope.launch {
                    val vehicleName = vehicleRepository.observeActiveVehicle().firstOrNull()
                        ?.displayName(fallback = "")
                        ?.takeIf { it.isNotBlank() }
                    if (vehicleName != null) {
                        notificationPort.updateDetectionVehicle(vehicleName, AppNotificationManager.BT_DETECTION_NOTIFICATION_ID)
                    }
                }

                PaparcarLogger.d(DIAG, "  → BT_DISCONNECTED device=$deviceAddress vehicle=$vehicleId — launching detector")
                detectionJob?.cancel()
                detectionJob = lifecycleScope.launch {
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
                        PaparcarLogger.d(DIAG, "  ■ detection finished — stopSelf()")
                        stopSelf()
                    }
                }
            }

            ACTION_BT_CONNECTED -> {
                val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: ""
                PaparcarLogger.d(DIAG, "  → BT_CONNECTED device=$deviceAddress — cancelling detection, stopSelf()")
                detectionJob?.cancel()
                detectionJob = null
                stopSelf()
            }

            null -> {
                // START_NOT_STICKY means this should never be delivered, but guard defensively.
                PaparcarLogger.w(DIAG, "  ✗ null intent (unexpected restart) — stopSelf")
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        PaparcarLogger.d(DIAG, "■ Service onDestroy — cancelling detectionJob")
        detectionJob?.cancel()
        super.onDestroy()
        PaparcarLogger.d(DIAG, "■ Service onDestroy DONE")
    }

    private fun startForegroundCompat() {
        val notification = foregroundNotificationProvider.buildDetectionNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AppNotificationManager.BT_DETECTION_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(AppNotificationManager.BT_DETECTION_NOTIFICATION_ID, notification)
        }
        PaparcarLogger.d(DIAG, "  ✓ startForeground done (notif ${AppNotificationManager.BT_DETECTION_NOTIFICATION_ID})")
    }

    companion object {
        const val ACTION_BT_DISCONNECTED = "io.apptolast.paparcar.ACTION_BT_DISCONNECTED"
        const val ACTION_BT_CONNECTED = "io.apptolast.paparcar.ACTION_BT_CONNECTED"
        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        const val EXTRA_VEHICLE_ID = "extra_vehicle_id"
        private const val DIAG = "BTDIAG/Service"
    }
}

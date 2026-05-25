package io.apptolast.paparcar.detection.service

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.domain.coordinator.ParkingDetectionCoordinator
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.notification.ForegroundNotificationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class ParkingDetectionService : LifecycleService() {

    private val parkingDetectionCoordinator: ParkingDetectionCoordinator by inject()
    private val observeAdaptiveLocation: ObserveAdaptiveLocationUseCase by inject()
    private val foregroundNotificationProvider: ForegroundNotificationProvider by inject()
    private val notificationPort: AppNotificationManager by inject()
    private var detectionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        PaparcarLogger.d(DIAG, "▶ Service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        PaparcarLogger.d(DIAG, "▶ onStartCommand action=${intent?.action} flags=$flags startId=$startId")

        // null intent = START_STICKY restart after process kill. Treat as START_TRACKING but guard
        // permissions first — the user may have revoked location access while we were dead. [§9]
        val action = intent?.action ?: ACTION_START_TRACKING

        when (action) {
            ACTION_START_TRACKING -> {
                if (!hasRequiredPermissions()) {
                    PaparcarLogger.w(DIAG, "  ✗ START_TRACKING aborted — missing location permission")
                    notificationPort.showPermissionRevoked()
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (detectionJob?.isActive == true && parkingDetectionCoordinator.hasDetectedMovement) {
                    PaparcarLogger.d(DIAG, "  ↻ START_TRACKING ignored — job active + hasDetectedMovement=true")
                    return START_STICKY
                }
                PaparcarLogger.d(DIAG, "  → START_TRACKING — (re)starting detection")
                detectionJob?.cancel()
                detectionJob = null
                val notification = foregroundNotificationProvider.buildDetectionNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        AppNotificationManager.DETECTION_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                    )
                } else {
                    startForeground(AppNotificationManager.DETECTION_NOTIFICATION_ID, notification)
                }
                PaparcarLogger.d(DIAG, "  ✓ startForeground done (notif ${AppNotificationManager.DETECTION_NOTIFICATION_ID})")
                startParkingDetection()
            }
            ACTION_VEHICLE_EXIT -> {
                PaparcarLogger.d(DIAG, "  → VEHICLE_EXIT delivered to coordinator")
                parkingDetectionCoordinator.onVehicleExit()
            }
            ACTION_PARKING_CONFIRMED -> {
                PaparcarLogger.d(DIAG, "  → PARKING_CONFIRMED delivered to coordinator")
                parkingDetectionCoordinator.onUserConfirmedParking()
            }
            ACTION_PARKING_DENIED -> {
                PaparcarLogger.d(DIAG, "  → PARKING_DENIED delivered to coordinator")
                parkingDetectionCoordinator.onUserDeniedParking()
            }
            ACTION_STOP_TRACKING -> {
                PaparcarLogger.d(DIAG, "  → STOP_TRACKING — stopSelf()")
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startParkingDetection() {
        PaparcarLogger.d(DIAG, "  ▶ startParkingDetection — launching coordinator")
        detectionJob = lifecycleScope.launch {
            try {
                PaparcarLogger.d(DIAG, "    ▶ detection coroutine entered, invoking coordinator")
                parkingDetectionCoordinator(observeAdaptiveLocation())
                PaparcarLogger.d(DIAG, "    ✓ coordinator returned NORMALLY")
            } catch (e: CancellationException) {
                PaparcarLogger.d(DIAG, "    ✗ detection cancelled: ${e.message}")
                throw e
            } catch (e: Exception) {
                PaparcarLogger.e(DIAG, "    ✗ detection error", e)
                notificationPort.showDebug("Detection error: ${e.message}")
            } finally {
                PaparcarLogger.d(DIAG, "    ■ finally → calling stopSelf()")
                stopSelf()
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val fineLoc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val bgLoc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else true
        return fineLoc && bgLoc
    }

    override fun onDestroy() {
        PaparcarLogger.d(DIAG, "■ Service onDestroy — cancelling detectionJob")
        detectionJob?.cancel()
        super.onDestroy()
        PaparcarLogger.d(DIAG, "■ Service onDestroy DONE")
    }

    companion object {
        const val ACTION_START_TRACKING = "io.apptolast.paparcar.ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "io.apptolast.paparcar.ACTION_STOP_TRACKING"
        const val ACTION_VEHICLE_EXIT = "io.apptolast.paparcar.ACTION_VEHICLE_EXIT"
        const val ACTION_PARKING_CONFIRMED = "io.apptolast.paparcar.ACTION_PARKING_CONFIRMED"
        const val ACTION_PARKING_DENIED = "io.apptolast.paparcar.ACTION_PARKING_DENIED"
        private const val DIAG = "PARKDIAG/Service"
    }
}
package io.apptolast.paparcar.detection.service

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import io.apptolast.paparcar.detection.activityLabel
import io.apptolast.paparcar.detection.transitionLabel
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.domain.coordinator.ParkingDetectionCoordinator
import io.apptolast.paparcar.domain.detection.TransitionAction
import io.apptolast.paparcar.domain.usecase.detection.HandleVehicleTransitionUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.notification.ForegroundNotificationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class ParkingDetectionService : LifecycleService() {

    private val parkingDetectionCoordinator: ParkingDetectionCoordinator by inject()
    private val observeAdaptiveLocation: ObserveAdaptiveLocationUseCase by inject()
    private val foregroundNotificationProvider: ForegroundNotificationProvider by inject()
    private val notificationPort: AppNotificationManager by inject()
    private val vehicleRepository: VehicleRepository by inject()
    private val handleVehicleTransition: HandleVehicleTransitionUseCase by inject()

    @Volatile private var detectionJob: Job? = null

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
                if (!guardPermissions("START_TRACKING")) return START_NOT_STICKY
                if (detectionJob?.isActive == true && parkingDetectionCoordinator.hasDetectedMovement) {
                    PaparcarLogger.d(DIAG, "  ↻ START_TRACKING ignored — job active + hasDetectedMovement=true")
                    return START_STICKY
                }
                PaparcarLogger.d(DIAG, "  → START_TRACKING — (re)starting detection")
                detectionJob?.cancel()
                detectionJob = null
                startForegroundCompat()
                startParkingDetection()
            }
            ACTION_VEHICLE_TRANSITION -> {
                // Delivered directly from Play Services via PendingIntent.getForegroundService().
                // startForeground() must be called first — Android 8+ enforces a 5 s window. [BUG-FGS-001]
                startForegroundCompat()
                if (!guardPermissions("VEHICLE_TRANSITION")) return START_NOT_STICKY
                processTransitionIntent(intent!!)
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

    private fun processTransitionIntent(intent: Intent) {
        val result = ActivityTransitionResult.extractResult(intent) ?: run {
            PaparcarLogger.w(DIAG, "  ✗ VEHICLE_TRANSITION — no ActivityTransitionResult in intent")
            stopIfIdle("no-transition-result")
            return
        }

        result.transitionEvents.forEach { event ->
            PaparcarLogger.d(DIAG, "  → VEHICLE_TRANSITION ${activityLabel(event.activityType)} ${transitionLabel(event.transitionType)}")

            if (event.activityType != DetectedActivity.IN_VEHICLE) return@forEach

            val isEnter = event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
            val epochMs = System.currentTimeMillis() -
                SystemClock.elapsedRealtime() +
                event.elapsedRealTimeNanos / 1_000_000L

            lifecycleScope.launch {
                when (handleVehicleTransition(isEnter = isEnter, epochMs = epochMs)) {
                    TransitionAction.StartCoordinatorDetection -> {
                        if (!guardPermissions("IN_VEHICLE_ENTER")) return@launch
                        PaparcarLogger.d(DIAG, "  → IN_VEHICLE_ENTER — starting Coordinator")
                        detectionJob?.cancel()
                        detectionJob = null
                        startParkingDetection()
                    }
                    TransitionAction.StopIfIdle -> stopIfIdle("transition-action")
                    TransitionAction.Ignore -> Unit
                }
            }
        }
    }

    /** Stops the service only when no detection job is currently running. */
    private fun stopIfIdle(reason: String) {
        if (detectionJob?.isActive == true) return
        PaparcarLogger.d(DIAG, "  stopIfIdle($reason) → stopSelf()")
        stopSelf()
    }

    private fun startForegroundCompat() {
        val notification = foregroundNotificationProvider.buildDetectionNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 14 logs "foreground service started by ACTIVITY_RECOGNITION exemption
            // can not have location access" — this is informational only; the service still
            // receives GPS fixes. The exemption type warning does not block any API. [FGS-003]
            startForeground(
                AppNotificationManager.DETECTION_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(AppNotificationManager.DETECTION_NOTIFICATION_ID, notification)
        }
        PaparcarLogger.d(DIAG, "  ✓ startForeground done (notif ${AppNotificationManager.DETECTION_NOTIFICATION_ID})")
    }

    private fun startParkingDetection() {
        PaparcarLogger.d(DIAG, "  ▶ startParkingDetection — launching coordinator")
        lifecycleScope.launch {
            val vehicleName = vehicleRepository.observeActiveVehicle().firstOrNull()
                ?.let { listOfNotNull(it.brand, it.model).joinToString(" ").ifBlank { null } }
            if (vehicleName != null) {
                notificationPort.updateDetectionVehicle(vehicleName, AppNotificationManager.DETECTION_NOTIFICATION_ID)
            }
        }
        detectionJob = lifecycleScope.launch {
            val thisJob = coroutineContext[Job]
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
                // Skip stopSelf when this job has been superseded by a newer detection job
                // (START_TRACKING / IN_VEHICLE_ENTER replacement). Calling stopSelf here would
                // destroy the service after the replacement coordinator was just launched,
                // killing it via onDestroy. [DETECT-SERVICE-RACE-001]
                if (detectionJob === thisJob) {
                    PaparcarLogger.d(DIAG, "    ■ finally → calling stopSelf()")
                    stopSelf()
                } else {
                    PaparcarLogger.d(DIAG, "    ■ finally → superseded by newer job, skipping stopSelf()")
                }
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

    /**
     * Centralised location-permission gate. Returns `false` (and stops the service) when the
     * user has revoked location access since the service was first scheduled — covers every
     * entry path: explicit START, IN_VEHICLE PendingIntent delivery, and Activity Recognition
     * fallback. Caller should `return START_NOT_STICKY` so the framework does not restart us.
     */
    private fun guardPermissions(actionLabel: String): Boolean {
        if (hasRequiredPermissions()) return true
        PaparcarLogger.w(DIAG, "  ✗ $actionLabel aborted — missing location permission")
        notificationPort.showPermissionRevoked()
        stopSelf()
        return false
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
        const val ACTION_VEHICLE_TRANSITION = "io.apptolast.paparcar.ACTION_VEHICLE_TRANSITION"
        const val ACTION_PARKING_CONFIRMED = "io.apptolast.paparcar.ACTION_PARKING_CONFIRMED"
        const val ACTION_PARKING_DENIED = "io.apptolast.paparcar.ACTION_PARKING_DENIED"
        private const val DIAG = "PARKDIAG/Service"
    }
}

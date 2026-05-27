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
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.domain.coordinator.ParkingDetectionCoordinator
import io.apptolast.paparcar.domain.detection.ParkingStrategyResolver
import io.apptolast.paparcar.domain.service.DepartureEventBus
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
    private val departureEventBus: DepartureEventBus by inject()
    private val strategyResolver: ParkingStrategyResolver by inject()
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
                handleVehicleTransition(intent!!)
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

    private fun handleVehicleTransition(intent: Intent) {
        val result = ActivityTransitionResult.extractResult(intent) ?: run {
            PaparcarLogger.w(DIAG, "  ✗ VEHICLE_TRANSITION — no ActivityTransitionResult in intent")
            if (detectionJob?.isActive != true) stopSelf()
            return
        }

        result.transitionEvents.forEach { event ->
            PaparcarLogger.d(DIAG, "  → VEHICLE_TRANSITION ${activityLabel(event.activityType)} ${transitionLabel(event.transitionType)}")

            when {
                event.activityType == DetectedActivity.IN_VEHICLE &&
                event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                    val eventEpochMs = System.currentTimeMillis() -
                        SystemClock.elapsedRealtime() +
                        event.elapsedRealTimeNanos / 1_000_000L
                    departureEventBus.onVehicleEntered(eventEpochMs)

                    lifecycleScope.launch {
                        if (strategyResolver.shouldUseCoordinator()) {
                            if (!guardPermissions("IN_VEHICLE_ENTER")) return@launch
                            if (detectionJob?.isActive != true || !parkingDetectionCoordinator.hasDetectedMovement) {
                                PaparcarLogger.d(DIAG, "  → IN_VEHICLE_ENTER — starting Coordinator (cancelling stale job if any)")
                                detectionJob?.cancel()
                                detectionJob = null
                                startParkingDetection()
                            } else {
                                PaparcarLogger.d(DIAG, "  ↻ IN_VEHICLE_ENTER — Coordinator already active + hasDetectedMovement=true")
                            }
                        } else {
                            PaparcarLogger.d(DIAG, "  → IN_VEHICLE_ENTER — BT strategy active, Coordinator not started")
                            if (detectionJob?.isActive != true) stopSelf()
                        }
                    }
                }

                event.activityType == DetectedActivity.IN_VEHICLE &&
                event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                    parkingDetectionCoordinator.onVehicleExit()
                    if (detectionJob?.isActive != true) stopSelf()
                }
            }
        }
    }

    private fun startForegroundCompat() {
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

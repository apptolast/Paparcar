package io.apptolast.paparcar.detection.service

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.apptolast.paparcar.detection.activityLabel
import io.apptolast.paparcar.detection.transitionLabel
import io.apptolast.paparcar.domain.coordinator.ParkingDetectionCoordinator
import io.apptolast.paparcar.domain.detection.TransitionAction
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.usecase.detection.HandleVehicleTransitionUseCase
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.domain.usecase.parking.RevertParkingUseCase
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
    private val revertParking: RevertParkingUseCase by inject() // [REFACTOR-300]

    // [REFACTOR: extract FGS lifecycle into ForegroundServiceController]
    private val fgs by lazy { ForegroundServiceController(this) }

    // Main-thread-only — lifecycleScope's default dispatcher is Main.immediate. @Volatile is
    // belt-and-braces against potential cross-thread reads from diagnostic code. [audit C-2]
    @Volatile private var detectionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        PaparcarLogger.d(DIAG, "▶ Service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        PaparcarLogger.d(DIAG, "▶ onStartCommand action=${intent?.action} flags=$flags startId=$startId")

        // Promote to foreground immediately — Android 8+ enforces a 5 s window for any
        // startForegroundService() call, including those from notification action receivers.
        // Use FOREGROUND_SERVICE_TYPE_LOCATION only when we actually hold location permission:
        // on Android 14+ calling startForeground() with type LOCATION without the runtime
        // permission throws SecurityException. [BUG-FGS-001a]
        val hasPerms = hasRequiredPermissions()
        fgs.promote(
            notificationId = AppNotificationManager.DETECTION_NOTIFICATION_ID,
            notification = foregroundNotificationProvider.buildDetectionNotification(),
            withLocationPermission = hasPerms,
        )
        PaparcarLogger.d(DIAG, "  ✓ startForeground done (locationPermission=$hasPerms)")
        updateCrashlyticsContext(intent?.action, hasPerms)

        // null intent = START_STICKY restart after process kill. Treat as START_TRACKING but guard
        // permissions first — the user may have revoked location access while we were dead. [§9]
        val action = intent?.action ?: ACTION_START_TRACKING

        when (action) {
            ACTION_START_TRACKING -> handleStartTracking()
            ACTION_VEHICLE_TRANSITION -> {
                if (!guardPermissions("VEHICLE_TRANSITION")) return START_NOT_STICKY
                processTransitionIntent(intent!!)
            }
            ACTION_PARKING_CONFIRMED -> handleUserConfirmed()
            ACTION_PARKING_DENIED -> handleUserDenied()
            ACTION_PARKING_ACK -> handlePostSaveAck() // [REFACTOR-300]
            ACTION_PARKING_REVERT -> handlePostSaveRevert(intent?.getStringExtra(EXTRA_PARKING_ID)) // [REFACTOR-300]
            ACTION_STOP_TRACKING -> {
                PaparcarLogger.d(DIAG, "  → STOP_TRACKING — stopForegroundAndSelf()")
                fgs.stopForegroundAndSelf() // [FIX BUG-FGS-100: cleanup FGS notif before stop]
            }
        }

        return START_STICKY
    }

    private fun                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       handleStartTracking() {
        if (!guardPermissions("START_TRACKING")) return
        // [FIX BUG-SERVICE-109: stop relying on stale hasDetectedMovement across sessions]
        // Active-job check alone is the right idempotency guard — a session that has already
        // started owns the work. hasDetectedMovement only makes sense in-session.
        if (detectionJob?.isActive == true) {
            PaparcarLogger.d(DIAG, "  ↻ START_TRACKING ignored — detectionJob already active")
            return
        }
        PaparcarLogger.d(DIAG, "  → START_TRACKING — (re)starting detection")
        cancelDetectionJob()
        startParkingDetection()
    }

    private fun handleUserConfirmed() {
        PaparcarLogger.d(DIAG, "  → PARKING_CONFIRMED delivered to coordinator")
        parkingDetectionCoordinator.onUserConfirmedParking()
        // [FIX BUG-FGS-103: a confirm action that arrives with no active job is a stale
        //  tap (auto-confirm already wrote the spot). The notification is dismissed by
        //  the coordinator's onUserConfirmedParking; we must also tear down the FGS so
        //  the service does not stay orphaned with its detection notification.]
        if (detectionJob?.isActive != true) {
            PaparcarLogger.d(DIAG, "    ↳ no active job — stopForegroundAndSelf()")
            fgs.stopForegroundAndSelf()
        }
    }

    private fun handleUserDenied() {
        PaparcarLogger.d(DIAG, "  → PARKING_DENIED delivered to coordinator")
        parkingDetectionCoordinator.onUserDeniedParking()
        // [FIX BUG-FGS-103: same stale-tap handling as confirm]
        if (detectionJob?.isActive != true) {
            PaparcarLogger.d(DIAG, "    ↳ no active job — stopForegroundAndSelf()")
            fgs.stopForegroundAndSelf()
        }
    }

    /**
     * [REFACTOR-300] "Sí, confirmar" on the post-save notification.
     * The save already happened; nothing to do except dismiss the notif and tear down.
     */
    private fun handlePostSaveAck() {
        PaparcarLogger.d(DIAG, "  → PARKING_ACK — user acknowledged auto-confirm")
        notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
        // Detection job is almost certainly idle (auto-confirm ends the session), but guard
        // anyway: if it's somehow active, leave it running.
        if (detectionJob?.isActive != true) {
            fgs.stopForegroundAndSelf()
        }
    }

    /**
     * [REFACTOR-300] "No, cancelar" on the post-save notification.
     * Runs the [RevertParkingUseCase] for the parkingId carried in the intent extras.
     * The use case dismisses the notification and removes the geofence + clears active session;
     * we tear down the FGS after it returns.
     */
    private fun handlePostSaveRevert(parkingId: String?) {
        if (parkingId.isNullOrBlank()) {
            PaparcarLogger.w(DIAG, "  ✗ PARKING_REVERT received without parkingId — dismissing notif and stopping")
            notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
            fgs.stopForegroundAndSelf()
            return
        }
        PaparcarLogger.d(DIAG, "  → PARKING_REVERT — running RevertParkingUseCase(parkingId=$parkingId)")
        lifecycleScope.launch {
            runCatching { revertParking(parkingId) }
                .onFailure { e -> PaparcarLogger.e(DIAG, "    ✗ revert failed", e) }
            // Whether revert succeeded or failed (best-effort), tear down so the FGS notif
            // does not stay glued on. The user can retry from the history screen if needed.
            fgs.stopForegroundAndSelf()
        }
    }

    private fun processTransitionIntent(intent: Intent) {
        val result = ActivityTransitionResult.extractResult(intent) ?: run {
            PaparcarLogger.w(DIAG, "  ✗ VEHICLE_TRANSITION — no ActivityTransitionResult in intent")
            stopIfIdle("no-transition-result")
            return
        }

        // [FIX BUG-DET-102: collect IN_VEHICLE events and process sequentially under a single
        //  launch. Per-event launches let an EXIT race ahead of an ENTER in the same batch and
        //  call stopSelf() before the ENTER could start the coordinator.]
        val vehicleEvents: List<ActivityTransitionEvent> = result.transitionEvents
            .filter { it.activityType == DetectedActivity.IN_VEHICLE }
        if (vehicleEvents.isEmpty()) {
            stopIfIdle("no-vehicle-events")
            return
        }

        lifecycleScope.launch {
            vehicleEvents.forEach { event ->
                PaparcarLogger.d(
                    DIAG,
                    "  → VEHICLE_TRANSITION ${activityLabel(event.activityType)} ${transitionLabel(event.transitionType)}"
                )
                val isEnter = event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
                val epochMs = System.currentTimeMillis() -
                    SystemClock.elapsedRealtime() +
                    event.elapsedRealTimeNanos / 1_000_000L

                when (handleVehicleTransition(isEnter = isEnter, epochMs = epochMs)) {
                    TransitionAction.StartCoordinatorDetection -> {
                        if (!guardPermissions("IN_VEHICLE_ENTER")) return@launch
                        PaparcarLogger.d(DIAG, "  → IN_VEHICLE_ENTER — starting Coordinator")
                        cancelDetectionJob()
                        startParkingDetection()
                    }
                    TransitionAction.StopIfIdle -> stopIfIdle("transition-action")
                    TransitionAction.Ignore -> Unit
                }
            }
        }
    }

    /** Cancels the in-flight detection job (if any) and nulls the slot. Main-thread only. */
    private fun cancelDetectionJob() {
        detectionJob?.cancel()
        detectionJob = null
    }

    /** Stops the service only when no detection job is currently running. */
    private fun stopIfIdle(reason: String) {
        if (detectionJob?.isActive == true) return
        PaparcarLogger.d(DIAG, "  stopIfIdle($reason) → stopForegroundAndSelf()")
        fgs.stopForegroundAndSelf() // [FIX BUG-FGS-100]
    }

    private fun startParkingDetection() {
        PaparcarLogger.d(DIAG, "  ▶ startParkingDetection — launching coordinator")
        detectionJob = lifecycleScope.launch {
            val thisJob = coroutineContext[Job]

            // [FIX BUG-SERVICE-108: pull vehicle name inside the detection job rather than in a
            //  parallel lifecycleScope.launch — same lifetime as the coordinator, no leak across
            //  flapping START_TRACKING events.]
            runCatching {
                val name = vehicleRepository.observeActiveVehicle().firstOrNull()
                    ?.let { it.displayName(fallback = "").takeIf { n -> n.isNotBlank() } }
                if (name != null) {
                    notificationPort.updateDetectionVehicle(
                        name,
                        AppNotificationManager.DETECTION_NOTIFICATION_ID,
                    )
                }
            }.onFailure { e ->
                PaparcarLogger.w(DIAG, "    ⚠ vehicle-name fetch failed: ${e.message}")
            }

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
                    PaparcarLogger.d(DIAG, "    ■ finally → stopForegroundAndSelf()")
                    fgs.stopForegroundAndSelf() // [FIX BUG-FGS-100]
                } else {
                    PaparcarLogger.d(DIAG, "    ■ finally → superseded by newer job, skipping stop")
                }
            }
        }
    }

    private fun updateCrashlyticsContext(intentAction: String?, hasLocationPerm: Boolean) {
        // [FIX BUG-SERVICE-110: never swallow throwables silently]
        runCatching {
            FirebaseCrashlytics.getInstance().run {
                setCustomKey("det_action", intentAction ?: "null→START_TRACKING")
                setCustomKey("det_job_active", detectionJob?.isActive == true)
                setCustomKey("det_has_movement", parkingDetectionCoordinator.hasDetectedMovement)
                setCustomKey("det_location_perm", hasLocationPerm)
            }
        }.onFailure { e ->
            PaparcarLogger.w(DIAG, "  ⚠ Crashlytics custom-keys update failed: ${e.message}")
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
     * Centralised location-permission gate. Returns `false` (and stops the service cleanly)
     * when the user has revoked location access since the service was first scheduled — covers
     * every entry path: explicit START, IN_VEHICLE PendingIntent delivery, and Activity
     * Recognition fallback. Caller should `return` so the framework does not restart us.
     *
     * [FIX BUG-FGS-104: route through stopForegroundAndSelf so the FGS detection notification
     *  is removed before the service is destroyed; the separate permission-revoked notification
     *  (different ID) is unaffected.]
     */
    private fun guardPermissions(actionLabel: String): Boolean {
        if (hasRequiredPermissions()) return true
        PaparcarLogger.w(DIAG, "  ✗ $actionLabel aborted — missing location permission")
        notificationPort.showPermissionRevoked()
        fgs.stopForegroundAndSelf()
        return false
    }

    override fun onDestroy() {
        PaparcarLogger.d(DIAG, "■ Service onDestroy — cancelling detectionJob")
        detectionJob?.cancel()
        // [FIX BUG-FGS-113: defensive safety net. Every primary teardown path is supposed
        //  to call fgs.stopForegroundAndSelf(), but if any future code path reaches onDestroy
        //  without first removing the FGS notification, do it now. Idempotent — calling
        //  stopForeground after the notification is already gone is a no-op on every Android
        //  version we ship to.]
        runCatching { fgs.removeForegroundNotification() }
            .onFailure { e -> PaparcarLogger.w(DIAG, "  ⚠ onDestroy stopForeground failed: ${e.message}") }
        super.onDestroy()
        PaparcarLogger.d(DIAG, "■ Service onDestroy DONE")
    }

    companion object {
        const val ACTION_START_TRACKING = "io.apptolast.paparcar.ACTION_START_TRACKING"
        const val ACTION_STOP_TRACKING = "io.apptolast.paparcar.ACTION_STOP_TRACKING"
        const val ACTION_VEHICLE_TRANSITION = "io.apptolast.paparcar.ACTION_VEHICLE_TRANSITION"
        // Pre-save prompt (state A): user is being asked whether they parked.
        const val ACTION_PARKING_CONFIRMED = "io.apptolast.paparcar.ACTION_PARKING_CONFIRMED"
        const val ACTION_PARKING_DENIED = "io.apptolast.paparcar.ACTION_PARKING_DENIED"
        // [REFACTOR-300] Post-save confirm (state B): the parking has been saved; user is
        // acknowledging or reverting.
        const val ACTION_PARKING_ACK = "io.apptolast.paparcar.ACTION_PARKING_ACK"
        const val ACTION_PARKING_REVERT = "io.apptolast.paparcar.ACTION_PARKING_REVERT"
        const val EXTRA_PARKING_ID = "io.apptolast.paparcar.EXTRA_PARKING_ID"
        private const val DIAG = "PARKDIAG/Service"
    }
}

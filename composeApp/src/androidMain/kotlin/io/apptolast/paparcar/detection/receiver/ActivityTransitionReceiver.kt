@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.detection.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import io.apptolast.paparcar.BuildConfig
import io.apptolast.paparcar.detection.service.ParkingDetectionService
import io.apptolast.paparcar.domain.coordinator.ParkingDetectionCoordinator
import io.apptolast.paparcar.domain.detection.ParkingStrategyResolver
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ActivityTransitionReceiver : BroadcastReceiver(), KoinComponent {

    private val notificationPort: AppNotificationManager by inject()
    private val departureEventBus: DepartureEventBus by inject()
    private val coordinator: ParkingDetectionCoordinator by inject()
    private val strategyResolver: ParkingStrategyResolver by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {

        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return

        // Epoch-ms of the first IN_VEHICLE_ENTER event (if any). Captured synchronously so
        // the departure event bus is updated before the async strategy check runs.
        var vehicleEnterEventEpochMs: Long? = null

        result.transitionEvents.forEach { event ->
            val activityType = toActivityString(event.activityType)
            val transitionType = toTransitionTypeString(event.transitionType)

            if (BuildConfig.DEBUG) {
                notificationPort.showDebug("Transición real: $activityType ($transitionType)")
            }

            when {
                event.activityType == DetectedActivity.STILL &&
                        event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                    // Device confirmed stationary — signals the parking confidence scorer
                    // to apply the STILL bonus (+0.10) in the slow path.
                    // Called directly on the coordinator (singleton) so the signal reaches
                    // the active detection session without going through the service.
                    // Safe to call even when BT strategy owns the session (no-op on idle coordinator).
                    coordinator.onStillDetected()
                }

                event.activityType == DetectedActivity.IN_VEHICLE &&
                        event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                    // Use the event's own elapsed-realtime timestamp (not delivery time)
                    // because the Transitions API can batch events up to ~5 min late.
                    val eventEpochMs = System.currentTimeMillis() -
                        SystemClock.elapsedRealtime() +
                        event.elapsedRealTimeNanos / 1_000_000L
                    departureEventBus.onVehicleEntered(eventEpochMs)
                    vehicleEnterEventEpochMs = eventEpochMs
                }

                event.activityType == DetectedActivity.IN_VEHICLE &&
                        event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                    startDrivingService(context, ParkingDetectionService.ACTION_VEHICLE_EXIT)
                }
            }
        }

        // Start the Coordinator service only when BT strategy is not the active owner.
        // goAsync() extends the broadcast timeout for the async strategy check.
        if (vehicleEnterEventEpochMs != null) {
            val pending = goAsync()
            scope.launch {
                try {
                    if (strategyResolver.shouldUseCoordinator()) {
                        startDrivingService(context, ParkingDetectionService.ACTION_START_TRACKING)
                    } else {
                        PaparcarLogger.d(TAG, "BT strategy active — Coordinator not started for IN_VEHICLE_ENTER")
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }

    private fun startDrivingService(context: Context, serviceAction: String?) {
        val serviceIntent = Intent(context, ParkingDetectionService::class.java).apply {
            action = serviceAction
        }
        context.startForegroundService(serviceIntent)
    }

    private fun toActivityString(activity: Int): String = when (activity) {
        DetectedActivity.STILL -> "STILL"
        DetectedActivity.WALKING -> "WALKING"
        DetectedActivity.RUNNING -> "RUNNING"
        DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
        else -> "UNKNOWN"
    }

    private fun toTransitionTypeString(transitionType: Int): String = when (transitionType) {
        ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "ENTER"
        ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "EXIT"
        else -> "UNKNOWN"
    }

    companion object {
        const val REQUEST_CODE = 101
        private const val TAG = "ActivityTransitionReceiver"
    }
}

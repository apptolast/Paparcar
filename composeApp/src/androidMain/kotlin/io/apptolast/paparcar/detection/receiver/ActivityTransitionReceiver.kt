@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.detection.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import io.apptolast.paparcar.BuildConfig
import io.apptolast.paparcar.detection.service.DrivingTrackingService
import io.apptolast.paparcar.detection.worker.VehicleSpeedCheckWorker
import io.apptolast.paparcar.domain.notification.NotificationPort
import io.apptolast.paparcar.domain.service.DepartureEventBus
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ActivityTransitionReceiver : BroadcastReceiver(), KoinComponent {

    private val notificationPort: NotificationPort by inject()
    private val departureEventBus: DepartureEventBus by inject()

    override fun onReceive(context: Context, intent: Intent) {

        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent)

            result?.transitionEvents?.forEach { event ->
                val activityType = toActivityString(event.activityType)
                val transitionType = toTransitionTypeString(event.transitionType)

                if (BuildConfig.DEBUG) {
                    notificationPort.showDebug("Transición real: $activityType ($transitionType)")
                }

                when {
                    event.activityType == DetectedActivity.STILL &&
                            event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                        // User stopped being still — might be about to enter a vehicle.
                        // Schedule a speed check as fallback in case IN_VEHICLE_ENTER is
                        // delayed or never delivered by the Transitions API (common for
                        // short trips < ~5 min).
                        WorkManager.getInstance(context).enqueueUniqueWork(
                            VehicleSpeedCheckWorker.TAG,
                            ExistingWorkPolicy.REPLACE,
                            VehicleSpeedCheckWorker.buildRequest(),
                        )
                    }

                    event.activityType == DetectedActivity.IN_VEHICLE &&
                            event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER -> {
                        // Real transition arrived — cancel the fallback check to avoid
                        // double-triggering departureEventBus / DrivingTrackingService.
                        // Use the event's own elapsed-realtime timestamp (not delivery time)
                        // because the Transitions API can batch events up to ~5 min late.
                        val eventEpochMs = System.currentTimeMillis() -
                            SystemClock.elapsedRealtime() +
                            event.elapsedRealTimeNanos / 1_000_000L
                        WorkManager.getInstance(context).cancelUniqueWork(VehicleSpeedCheckWorker.TAG)
                        departureEventBus.onVehicleEntered(eventEpochMs)
                        startDrivingService(context, DrivingTrackingService.ACTION_START_TRACKING)
                    }

                    event.activityType == DetectedActivity.IN_VEHICLE &&
                            event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT -> {
                        startDrivingService(context, DrivingTrackingService.ACTION_VEHICLE_EXIT)
                    }
                }
            }
        }
    }

    private fun startDrivingService(context: Context, serviceAction: String?) {
        val serviceIntent = Intent(context, DrivingTrackingService::class.java).apply {
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
    }
}
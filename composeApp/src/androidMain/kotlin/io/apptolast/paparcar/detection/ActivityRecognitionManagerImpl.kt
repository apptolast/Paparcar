package io.apptolast.paparcar.detection

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import io.apptolast.paparcar.detection.receiver.ActivityTransitionReceiver
import io.apptolast.paparcar.detection.service.ParkingDetectionService
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.github.aakira.napier.Napier

class ActivityRecognitionManagerImpl(
    private val context: Context,
) : ActivityRecognitionManager {

    private val activityClient = ActivityRecognition.getClient(context)

    // STILL events → BroadcastReceiver. No FGS needed — coordinator.onStillDetected() is fire-and-forget.
    private val stillPendingIntent: PendingIntent by lazy {
        val intent = Intent(context, ActivityTransitionReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            ActivityTransitionReceiver.REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    // IN_VEHICLE events → ForegroundService PendingIntent. Play Services delivers directly to the
    // service with system privileges, bypassing the Android 12+ background FGS start restriction.
    // [BUG-FGS-001]
    private val vehiclePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, ParkingDetectionService::class.java).apply {
            action = ParkingDetectionService.ACTION_VEHICLE_TRANSITION
        }
        PendingIntent.getForegroundService(
            context,
            VEHICLE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    override fun registerTransitions() {
        if (!hasActivityRecognitionPermission()) {
            Napier.w("registerTransitions skipped — ACTIVITY_RECOGNITION permission not granted", tag = TAG)
            return
        }

        val stillRequest = ActivityTransitionRequest(
            listOf(
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.STILL)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
            ),
        )

        val vehicleRequest = ActivityTransitionRequest(
            listOf(
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.IN_VEHICLE)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.IN_VEHICLE)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build(),
            ),
        )

        activityClient.requestActivityTransitionUpdates(stillRequest, stillPendingIntent)
            .addOnSuccessListener { Napier.d("STILL transitions registered", tag = TAG) }
            .addOnFailureListener { e -> Napier.e("Failed to register STILL transitions", e, tag = TAG) }

        activityClient.requestActivityTransitionUpdates(vehicleRequest, vehiclePendingIntent)
            .addOnSuccessListener { Napier.d("IN_VEHICLE transitions registered", tag = TAG) }
            .addOnFailureListener { e -> Napier.e("Failed to register IN_VEHICLE transitions", e, tag = TAG) }
    }

    override fun unregisterTransitions() {
        activityClient.removeActivityTransitionUpdates(stillPendingIntent)
        activityClient.removeActivityTransitionUpdates(vehiclePendingIntent)
    }

    private fun hasActivityRecognitionPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private companion object {
        const val TAG = "ActivityRecognitionManager"
        const val VEHICLE_REQUEST_CODE = 102
    }
}

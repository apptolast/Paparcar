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
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.util.PaparcarLogger

class ActivityRecognitionManagerImpl(
    private val context: Context,
) : ActivityRecognitionManager {

    // createAttributionContext (API 30+) ties AppOps operations to the "detection" tag declared
    // in the manifest. Without it, Play Services logs "attributionTag not declared" for every AR
    // operation and the same AppOps startTime leaks forever, filling logcat with 5 000+
    // ACTIVITY_RECOGNITION warnings that bury all PARKDIAG output. [FGS-004]
    private val activityClient = ActivityRecognition.getClient(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.createAttributionContext("detection")
        } else {
            context
        },
    )

    // IN_VEHICLE events → BroadcastReceiver. [DET-G-01] AR no longer ARMS the coordinator (the
    // geofence exit does), so it needs no foreground service — a getBroadcast avoids the FGS flash on
    // every bus ride / spurious IN_VEHICLE. The receiver records the enter timestamp (departure-window
    // corroborator) and forwards EXIT to a running coordinator as a non-decisive hint.
    private val vehiclePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, ActivityTransitionReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            VEHICLE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    override fun registerTransitions() {
        PaparcarLogger.d(TAG, "▶ registerTransitions called")
        if (!hasActivityRecognitionPermission()) {
            PaparcarLogger.w(TAG, "  ✗ skipped — ACTIVITY_RECOGNITION permission not granted")
            return
        }

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

        activityClient.requestActivityTransitionUpdates(vehicleRequest, vehiclePendingIntent)
            .addOnSuccessListener { PaparcarLogger.d(TAG, "  ✓ IN_VEHICLE transitions registered") }
            .addOnFailureListener { e -> PaparcarLogger.e(TAG, "  ✗ Failed to register IN_VEHICLE transitions", e) }
    }

    override fun unregisterTransitions() {
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

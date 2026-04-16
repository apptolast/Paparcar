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
import io.github.aakira.napier.Napier

class ActivityRecognitionManagerImpl(
    private val context: Context,
) : ActivityRecognitionManager {

    private val activityClient = ActivityRecognition.getClient(context)
    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, ActivityTransitionReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            ActivityTransitionReceiver.REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    override fun registerTransitions() {
        if (!hasActivityRecognitionPermission()) {
            Napier.w("registerTransitions skipped — ACTIVITY_RECOGNITION permission not granted", tag = TAG)
            return
        }

        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
        )

        val request = ActivityTransitionRequest(transitions)

        activityClient.requestActivityTransitionUpdates(request, pendingIntent)
            .addOnSuccessListener {
                Napier.d("Activity transition updates registered", tag = TAG)
            }
            .addOnFailureListener { e ->
                Napier.e("Failed to register activity transition updates", e, tag = TAG)
            }
    }

    override fun unregisterTransitions() {
        activityClient.removeActivityTransitionUpdates(pendingIntent)
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
    }
}

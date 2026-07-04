package io.apptolast.paparcar.detection

import android.Manifest
import android.annotation.SuppressLint
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
import io.apptolast.paparcar.detection.service.CoordinatorDetectionService
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.util.PaparcarLogger

class ActivityRecognitionManagerImpl(
    private val context: Context,
    private val appPreferences: AppPreferences,
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

    // IN_VEHICLE **ENTER + EXIT** → BroadcastReceiver (always-on). [DET-G-01][DET-SOLID-001]
    // A getBroadcast avoids the FGS flash on every bus ride: both transitions are pure
    // INDICATORS — EXIT is a non-decisive hint forwarded to a running coordinator; ENTER only
    // populates DepartureEventBus (evidence for the departure verifier/worker). NOTHING is armed
    // from this receiver — arming stays exclusive to GEOFENCE_EXIT + MANUAL, per the
    // "AR = indicator only" rule that replaced the legacy AR-proximity arm.
    private val vehicleTransitionsPendingIntent: PendingIntent by lazy {
        val intent = Intent(context, ActivityTransitionReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            VEHICLE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    // [DET-SOLID-001 C1b] The scoped ENTER-arming PendingIntent (getForegroundService →
    // ACTION_AR_VEHICLE_ENTER) was purged with the AR-proximity re-arm. AR is indicator-only.

    @SuppressLint("MissingPermission")
    override fun registerTransitions() {
        PaparcarLogger.d(TAG, "▶ registerTransitions called (IN_VEHICLE ENTER+EXIT, always-on, indicator-only)")
        // Master gate: the user can switch auto-detection OFF from Settings — an intent flag
        // independent of permissions. Off → never arm (and clear any existing arming), so every
        // caller (MainActivity, BootCompletedReceiver, the periodic worker) respects it. [DET-TOGGLE-001]
        if (!appPreferences.autoDetectParking) {
            PaparcarLogger.d(TAG, "  ⊘ skipped — auto-detection turned OFF in Settings; unregistering")
            unregisterTransitions()
            return
        }
        if (!hasActivityRecognitionPermission()) {
            PaparcarLogger.w(TAG, "  ✗ skipped — ACTIVITY_RECOGNITION permission not granted")
            return
        }

        // [DET-SOLID-001] ENTER rides the SAME always-on broadcast request as EXIT — NOT the
        // legacy getForegroundService arming PendingIntent (that one flashes an FGS on every bus
        // and is the arm path the "AR = indicator only" rule forbids). The receiver only stamps
        // the bus with the true transition time.
        val transitionsRequest = ActivityTransitionRequest(
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

        activityClient.requestActivityTransitionUpdates(transitionsRequest, vehicleTransitionsPendingIntent)
            .addOnSuccessListener { PaparcarLogger.d(TAG, "  ✓ IN_VEHICLE ENTER+EXIT transitions registered") }
            .addOnFailureListener { e -> PaparcarLogger.e(TAG, "  ✗ Failed to register IN_VEHICLE transitions", e) }
    }

    @SuppressLint("MissingPermission")
    override fun unregisterTransitions() {
        activityClient.removeActivityTransitionUpdates(vehicleTransitionsPendingIntent)
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

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

    // IN_VEHICLE **EXIT** → BroadcastReceiver (always-on). [DET-G-01] A getBroadcast avoids the FGS
    // flash on every bus ride: EXIT is a non-decisive hint forwarded to a running coordinator only.
    // ENTER is no longer delivered here — it is the scoped getForegroundService registration below
    // ([vehicleEnterArmingPendingIntent]) so each transition reaches exactly one PendingIntent and
    // ENTER can do the privileged FGS start the proximity re-arm needs. [DET-AR-REARM-001]
    private val vehicleExitPendingIntent: PendingIntent by lazy {
        val intent = Intent(context, ActivityTransitionReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            VEHICLE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    // IN_VEHICLE **ENTER** → CoordinatorDetectionService directly (privileged FGS start, the same
    // getForegroundService mechanism the geofence exit uses). Registered only while a car is parked
    // (see [registerVehicleEnterArming]); the service proximity-gates it so only boarding the user's
    // OWN car (near the parked location) arms detection. [DET-AR-REARM-001]
    private val vehicleEnterArmingPendingIntent: PendingIntent by lazy {
        val intent = Intent(context, CoordinatorDetectionService::class.java).apply {
            action = CoordinatorDetectionService.ACTION_AR_VEHICLE_ENTER
        }
        // FLAG_MUTABLE: Play Services fills the ActivityTransitionResult extras into the intent.
        PendingIntent.getForegroundService(
            context,
            VEHICLE_ENTER_ARMING_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    @SuppressLint("MissingPermission")
    override fun registerTransitions() {
        PaparcarLogger.d(TAG, "▶ registerTransitions called (IN_VEHICLE EXIT, always-on)")
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

        val exitRequest = ActivityTransitionRequest(
            listOf(
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.IN_VEHICLE)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build(),
            ),
        )

        activityClient.requestActivityTransitionUpdates(exitRequest, vehicleExitPendingIntent)
            .addOnSuccessListener { PaparcarLogger.d(TAG, "  ✓ IN_VEHICLE EXIT transition registered") }
            .addOnFailureListener { e -> PaparcarLogger.e(TAG, "  ✗ Failed to register IN_VEHICLE EXIT", e) }
    }

    @SuppressLint("MissingPermission")
    override fun unregisterTransitions() {
        activityClient.removeActivityTransitionUpdates(vehicleExitPendingIntent)
    }

    @SuppressLint("MissingPermission")
    override fun registerVehicleEnterArming() {
        // [DET-AR-REARM-001] Isolation toggle. With this OFF the scoped IN_VEHICLE ENTER is never
        // registered → AR never wakes the service → the coordinator is only ever armed by a REAL
        // geofence exit (or manual). Used to test whether AR-rearm is what provokes the spurious
        // GEOFENCE_EXIT (AR misfire → arm → coordinator's active GPS nudges the geofence).
        if (!AR_REARM_ENABLED) {
            PaparcarLogger.d(TAG, "  ⊘ registerVehicleEnterArming skipped — AR_REARM_ENABLED=false (isolation)")
            return
        }
        PaparcarLogger.d(TAG, "▶ registerVehicleEnterArming (IN_VEHICLE ENTER, scoped to parked window)")
        if (!hasActivityRecognitionPermission()) {
            PaparcarLogger.w(TAG, "  ✗ skipped — ACTIVITY_RECOGNITION permission not granted")
            return
        }

        val enterRequest = ActivityTransitionRequest(
            listOf(
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.IN_VEHICLE)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
            ),
        )

        activityClient.requestActivityTransitionUpdates(enterRequest, vehicleEnterArmingPendingIntent)
            .addOnSuccessListener { PaparcarLogger.d(TAG, "  ✓ IN_VEHICLE ENTER arming registered") }
            .addOnFailureListener { e -> PaparcarLogger.e(TAG, "  ✗ Failed to register IN_VEHICLE ENTER arming", e) }
    }

    @SuppressLint("MissingPermission")
    override fun unregisterVehicleEnterArming() {
        PaparcarLogger.d(TAG, "■ unregisterVehicleEnterArming")
        activityClient.removeActivityTransitionUpdates(vehicleEnterArmingPendingIntent)
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
        const val VEHICLE_ENTER_ARMING_REQUEST_CODE = 103
        // [DET-AR-REARM-001] Isolation toggle (non-const so the body stays reachable). Set true to
        // re-enable the AR proximity re-arm once the spurious-geofence-exit cause is confirmed.
        val AR_REARM_ENABLED = false
    }
}

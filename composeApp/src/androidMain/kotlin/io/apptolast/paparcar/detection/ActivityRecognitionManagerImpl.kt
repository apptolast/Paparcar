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
    // The EVIDENCE lane: a getBroadcast that stamps DepartureEventBus with true transition times
    // and accelerates the safety-net evaluator. It never arms anything and survives even if the
    // decision lane below is ever denied by an OEM — twin-lane, like the geofence witness.
    private val vehicleTransitionsPendingIntent: PendingIntent by lazy {
        val intent = Intent(context, ActivityTransitionReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            VEHICLE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    // [DET-AR-FIRST-001] IN_VEHICLE **ENTER only** → CoordinatorDetectionService via
    // `getForegroundService`: the DECISION lane. Play Services starts the service WITH privileges
    // — the same mechanism the geofence EXIT lane proves in the field daily (6/6 starts on the
    // Oppo 2026-07-10, even with late deliveries). This is NOT the BUG-FGS-001 crash class: that
    // was OUR `startForegroundService()` from a background receiver; here GMS owns the start.
    // The service runs the arm ladder (EvaluateArEnterArmUseCase) and stops within seconds when
    // the ENTER is not tied to the user's own car — a bus ride costs one notification flash.
    private val vehicleEnterDecisionPendingIntent: PendingIntent by lazy {
        val intent = Intent(context, CoordinatorDetectionService::class.java).apply {
            action = CoordinatorDetectionService.ACTION_AR_TRANSITION
        }
        // FLAG_MUTABLE: Play Services fills ActivityTransitionResult extras at delivery time.
        PendingIntent.getForegroundService(
            context,
            VEHICLE_ENTER_DECISION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    /**
     * [DET-AR-FIRST-001b] GMS re-delivers the LAST known IN_VEHICLE transition every time the
     * same PendingIntent is re-registered — and registerTransitions() runs on every app open
     * (MainActivity + HomeViewModel). Field 2026-07-11: stale ENTERs with up to 32 min of lag
     * flash-started the decision-lane FGS all evening while the user sat at home. In-process
     * throttle: at most one real registration per [RE_REGISTER_MIN_INTERVAL_MS]; process death
     * (force-stop, app update, OEM kill) resets it, so the register-on-restart cure is untouched.
     */
    @Volatile private var lastRegisteredAtMs = 0L

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
        val now = System.currentTimeMillis()
        if (now - lastRegisteredAtMs < RE_REGISTER_MIN_INTERVAL_MS) {
            PaparcarLogger.d(TAG, "  ↻ skipped — registered ${(now - lastRegisteredAtMs) / 1000}s ago; GMS would re-deliver the last stale transition [DET-AR-FIRST-001b]")
            return
        }
        lastRegisteredAtMs = now

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
            .addOnSuccessListener { PaparcarLogger.d(TAG, "  ✓ IN_VEHICLE ENTER+EXIT transitions registered (evidence lane)") }
            .addOnFailureListener { e ->
                PaparcarLogger.e(TAG, "  ✗ Failed to register IN_VEHICLE transitions", e)
                lastRegisteredAtMs = 0L // failed registration must not block the next attempt
            }

        // [DET-AR-FIRST-001] Decision lane: ENTER only (EXIT decisions stay with the evaluator),
        // its own registration + PendingIntent — same multi-registration pattern as the three
        // geofences per parking. Registered second so a failure here degrades to today's
        // receiver-only behaviour instead of losing the evidence lane.
        val enterDecisionRequest = ActivityTransitionRequest(
            listOf(
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.IN_VEHICLE)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
            ),
        )
        activityClient.requestActivityTransitionUpdates(enterDecisionRequest, vehicleEnterDecisionPendingIntent)
            .addOnSuccessListener { PaparcarLogger.d(TAG, "  ✓ IN_VEHICLE ENTER decision lane registered (getForegroundService) [DET-AR-FIRST-001]") }
            .addOnFailureListener { e ->
                PaparcarLogger.e(TAG, "  ✗ Failed to register ENTER decision lane", e)
                lastRegisteredAtMs = 0L // failed registration must not block the next attempt
            }
    }

    @SuppressLint("MissingPermission")
    override fun unregisterTransitions() {
        lastRegisteredAtMs = 0L // an OFF→ON toggle flip must register immediately
        activityClient.removeActivityTransitionUpdates(vehicleTransitionsPendingIntent)
        activityClient.removeActivityTransitionUpdates(vehicleEnterDecisionPendingIntent)
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
        /** [DET-AR-FIRST-001] Decision-lane PendingIntent request code (must differ from 102). */
        const val VEHICLE_ENTER_DECISION_REQUEST_CODE = 103
        /** [DET-AR-FIRST-001b] Min gap between real GMS registrations — each one re-delivers the
         *  last stale IN_VEHICLE transition to BOTH lanes. A GMS-side wipe is cured within this
         *  window by the next caller (app open, periodic worker, boot receiver). */
        const val RE_REGISTER_MIN_INTERVAL_MS = 30L * 60L * 1000L
    }
}

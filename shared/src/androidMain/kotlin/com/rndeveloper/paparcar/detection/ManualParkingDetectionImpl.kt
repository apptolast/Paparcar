package com.rndeveloper.paparcar.detection

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.rndeveloper.paparcar.detection.service.CoordinatorDetectionService
import com.rndeveloper.paparcar.domain.detection.ports.ManualParkingDetection

/**
 * Starts the Coordinator detection service to track the current trip via [ACTION_START_TRACKING]
 * (which the service already handles — `handleStartTracking` → `startParkingDetection`, no prior
 * geofence required). Triggered by the user tapping "I'm driving"; the app is in the foreground so
 * the foreground-service start is permitted on Android 12+. [DET-G-01b]
 */
class ManualParkingDetectionImpl(private val context: Context) : ManualParkingDetection {
    override suspend fun finishTrip(rejectTrip: Boolean): Result<Unit> =
        ManualParkingCompletion.await { token ->
            context.startService(Intent(context, CoordinatorDetectionService::class.java).apply {
                action = CoordinatorDetectionService.ACTION_FINISH_TRIP
                putExtra("manual_completion_token", token)
                putExtra("reject_trip", rejectTrip)
            })
        }

    override fun resumeTrip(vehicleId: String, location: com.rndeveloper.paparcar.domain.model.GpsPoint) {
        ContextCompat.startForegroundService(context, Intent(context, CoordinatorDetectionService::class.java).apply {
            action = CoordinatorDetectionService.ACTION_START_TRACKING
            putExtra("manual_vehicle_id", vehicleId)
            putExtra("manual_lat", location.latitude)
            putExtra("manual_lon", location.longitude)
        })
    }

    override fun start() {
        val intent = Intent(context, CoordinatorDetectionService::class.java).apply {
            action = CoordinatorDetectionService.ACTION_START_TRACKING
        }
        ContextCompat.startForegroundService(context, intent)
    }

    // [DET-MANUAL-CANCEL-001] The user marked a park manually → cancel any in-progress coordinator
    // session so a late auto-confirm cannot overwrite the manual pin. A plain startService (not
    // foreground) is fine: STOP_TRACKING only cancels the detection job, it never needs to promote.
    override fun stop() {
        val intent = Intent(context, CoordinatorDetectionService::class.java).apply {
            action = CoordinatorDetectionService.ACTION_STOP_TRACKING
        }
        context.startService(intent)
    }

    // [DET-STOP-BUTTON-001] "Parar detección" on the Home row. Its own action, not STOP_TRACKING:
    // this one stamps the `stopped_by_user` outcome, drops any held confirm and opens the quiet
    // period. A plain startService is enough — the service is already in the foreground running the
    // very session being stopped, so there is nothing to promote.
    override fun stopByUser() {
        val intent = Intent(context, CoordinatorDetectionService::class.java).apply {
            action = CoordinatorDetectionService.ACTION_USER_STOP
        }
        context.startService(intent)
    }

    // [DET-ASK-STATE-001] Answering the question from the Home row fires the very actions the
    // notification's two buttons fire — one intake, one set of coordinator hooks, one dismissal.
    // A plain startService is enough: the question only exists while the service is already in the
    // foreground running the session being asked about.
    override fun answerStillParked(
        question: com.rndeveloper.paparcar.domain.detection.StillParkedQuestion,
        answer: com.rndeveloper.paparcar.domain.detection.StillParkedAnswer,
    ): Boolean {
        val intent = Intent(context, CoordinatorDetectionService::class.java).apply {
            action = when (answer) {
                com.rndeveloper.paparcar.domain.detection.StillParkedAnswer.HERE -> CoordinatorDetectionService.ACTION_STILL_PARKED_HERE
                com.rndeveloper.paparcar.domain.detection.StillParkedAnswer.DRIVING -> CoordinatorDetectionService.ACTION_STILL_PARKED_DRIVING
                com.rndeveloper.paparcar.domain.detection.StillParkedAnswer.ELSEWHERE -> CoordinatorDetectionService.ACTION_STILL_PARKED_ELSEWHERE
            }
            putExtra(CoordinatorDetectionService.EXTRA_GEOFENCE_ID, question.geofenceId)
            putExtra(CoordinatorDetectionService.EXTRA_PROMPT_AT, question.shownAtMs)
        }
        ContextCompat.startForegroundService(context, intent)
        return true
    }

    override fun answerPrompt(parked: Boolean) = answerPrompt(parked, null)

    override fun answerPrompt(parked: Boolean, shownAtMs: Long?) {
        val intent = Intent(context, CoordinatorDetectionService::class.java).apply {
            action = if (parked) {
                CoordinatorDetectionService.ACTION_PARKING_CONFIRMED
            } else {
                CoordinatorDetectionService.ACTION_PARKING_DENIED
            }
        }
        shownAtMs?.let { intent.putExtra(CoordinatorDetectionService.EXTRA_PROMPT_AT, it) }
        if (!parked && shownAtMs == null) intent.putExtra(CoordinatorDetectionService.EXTRA_CANDIDATE_RESET, true)
        context.startService(intent)
    }
}

package io.apptolast.paparcar.detection

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.apptolast.paparcar.detection.service.CoordinatorDetectionService
import io.apptolast.paparcar.domain.detection.ManualParkingDetection

/**
 * Starts the Coordinator detection service to track the current trip via [ACTION_START_TRACKING]
 * (which the service already handles — `handleStartTracking` → `startParkingDetection`, no prior
 * geofence required). Triggered by the user tapping "I'm driving"; the app is in the foreground so
 * the foreground-service start is permitted on Android 12+. [DET-G-01b]
 */
class ManualParkingDetectionImpl(private val context: Context) : ManualParkingDetection {
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
}

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
}

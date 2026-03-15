package io.apptolast.paparcar.detection.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.apptolast.paparcar.detection.service.ParkingDetectionService

/**
 * Handles user responses to the parking-confirmation notification.
 *
 * "Sí, he aparcado" → [ParkingDetectionService.ACTION_PARKING_CONFIRMED]
 * "Sigo conduciendo" → [ParkingDetectionService.ACTION_PARKING_DENIED]
 *
 * The receiver just forwards the decision to [ParkingDetectionService], which
 * calls [ParkingDetectionCoordinator.onUserConfirmedParking] or
 * [ParkingDetectionCoordinator.onUserDeniedParking] on the running job.
 */
class ParkingConfirmationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val serviceAction = when (intent.action) {
            ACTION_CONFIRMED -> ParkingDetectionService.ACTION_PARKING_CONFIRMED
            ACTION_DENIED -> ParkingDetectionService.ACTION_PARKING_DENIED
            else -> return
        }
        val serviceIntent = Intent(context, ParkingDetectionService::class.java).apply {
            action = serviceAction
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    companion object {
        const val ACTION_CONFIRMED = "io.apptolast.paparcar.ACTION_PARKING_CONFIRMED"
        const val ACTION_DENIED = "io.apptolast.paparcar.ACTION_PARKING_DENIED"
    }
}
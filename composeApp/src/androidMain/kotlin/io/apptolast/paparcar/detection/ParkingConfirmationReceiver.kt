package io.apptolast.paparcar.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.apptolast.paparcar.detection.services.DrivingTrackingService

/**
 * Handles user responses to the parking-confirmation notification.
 *
 * "Sí, he aparcado" → [DrivingTrackingService.ACTION_PARKING_CONFIRMED]
 * "Sigo conduciendo" → [DrivingTrackingService.ACTION_PARKING_DENIED]
 *
 * The receiver just forwards the decision to [DrivingTrackingService], which
 * calls [DetectAndReportParkingUseCase.onUserConfirmedParking] or
 * [DetectAndReportParkingUseCase.onUserDeniedParking] on the running job.
 */
class ParkingConfirmationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val serviceAction = when (intent.action) {
            ACTION_CONFIRMED -> DrivingTrackingService.ACTION_PARKING_CONFIRMED
            ACTION_DENIED -> DrivingTrackingService.ACTION_PARKING_DENIED
            else -> return
        }
        context.startService(
            Intent(context, DrivingTrackingService::class.java).apply {
                action = serviceAction
            }
        )
    }

    companion object {
        const val ACTION_CONFIRMED = "io.apptolast.paparcar.ACTION_PARKING_CONFIRMED"
        const val ACTION_DENIED = "io.apptolast.paparcar.ACTION_PARKING_DENIED"
    }
}

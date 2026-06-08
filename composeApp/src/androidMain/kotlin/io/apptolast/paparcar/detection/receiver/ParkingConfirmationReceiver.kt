package io.apptolast.paparcar.detection.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.apptolast.paparcar.detection.service.ParkingDetectionService

/**
 * Handles user responses to the parking-confirmation notification.
 *
 * **Pre-save prompt (state A — "¿Has aparcado?"):**
 * - "Sí, he aparcado" → [ParkingDetectionService.ACTION_PARKING_CONFIRMED]
 * - "Sigo conduciendo" → [ParkingDetectionService.ACTION_PARKING_DENIED]
 *
 * **Post-save confirm (state B — "Toyota aparcado") [REFACTOR-300]:**
 * - "Sí, confirmar" → [ParkingDetectionService.ACTION_PARKING_ACK]
 * - "No, cancelar" → [ParkingDetectionService.ACTION_PARKING_REVERT] (+ parkingId extra)
 *
 * The receiver just forwards the decision to [ParkingDetectionService], which calls the
 * appropriate coordinator hook (states A) or use case (state B). State A and state B
 * share the same notification ID — the morph from prompt to "saved with revert" is
 * driven by the notification post itself, not by the receiver.
 *
 * [FIX BUG-SERVICE-107: ACTION_CONFIRMED / ACTION_DENIED used to be separately-defined
 *  string literals that *happened* to equal the Service constants. They are now compile-time
 *  aliases so the Service is the single source of truth — a rename on either side would
 *  silently break intent routing otherwise.]
 */
class ParkingConfirmationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // [REFACTOR: bind action to a val so we never need !! — the early-return guards null.]
        val action = intent.action ?: return
        if (action !in ROUTABLE_ACTIONS) return
        val serviceIntent = Intent(context, ParkingDetectionService::class.java).apply {
            this.action = action
            // Forward the parkingId for the REVERT action; harmless on other actions.
            intent.getStringExtra(EXTRA_PARKING_ID)?.let { putExtra(EXTRA_PARKING_ID, it) }
        }
        context.startForegroundService(serviceIntent)
    }

    companion object {
        // Aliases to the Service constants. Single source of truth, no duplication.
        const val ACTION_CONFIRMED = ParkingDetectionService.ACTION_PARKING_CONFIRMED
        const val ACTION_DENIED = ParkingDetectionService.ACTION_PARKING_DENIED
        const val ACTION_ACK = ParkingDetectionService.ACTION_PARKING_ACK
        const val ACTION_REVERT = ParkingDetectionService.ACTION_PARKING_REVERT
        const val EXTRA_PARKING_ID = ParkingDetectionService.EXTRA_PARKING_ID

        private val ROUTABLE_ACTIONS = setOf(ACTION_CONFIRMED, ACTION_DENIED, ACTION_ACK, ACTION_REVERT)
    }
}

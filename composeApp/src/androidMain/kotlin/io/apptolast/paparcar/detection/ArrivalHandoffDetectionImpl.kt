package io.apptolast.paparcar.detection

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.apptolast.paparcar.detection.service.CoordinatorDetectionService
import io.apptolast.paparcar.domain.detection.ports.ArrivalHandoffDetection

/**
 * [DET-HANDOFF-NOT-MANUAL-001] Sends the safety net's arrival handoff through its OWN service
 * action ([CoordinatorDetectionService.ACTION_ARRIVAL_HANDOFF]) instead of borrowing the "I'm
 * driving" one. The start is a foreground-service start from a worker that is itself foreground
 * (the safety net runs with `getForegroundInfo`), and the caller handles a platform refusal by
 * asking the user.
 */
class ArrivalHandoffDetectionImpl(private val context: Context) : ArrivalHandoffDetection {
    override fun start() {
        val intent = Intent(context, CoordinatorDetectionService::class.java).apply {
            action = CoordinatorDetectionService.ACTION_ARRIVAL_HANDOFF
        }
        ContextCompat.startForegroundService(context, intent)
    }
}

package io.apptolast.paparcar.detection.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import io.apptolast.paparcar.detection.ExactHeartbeatScheduler
import io.apptolast.paparcar.detection.worker.ParkingSafetyNetWorker
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.util.PaparcarLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Fires on each exact-heartbeat alarm tick while a session is parked: enqueues the standard
 * safety-net check (same evaluator, same proofs — this is a TRIGGER, not a second brain) and
 * immediately re-arms the next tick so the chain survives even if the check itself is throttled.
 * The worker's own [ExactHeartbeatScheduler.sync] on the resulting tick is what DISARMS the chain
 * once no active session remains. [DET-EXACT-HEARTBEAT-001]
 *
 * [DET-HEARTBEAT-MISS-IS-EVIDENCE-001] Reaching this method is itself the evidence that the lane
 * WORKS on this device, so it clears the lost-tick streak before anything else. Everything that
 * judges the lane's health is written on the assumption that this code either runs or does not —
 * on the 2026-08-21 Oppo it did not, for three hours, while the alarms were being delivered.
 */
class ExactHeartbeatReceiver : BroadcastReceiver(), KoinComponent {

    private val config: ParkingDetectionConfig by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val delay = ExactHeartbeatScheduler.firedDelayMs(context, System.currentTimeMillis())
        PaparcarLogger.d(TAG, "⏰ exact heartbeat fired (doze stretch: ${delay ?: "?"} ms)")
        ExactHeartbeatScheduler.markLaneAlive(context, config)
        ExactHeartbeatScheduler.sync(context, shouldBeArmed = true, config = config)
        ParkingSafetyNetWorker.enqueueCheckNow(
            WorkManager.getInstance(context),
            source = ParkingSafetyNetWorker.SOURCE_EXACT_ALARM,
        )
    }

    private companion object {
        const val TAG = "PARKDIAG/ExactNet"
    }
}

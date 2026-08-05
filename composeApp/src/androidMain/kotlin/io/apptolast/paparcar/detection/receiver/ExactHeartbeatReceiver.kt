package io.apptolast.paparcar.detection.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import io.apptolast.paparcar.detection.ExactHeartbeatScheduler
import io.apptolast.paparcar.detection.worker.ParkingSafetyNetWorker
import io.apptolast.paparcar.domain.util.PaparcarLogger

/**
 * Fires on each exact-heartbeat alarm tick while a session is parked: enqueues the standard
 * safety-net check (same evaluator, same proofs — this is a TRIGGER, not a second brain) and
 * immediately re-arms the next tick so the chain survives even if the check itself is throttled.
 * The worker's own [ExactHeartbeatScheduler.sync] on the resulting tick is what DISARMS the chain
 * once no active session remains. [DET-EXACT-HEARTBEAT-001]
 */
class ExactHeartbeatReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val delay = ExactHeartbeatScheduler.firedDelayMs(context, System.currentTimeMillis())
        PaparcarLogger.d(TAG, "⏰ exact heartbeat fired (doze stretch: ${delay ?: "?"} ms)")
        ExactHeartbeatScheduler.sync(context, shouldBeArmed = true)
        ParkingSafetyNetWorker.enqueueCheckNow(
            WorkManager.getInstance(context),
            source = ParkingSafetyNetWorker.SOURCE_EXACT_ALARM,
        )
    }

    private companion object {
        const val TAG = "PARKDIAG/ExactNet"
    }
}

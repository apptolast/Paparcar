package io.apptolast.paparcar.detection.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import io.apptolast.paparcar.detection.worker.DetectionHeartbeatWorker
import io.apptolast.paparcar.detection.worker.GeofenceJanitorWorker
import io.apptolast.paparcar.detection.worker.RegisterActivityTransitionsWorker
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Restores activity recognition tracking after a device reboot.
 *
 * On boot:
 * 1. Immediately re-registers Activity Recognition transitions.
 * 2. Re-enqueues the periodic [RegisterActivityTransitionsWorker] so it keeps
 *    running every 12 hours (KEEP policy avoids restarting a running job).
 */
class BootCompletedReceiver : BroadcastReceiver(), KoinComponent {

    private val activityRecognitionManager: ActivityRecognitionManager by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val workManager = WorkManager.getInstance(context)
        activityRecognitionManager.registerTransitions()
        RegisterActivityTransitionsWorker.enqueueKeep(workManager)
        GeofenceJanitorWorker.enqueueKeep(workManager)
        // Reboot wipes every registered geofence. The periodic KEEP above can take up to 12 h to
        // fire — an active park would sit blind (departure undetectable) for that whole window.
        // Run the restore ONCE right now as well. Idempotent. [DET-SOLID-001]
        GeofenceJanitorWorker.enqueueOnce(workManager)
        // Fixme: Seguimos necesitando DetectionHeartbeatWorker?
        DetectionHeartbeatWorker.enqueueKeep(workManager)
    }
}
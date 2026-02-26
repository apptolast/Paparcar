package io.apptolast.paparcar.detection.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
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

        activityRecognitionManager.registerTransitions()
        RegisterActivityTransitionsWorker.enqueueKeep(WorkManager.getInstance(context))
    }
}
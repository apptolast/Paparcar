package io.apptolast.paparcar.detection.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import io.apptolast.paparcar.detection.worker.GeofenceJanitorWorker
import io.apptolast.paparcar.detection.worker.ParkingSafetyNetWorker
import io.apptolast.paparcar.detection.worker.RegisterActivityTransitionsWorker
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Re-arms every OS-registered detection trigger after the events that wipe them:
 *
 * - **`BOOT_COMPLETED`** — reboot erases geofences, AR transition registrations and alarms.
 *   On Android 15+ this action is ALSO delivered synthetically when the app leaves the *stopped*
 *   state (user opens it after a force-stop / OEM deep-kill), so this handler doubles as the
 *   post-force-stop re-arm hook and must stay idempotent (KEEP policies + FLAG_UPDATE_CURRENT
 *   re-registers — it is).
 * - **`MY_PACKAGE_REPLACED`** — an app update also drops Play Services geofence registrations
 *   ([GEOF-RESTORE-001]); without this hook an updated app sits blind until the next manual open
 *   or 12 h periodic.
 *
 * On either event:
 * 1. Immediately re-registers Activity Recognition transitions.
 * 2. Re-enqueues the periodic workers (KEEP avoids restarting running jobs) and runs the
 *    geofence restore ONCE right now.
 */
class BootCompletedReceiver : BroadcastReceiver(), KoinComponent {

    private val activityRecognitionManager: ActivityRecognitionManager by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val workManager = WorkManager.getInstance(context)
        activityRecognitionManager.registerTransitions()
        RegisterActivityTransitionsWorker.enqueueKeep(workManager)
        GeofenceJanitorWorker.enqueueKeep(workManager)
        // Reboot wipes every registered geofence. The periodic KEEP above can take up to 12 h to
        // fire — an active park would sit blind (departure undetectable) for that whole window.
        // Run the restore ONCE right now as well. Idempotent. [DET-SOLID-001]
        GeofenceJanitorWorker.enqueueOnce(workManager)
        // Parked-session safety net (fence cure + missed-departure recovery + sig-motion re-arm).
        // Reboot also killed the in-process sensor listener — the periodic re-arms it. [DET-SAFETY-NET-001]
        ParkingSafetyNetWorker.enqueueKeep(workManager)
    }
}
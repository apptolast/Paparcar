package com.rndeveloper.paparcar.detection.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.usecase.detection.EvaluateFirstParkNudgeUseCase
import com.rndeveloper.paparcar.domain.usecase.detection.isFirstParkNudgeSpent
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Daily, low-cadence worker that fires the cold-start "park once" nudge. [DET-TOGGLE-002]
 *
 * Firing is the exception, not the rule: it wakes ~once a day but only shows a notification when
 * [EvaluateFirstParkNudgeUseCase] says so — i.e. detection is fully ready on the Coordinator strategy
 * (the `AwaitingFirstPark` cold-start), the user has never confirmed a park, the cooldown has elapsed,
 * and the hard cap is not yet reached. Bluetooth and inactive vehicles never reach the cold-start
 * state, so they are never nudged.
 *
 * A PERMANENTLY spent nudge (park confirmed, or cap exhausted) owns no clock: the worker cancels
 * its own periodic on the tick that finds it spent, and [syncSchedule] refuses to (re)install it
 * at app start — otherwise the periodic wakes daily forever doing nothing, spending the shared
 * background-job quota Android 16 meters per app. [DET-SPENT-NUDGE-MUST-STOP-WAKING-001]
 */
class FirstParkNudgeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val evaluateNudge: EvaluateFirstParkNudgeUseCase by inject()
    private val notificationPort: AppNotificationManager by inject()
    private val appPreferences: AppPreferences by inject()

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        if (evaluateNudge(now)) {
            notificationPort.showFirstParkNudge()
            appPreferences.setFirstParkNudgeCount(appPreferences.firstParkNudgeCount + 1)
            appPreferences.setLastFirstParkNudgeAt(now)
            PaparcarLogger.d(TAG, "▶ cold-start nudge shown (count=${appPreferences.firstParkNudgeCount})")
        }
        // Checked AFTER the show so the tick that fires the last capped nudge also retires the
        // clock, instead of waking once more just to notice. Cancelling the unique work from
        // inside its own run is safe: there is nothing left to do below, and the periodic must
        // not survive either way. [DET-SPENT-NUDGE-MUST-STOP-WAKING-001]
        if (isFirstParkNudgeSpent(appPreferences.hasConfirmedFirstPark, appPreferences.firstParkNudgeCount)) {
            PaparcarLogger.d(TAG, "■ nudge permanently spent — cancelling its daily periodic [DET-SPENT-NUDGE-MUST-STOP-WAKING-001]")
            WorkManager.getInstance(applicationContext).cancelUniqueWork(TAG)
        }
        return Result.success()
    }

    companion object {
        const val TAG = "FirstParkNudgeWorker"
        private const val INTERVAL_HOURS = 24L

        fun buildPeriodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<FirstParkNudgeWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .addTag(TAG)
                .build()

        /**
         * Installs the daily periodic — or, when the nudge is permanently spent, removes it.
         * The cancel branch is what retires the clock on devices where the state went spent
         * while the app was dead (the worker's own self-cancel never got to run): app start is
         * the first moment we reliably get. [DET-SPENT-NUDGE-MUST-STOP-WAKING-001]
         */
        fun syncSchedule(workManager: WorkManager, nudgeSpent: Boolean) {
            if (nudgeSpent) {
                workManager.cancelUniqueWork(TAG)
            } else {
                workManager.enqueueUniquePeriodicWork(
                    TAG,
                    ExistingPeriodicWorkPolicy.KEEP,
                    buildPeriodicRequest(),
                )
            }
        }
    }
}

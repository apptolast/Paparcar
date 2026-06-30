package io.apptolast.paparcar.detection.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.usecase.detection.EvaluateFirstParkNudgeUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Daily, low-cadence worker that fires the cold-start "park once" nudge. [DET-TOGGLE-002]
 *
 * Firing is the exception, not the rule: it wakes ~once a day but only shows a notification when
 * [EvaluateFirstParkNudgeUseCase] says so — i.e. detection is fully ready on the Coordinator strategy
 * (the `AwaitingFirstPark` cold-start), the user has never confirmed a park, the cooldown has elapsed,
 * and the hard cap is not yet reached. After the first confirmed park the use case self-disables, so
 * this worker quietly does nothing forever after. Bluetooth and inactive vehicles never reach the
 * cold-start state, so they are never nudged.
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
        return Result.success()
    }

    companion object {
        const val TAG = "FirstParkNudgeWorker"
        private const val INTERVAL_HOURS = 24L

        fun buildPeriodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<FirstParkNudgeWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .addTag(TAG)
                .build()

        fun enqueueKeep(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                buildPeriodicRequest(),
            )
        }
    }
}

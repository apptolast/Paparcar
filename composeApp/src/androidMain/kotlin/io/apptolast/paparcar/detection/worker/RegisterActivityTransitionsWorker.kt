package io.apptolast.paparcar.detection.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that re-registers Activity Recognition transitions every [INTERVAL_HOURS] hours.
 *
 * Activity Recognition registrations can be dropped by the OS over time.
 * This worker ensures they are re-registered without network constraints.
 *
 * Enqueued at app startup ([PaparcarApp]) and after device reboot ([BootCompletedReceiver]).
 */
class RegisterActivityTransitionsWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val activityRecognitionManager: ActivityRecognitionManager by inject()

    override suspend fun doWork(): Result = runCatching {
        activityRecognitionManager.registerTransitions()
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure() },
    )

    companion object {
        const val TAG = "RegisterActivityTransitionsWorker"
        private const val INTERVAL_HOURS = 12L
        private const val MAX_RETRIES = 3

        fun buildPeriodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<RegisterActivityTransitionsWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
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
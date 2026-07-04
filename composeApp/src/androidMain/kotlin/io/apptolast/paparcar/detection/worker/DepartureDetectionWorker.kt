@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.detection.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.apptolast.paparcar.domain.usecase.parking.DepartureCheckOutcome
import io.apptolast.paparcar.domain.usecase.parking.RunDepartureCheckUseCase
import kotlin.time.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Decides whether a geofence-exit represents a real car departure and, if confirmed,
 * processes all side-effects.
 *
 * Pure translation layer: input parsing + [DepartureCheckOutcome] → WorkManager [Result].
 * The whole decide → retry-policy → fallthrough-guard → live-session-upgrade → process
 * sequence lives in [RunDepartureCheckUseCase] (domain, commonTest-covered). [DET-SOLID-001]
 */
class DepartureDetectionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val runDepartureCheck: RunDepartureCheckUseCase by inject()

    override suspend fun doWork(): Result {
        val geofenceId = inputData.getString(KEY_GEOFENCE_ID)
            ?: return Result.success()

        val exitTimestampMs = inputData.getLong(KEY_EXIT_TIMESTAMP, -1L)
            .takeIf { it > 0L } ?: Clock.System.now().toEpochMilliseconds()

        return when (runDepartureCheck(geofenceId, exitTimestampMs, attempt = runAttemptCount)) {
            DepartureCheckOutcome.Retry,
            DepartureCheckOutcome.ProcessFailedRetry -> Result.retry()
            DepartureCheckOutcome.Dismissed,
            DepartureCheckOutcome.Processed -> Result.success()
        }
    }

    companion object {
        const val TAG = "DepartureDetectionWorker"
        private const val KEY_GEOFENCE_ID = "geofence_id"
        private const val KEY_EXIT_TIMESTAMP = "exit_timestamp_ms"

        /** Backoff for [DepartureCheckOutcome.Retry] — with EXPONENTIAL 15s the inconclusive
         *  retries fire at ~15s/30s/60s (see [RunDepartureCheckUseCase.MAX_INCONCLUSIVE_ATTEMPTS]). */
        private const val INITIAL_BACKOFF_SECONDS = 15L

        fun buildRequest(geofenceId: String, exitTimestampMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<DepartureDetectionWorker>()
                .setInputData(
                    workDataOf(
                        KEY_GEOFENCE_ID to geofenceId,
                        KEY_EXIT_TIMESTAMP to exitTimestampMs,
                    )
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
    }
}

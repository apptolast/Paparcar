@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.detection.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.rndeveloper.paparcar.detection.service.CoordinatorDetectionService
import com.rndeveloper.paparcar.domain.usecase.parking.DepartureCheckOutcome
import com.rndeveloper.paparcar.domain.usecase.parking.RunDepartureCheckUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
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
        val preconfirmed = inputData.getBoolean(KEY_PRECONFIRMED, false)

        return when (val outcome = runDepartureCheck(geofenceId, exitTimestampMs, attempt = runAttemptCount, preconfirmed = preconfirmed)) {
            DepartureCheckOutcome.Retry,
            DepartureCheckOutcome.ProcessFailedRetry -> Result.retry()
            DepartureCheckOutcome.Dismissed -> {
                // A delivered EXIT judged false (walking/GPS drift) leaves Play Services' fence
                // state OUTSIDE — the fence is still registered and the next real drive-away would
                // be silent. Say so: the cure's next tick inside repairs it immediately, rebuilding
                // the state from a fresh fix. [DET-ANCHOR-FREEZE-001 F4]
                // [DET-FENCE-REREGISTER-BY-CAUSE-001 §B] This used to DELETE the cure's throttle
                // key to say the same thing, which made a cause indistinguishable from "never cured"
                // and from "freshly installed". Now it stamps the cause itself.
                ParkingSafetyNetWorker.markFenceStatePoisoned(applicationContext, geofenceId)
                Result.success()
            }
            is DepartureCheckOutcome.Processed -> {
                // [DET-A-JUST-DEPARTED-CAR-IS-NOT-NO-SESSION-001] The check measured the departure
                // and nobody is following the trip: arm the follower through the service's own door.
                // Best-effort like the re-look's arm — a background FGS-start may be denied
                // (Android 12+/OEM), and nothing is lost that was not already lost before this
                // ticket: the safety net stays the backstop it has always been.
                outcome.followTrip?.let { handoff ->
                    runCatching {
                        CoordinatorDetectionService.startDepartureFollowerArm(
                            applicationContext,
                            geofenceId = handoff.geofenceId,
                            speedKmh = handoff.speedKmh,
                            accuracyM = handoff.accuracyM,
                        )
                    }.onFailure { e ->
                        PaparcarLogger.w(DIAG, "⊘ follower arm denied by the OS (${e.message}) — the safety net stays the backstop")
                    }
                }
                Result.success()
            }
        }
    }

    companion object {
        const val TAG = "DepartureDetectionWorker"
        private const val DIAG = "PARKDIAG/Depart"
        private const val KEY_GEOFENCE_ID = "geofence_id"
        private const val KEY_EXIT_TIMESTAMP = "exit_timestamp_ms"
        /** Departure already proven by the parked-state reconcile (step budget) — the check must
         *  not re-decide by live speed, only process. [DET-RECONCILE-001] */
        private const val KEY_PRECONFIRMED = "preconfirmed"

        /** Backoff for [DepartureCheckOutcome.Retry] — with EXPONENTIAL 15s the inconclusive
         *  retries fire at ~15s/30s/60s (see [RunDepartureCheckUseCase.MAX_INCONCLUSIVE_ATTEMPTS]). */
        private const val INITIAL_BACKOFF_SECONDS = 15L

        fun buildRequest(
            geofenceId: String,
            exitTimestampMs: Long,
            preconfirmed: Boolean = false,
        ): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<DepartureDetectionWorker>()
                .setInputData(
                    workDataOf(
                        KEY_GEOFENCE_ID to geofenceId,
                        KEY_EXIT_TIMESTAMP to exitTimestampMs,
                        KEY_PRECONFIRMED to preconfirmed,
                    )
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
    }
}

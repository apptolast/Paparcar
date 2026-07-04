@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.detection.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.apptolast.paparcar.domain.coordinator.CoordinatorParkingDetector
import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.usecase.location.GetOneLocationUseCase
import io.apptolast.paparcar.domain.usecase.parking.DepartureDecision
import io.apptolast.paparcar.domain.usecase.parking.DetectParkingDepartureUseCase
import io.apptolast.paparcar.domain.usecase.parking.ProcessConfirmedDepartureUseCase
import kotlin.time.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Decides whether a geofence-exit represents a real car departure and, if confirmed,
 * delegates all side-effects to [ProcessConfirmedDepartureUseCase].
 *
 * This worker only handles WorkManager concerns: retries, backoff, and the
 * max-retries fallthrough guard. All domain logic lives in use cases.
 */
class DepartureDetectionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val detectParkingDeparture: DetectParkingDepartureUseCase by inject()
    private val processConfirmedDeparture: ProcessConfirmedDepartureUseCase by inject()
    private val getOneLocation: GetOneLocationUseCase by inject()
    private val departureEventBus: DepartureEventBus by inject()
    private val coordinator: CoordinatorParkingDetector by inject() // [DET-G-05]
    private val detectionEventLogger: DetectionEventLogger by inject() // [DET-SOLID-001]

    override suspend fun doWork(): Result {
        val geofenceId = inputData.getString(KEY_GEOFENCE_ID)
            ?: return Result.success()

        val exitTimestampMs = inputData.getLong(KEY_EXIT_TIMESTAMP, -1L)
            .takeIf { it > 0L } ?: Clock.System.now().toEpochMilliseconds()

        val speedKmh = getOneLocation()?.speed?.times(3.6f)

        val decision = detectParkingDeparture(
            geofenceId = geofenceId,
            exitTimestampMs = exitTimestampMs,
            currentSpeedKmh = speedKmh,
        )

        // [DET-SOLID-001] Observability: every worker verdict, traced by geofenceId. Firestore
        // trips over generic sealed objects — log the simple name string.
        runCatching {
            detectionEventLogger.log(
                DetectionEvent.DepartureVerdict(
                    sessionId = geofenceId,
                    timestampMs = Clock.System.now().toEpochMilliseconds(),
                    verdict = decision::class.simpleName ?: "UNKNOWN",
                    source = "worker",
                    attempt = runAttemptCount,
                    speedKmh = speedKmh,
                    enterAgeMs = departureEventBus.lastVehicleEnteredAt?.let { exitTimestampMs - it },
                )
            )
        }

        if (decision == DepartureDecision.Rejected) return Result.success()

        if (decision == DepartureDecision.Inconclusive && runAttemptCount < MAX_INCONCLUSIVE_RETRIES) {
            return Result.retry()
        }
        // Max retries exhausted. Fall through only if IN_VEHICLE_ENTER was recorded after
        // parking was confirmed — covers slow garage exits where speed never crosses the
        // departure threshold. Without any vehicle signal, reject to avoid false positives
        // from the user walking near the car. [BUG-WALK-DEPART-001]
        if (decision == DepartureDecision.Inconclusive && departureEventBus.lastVehicleEnteredAt == null) {
            return Result.success()
        }

        // [DET-G-05] The departure is confirmed. If the GEOFENCE_EXIT armed the coordinator
        // UNVERIFIED (no vehicle evidence at arm time — AR ENTER delivers up to ~2 min late),
        // upgrade the live session so its confirm paths unlock: the drive provably happened.
        coordinator.notifyDepartureConfirmed()

        return processConfirmedDeparture(geofenceId)
            .fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() },
            )
    }

    companion object {
        const val TAG = "DepartureDetectionWorker"
        private const val KEY_GEOFENCE_ID = "geofence_id"
        private const val KEY_EXIT_TIMESTAMP = "exit_timestamp_ms"

        /**
         * Maximum retries when the decision is [DepartureDecision.Inconclusive].
         * With EXPONENTIAL backoff starting at 15s the retries fire at ~15s, ~30s, ~60s —
         * a ~2 min window for AR delivery and for the vehicle to accelerate past the
         * departure threshold.
         */
        private const val MAX_INCONCLUSIVE_RETRIES = 3
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

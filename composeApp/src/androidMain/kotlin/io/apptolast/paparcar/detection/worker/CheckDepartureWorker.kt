@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.detection.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.usecase.location.GetOneLocationUseCase
import io.apptolast.paparcar.domain.usecase.parking.DetectParkingDepartureUseCase
import kotlin.time.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that decides — after a geofence-exit — whether the user
 * actually drove away in their own car, and if so enqueues [ReportSpotWorker]
 * to publish the freed spot to Firebase.
 *
 * This worker replaces the previous approach of enqueuing [ReportSpotWorker]
 * directly from [GeofenceBroadcastReceiver], which had no way to distinguish
 * "user went for a walk" from "user drove away in their car".
 *
 * Decision logic is fully delegated to [DetectParkingDepartureUseCase]:
 * - Saved parking session must exist and match [KEY_GEOFENCE_ID].
 * - IN_VEHICLE_ENTER must have occurred within the configured time window.
 * - Speed (from a fresh GPS reading) must exceed the departure threshold if available.
 *
 * No network constraint: this is a local decision, not a network operation.
 */
class CheckDepartureWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val detectParkingDeparture: DetectParkingDepartureUseCase by inject()
    private val getOneLocation: GetOneLocationUseCase by inject()
    private val departureEventBus: DepartureEventBus by inject()

    override suspend fun doWork(): Result {
        val geofenceId = inputData.getString(KEY_GEOFENCE_ID)
            ?: return Result.success() // missing data — no-op

        val exitTimestampMs = inputData.getLong(KEY_EXIT_TIMESTAMP, -1L)
            .takeIf { it > 0L } ?: Clock.System.now().toEpochMilliseconds()

        // Attempt a fresh location read to derive speed.
        // null is acceptable — DetectParkingDepartureUseCase skips the speed check.
        val speedKmh = getOneLocation()?.speed?.times(3.6f)

        val shouldPublish = detectParkingDeparture(
            geofenceId = geofenceId,
            exitTimestampMs = exitTimestampMs,
            currentSpeedKmh = speedKmh,
        )

        if (!shouldPublish) return Result.success()

        // Confirmed departure: reset bus state and schedule the Firebase upload.
        departureEventBus.reset()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "${ReportSpotWorker.TAG}_$geofenceId",
            ExistingWorkPolicy.REPLACE,
            ReportSpotWorker.buildRequest(),
        )

        return Result.success()
    }

    companion object {
        const val TAG = "CheckDepartureWorker"
        private const val KEY_GEOFENCE_ID = "geofence_id"
        private const val KEY_EXIT_TIMESTAMP = "exit_timestamp_ms"

        fun buildRequest(geofenceId: String, exitTimestampMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<CheckDepartureWorker>()
                .setInputData(
                    workDataOf(
                        KEY_GEOFENCE_ID to geofenceId,
                        KEY_EXIT_TIMESTAMP to exitTimestampMs,
                    )
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
    }
}
@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.detection.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.service.GeofenceManager
import io.apptolast.paparcar.domain.usecase.location.GetOneLocationUseCase
import io.apptolast.paparcar.domain.usecase.parking.ClearUserParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.DepartureDecision
import io.apptolast.paparcar.domain.usecase.parking.DetectParkingDepartureUseCase
import io.apptolast.paparcar.domain.usecase.parking.GetUserParkingUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import kotlin.time.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Decides whether a geofence-exit represents a real car departure and, if so,
 * clears the saved parking session and schedules a "spot released" report.
 *
 * Decision logic is fully delegated to [DetectParkingDepartureUseCase]:
 * - Saved parking session must exist and match [KEY_GEOFENCE_ID].
 * - IN_VEHICLE_ENTER must have occurred within the configured time window.
 * - Speed (from a fresh GPS reading) must exceed the departure threshold if available.
 *
 * No network constraint: the departure check is purely local.
 */
class DepartureDetectionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val detectParkingDeparture: DetectParkingDepartureUseCase by inject()
    private val getOneLocation: GetOneLocationUseCase by inject()
    private val departureEventBus: DepartureEventBus by inject()
    private val getUserParking: GetUserParkingUseCase by inject()
    private val clearUserParking: ClearUserParkingUseCase by inject()
    private val reportSpotReleased: ReportSpotReleasedUseCase by inject()
    private val geofenceService: GeofenceManager by inject()

    override suspend fun doWork(): Result {
        val geofenceId = inputData.getString(KEY_GEOFENCE_ID)
            ?: return Result.success() // missing data — no-op

        val exitTimestampMs = inputData.getLong(KEY_EXIT_TIMESTAMP, -1L)
            .takeIf { it > 0L } ?: Clock.System.now().toEpochMilliseconds()

        val speedKmh = getOneLocation()?.speed?.times(3.6f)

        return when (
            detectParkingDeparture(
                geofenceId = geofenceId,
                exitTimestampMs = exitTimestampMs,
                currentSpeedKmh = speedKmh,
            )
        ) {
            DepartureDecision.Confirmed -> {
                val session = getUserParking()
                val spotId = session?.id ?: "auto_${Clock.System.now().toEpochMilliseconds()}"
                val lat = session?.location?.latitude
                val lon = session?.location?.longitude
                // Clear parking BEFORE resetting the bus so that if the clear fails
                // and we retry, DetectParkingDepartureUseCase can still read
                // lastVehicleEnteredAt and return Confirmed again instead of Inconclusive.
                clearUserParking().onFailure { return Result.retry() }
                departureEventBus.reset()
                if (lat != null && lon != null) {
                    reportSpotReleased(lat, lon, spotId)
                }
                // Remove the geofence so Play Services doesn't keep monitoring it and
                // re-firing exits after the session is already cleared.
                geofenceService.removeGeofence(geofenceId)
                Result.success()
            }

            DepartureDecision.Rejected -> Result.success()

            DepartureDecision.Inconclusive -> {
                // Session + geofence match, but IN_VEHICLE_ENTER hasn't arrived yet.
                // Retry — the Transitions API can take up to ~2 min on some devices.
                if (runAttemptCount < MAX_INCONCLUSIVE_RETRIES) Result.retry()
                else Result.success()
            }
        }
    }

    companion object {
        const val TAG = "DepartureDetectionWorker"
        private const val KEY_GEOFENCE_ID = "geofence_id"
        private const val KEY_EXIT_TIMESTAMP = "exit_timestamp_ms"

        /**
         * Maximum retries when the decision is [DepartureDecision.Inconclusive].
         * Inconclusive covers two scenarios:
         *  1. IN_VEHICLE_ENTER not yet delivered (AR API can take up to ~2 min).
         *  2. IN_VEHICLE_ENTER is within window but GPS speed is still below threshold
         *     (user leaving a garage ramp or a tight urban exit slowly).
         * With EXPONENTIAL backoff starting at 15s the retries fire at ~15s, ~30s, ~60s
         * giving a total window of ~2 min — enough for AR delivery and for the vehicle
         * to accelerate above the departure threshold.
         */
        private const val MAX_INCONCLUSIVE_RETRIES = 3

        fun buildRequest(geofenceId: String, exitTimestampMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<DepartureDetectionWorker>()
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

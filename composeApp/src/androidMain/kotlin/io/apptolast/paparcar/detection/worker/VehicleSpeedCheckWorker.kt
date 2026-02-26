@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.detection.worker

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import io.apptolast.paparcar.BuildConfig
import io.apptolast.paparcar.detection.service.DrivingTrackingService
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.notification.NotificationPort
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.usecase.location.GetOneLocationUseCase
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * One-shot worker that fires ~[DELAY_MS] after a STILL_EXIT transition.
 *
 * Serves as a speed-based fallback for IN_VEHICLE_ENTER detection: the
 * Activity Recognition Transitions API may take up to 5 minutes to confirm
 * IN_VEHICLE, making short trips (< ~5 min) invisible to the detection pipeline.
 *
 * If GPS speed exceeds [ParkingDetectionConfig.vehicleSpeedFallbackThresholdKmh]
 * when this worker runs, the user is almost certainly driving and
 * [DepartureEventBus.onVehicleEntered] + [DrivingTrackingService] are triggered
 * synthetically, exactly as [ActivityTransitionReceiver] would on a real
 * IN_VEHICLE_ENTER event.
 *
 * Cancelled immediately when a real IN_VEHICLE_ENTER arrives, preventing
 * double-triggering.
 */
class VehicleSpeedCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val getOneLocation: GetOneLocationUseCase by inject()
    private val departureEventBus: DepartureEventBus by inject()
    private val config: ParkingDetectionConfig by inject()
    private val notificationPort: NotificationPort by inject()

    override suspend fun doWork(): Result {
        val speedKmh = getOneLocation()?.speed?.times(3.6f)

        if (BuildConfig.DEBUG) {
            notificationPort.showDebug(
                "SpeedCheck: ${speedKmh?.let { "%.1f km/h".format(it) } ?: "no fix"}"
            )
        }

        if (speedKmh == null || speedKmh < config.vehicleSpeedFallbackThresholdKmh) {
            return Result.success()
        }

        // Speed confirms vehicle movement — synthesise IN_VEHICLE_ENTER
        departureEventBus.onVehicleEntered(Clock.System.now().toEpochMilliseconds())
        applicationContext.startForegroundService(
            Intent(applicationContext, DrivingTrackingService::class.java).apply {
                action = DrivingTrackingService.ACTION_START_TRACKING
            }
        )

        if (BuildConfig.DEBUG) {
            notificationPort.showDebug("SpeedCheck: fallback IN_VEHICLE @ ${"%.1f".format(speedKmh)} km/h")
        }

        return Result.success()
    }

    companion object {
        const val TAG = "VehicleSpeedCheckWorker"

        /** Delay before checking speed, giving the Transitions API time to fire naturally first. */
        private const val DELAY_MS = 60_000L

        fun buildRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<VehicleSpeedCheckWorker>()
                .setInitialDelay(DELAY_MS, TimeUnit.MILLISECONDS)
                .addTag(TAG)
                .build()
    }
}
package io.apptolast.paparcar.detection.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import io.apptolast.paparcar.BuildConfig
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.notification.NotificationPort
import io.apptolast.paparcar.domain.usecase.notification.NotifySpotUploadingUseCase
import io.apptolast.paparcar.domain.usecase.parking.ClearUserParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.GetUserParkingUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Guaranteed delivery of a "spot released" report to Firebase.
 *
 * Input data: none required — always reports the current active [ParkingSession] from Room.
 * Constraints: NETWORK_CONNECTED.
 * Backoff: EXPONENTIAL starting at 30s, up to [MAX_RETRY_ATTEMPTS] attempts.
 */
class ReportSpotWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val getUserParking: GetUserParkingUseCase by inject()
    private val clearUserParking: ClearUserParkingUseCase by inject()
    private val reportSpotReleased: ReportSpotReleasedUseCase by inject()
    private val notifySpotUploading: NotifySpotUploadingUseCase by inject()
    private val notificationPort: NotificationPort by inject()

    override suspend fun doWork(): Result {
        val session = getUserParking()
            ?: return Result.success() // session already cleared — nothing to report

        val spot = Spot(
            id = session.id,
            location = GpsPoint(
                latitude = session.location.latitude,
                longitude = session.location.longitude,
                accuracy = session.location.accuracy,
                timestamp = session.location.timestamp,
                speed = 0f,
            ),
            reportedBy = "anonymous",
            isActive = true, // spot is free for other users
        )

        notifySpotUploading()

        return reportSpotReleased(spot).fold(
            onSuccess = {
                val cleared = clearUserParking()
                if (cleared.isFailure) {
                    return if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry()
                    else Result.failure()
                }
                if (BuildConfig.DEBUG) { notificationPort.showDebug("Plaza publicada como libre") }
                Result.success()
            },
            onFailure = {
                if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry()
                else Result.failure()
            },
        )
    }

    companion object {
        const val TAG = "ReportSpotWorker"
        private const val MAX_RETRY_ATTEMPTS = 5

        fun buildRequest(): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ReportSpotWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
    }
}

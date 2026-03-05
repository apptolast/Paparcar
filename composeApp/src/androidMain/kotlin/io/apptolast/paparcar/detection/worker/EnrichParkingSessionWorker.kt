package io.apptolast.paparcar.detection.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.usecase.location.GetLocationInfoUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Enriches a saved [UserParking] session with geocoder address + POI data.
 *
 * Runs off the critical parking-confirmation path so [ConfirmParkingUseCase]
 * can return immediately after saving the base session.
 *
 * Input data: [KEY_SESSION_ID], [KEY_LAT], [KEY_LON].
 * Constraints: NETWORK_CONNECTED.
 * Backoff: EXPONENTIAL starting at 30 s, up to [MAX_RETRIES] attempts.
 */
class EnrichParkingSessionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val getLocationInfo: GetLocationInfoUseCase by inject()
    private val userParkingRepository: UserParkingRepository by inject()

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        val lat = inputData.getDouble(KEY_LAT, 0.0)
        val lon = inputData.getDouble(KEY_LON, 0.0)

        val info = getLocationInfo(lat, lon).getOrNull()
            ?: return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()

        return userParkingRepository
            .updateLocationInfo(sessionId, info.address, info.placeInfo)
            .fold(
                onSuccess = { Result.success() },
                onFailure = { if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure() },
            )
    }

    companion object {
        const val TAG = "EnrichParkingSessionWorker"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val MAX_RETRIES = 3

        fun buildRequest(sessionId: String, lat: Double, lon: Double): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<EnrichParkingSessionWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInputData(
                    workDataOf(
                        KEY_SESSION_ID to sessionId,
                        KEY_LAT to lat,
                        KEY_LON to lon,
                    )
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
    }
}

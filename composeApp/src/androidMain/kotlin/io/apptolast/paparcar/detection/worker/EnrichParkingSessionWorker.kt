package io.apptolast.paparcar.detection.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.usecase.location.GetLocationInfoUseCase
import kotlinx.coroutines.flow.catch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Enriches a saved [UserParking] session with geocoder address + POI data.
 *
 * Uses [GetLocationInfoUseCase] which emits in two steps:
 *  1. Address (local geocoder, instant) — written to DB immediately.
 *  2. Place info (network, best-effort) — written to DB if available.
 *
 * No network constraint: the geocoder works offline. The places lookup is
 * best-effort and silently skipped on failure.
 *
 * Input data: [KEY_SESSION_ID], [KEY_LAT], [KEY_LON].
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
        val lat = inputData.getDouble(KEY_LAT, Double.NaN).takeIf { !it.isNaN() } ?: return Result.failure()
        val lon = inputData.getDouble(KEY_LON, Double.NaN).takeIf { !it.isNaN() } ?: return Result.failure()

        var addressSaved = false

        getLocationInfo(lat, lon)
            .catch { /* geocoder failure — will retry below */ }
            .collect { info ->
                userParkingRepository
                    .updateLocationInfo(sessionId, info.address, info.placeInfo)
                    .onSuccess { addressSaved = true }
            }

        return when {
            addressSaved -> Result.success()
            runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val TAG = "EnrichParkingSessionWorker"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val MAX_RETRIES = 3

        fun buildRequest(sessionId: String, lat: Double, lon: Double): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<EnrichParkingSessionWorker>()
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
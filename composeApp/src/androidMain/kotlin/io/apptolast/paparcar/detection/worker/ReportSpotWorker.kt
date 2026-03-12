@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.detection.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.apptolast.paparcar.BuildConfig
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.notification.NotificationPort
import io.apptolast.paparcar.domain.repository.SpotRepository
import kotlin.time.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Guaranteed delivery of a "spot released" report to Firebase.
 *
 * All data (spot ID, coordinates, address, place info) is provided via [inputData]
 * — the worker does not read from the local database, so it runs safely even after
 * the parking session has been cleared.
 *
 * Constraints: NETWORK_CONNECTED.
 * Backoff: EXPONENTIAL starting at 30s, up to [MAX_RETRY_ATTEMPTS] attempts.
 */
class ReportSpotWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val spotRepository: SpotRepository by inject()
    private val notificationPort: NotificationPort by inject()

    override suspend fun doWork(): Result {
        val spotId = inputData.getString(KEY_SPOT_ID) ?: return Result.failure()
        val lat = inputData.getDouble(KEY_LAT, Double.NaN).takeIf { !it.isNaN() } ?: return Result.failure()
        val lon = inputData.getDouble(KEY_LON, Double.NaN).takeIf { !it.isNaN() } ?: return Result.failure()

        val spot = Spot(
            id = spotId,
            location = GpsPoint(
                latitude = lat,
                longitude = lon,
                accuracy = 0f,
                timestamp = inputData.getLong(KEY_TIMESTAMP, Clock.System.now().toEpochMilliseconds()),
                speed = 0f,
            ),
            reportedBy = "anonymous",
            address = inputData.toAddressInfo(),
            placeInfo = inputData.toPlaceInfo(),
        )

        notificationPort.showSpotUploading()

        return spotRepository.reportSpotReleased(spot).fold(
            onSuccess = {
                notificationPort.dismiss(NotificationPort.UPLOAD_NOTIFICATION_ID)
                if (BuildConfig.DEBUG) notificationPort.showDebug("Plaza publicada como libre")
                Result.success()
            },
            onFailure = {
                if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry()
                else {
                    notificationPort.dismiss(NotificationPort.UPLOAD_NOTIFICATION_ID)
                    Result.failure()
                }
            },
        )
    }

    companion object {
        const val TAG = "ReportSpotWorker"
        private const val MAX_RETRY_ATTEMPTS = 5

        private const val KEY_SPOT_ID = "spot_id"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_ADDRESS_STREET = "address_street"
        private const val KEY_ADDRESS_CITY = "address_city"
        private const val KEY_ADDRESS_REGION = "address_region"
        private const val KEY_ADDRESS_COUNTRY = "address_country"
        private const val KEY_PLACE_NAME = "place_name"
        private const val KEY_PLACE_CATEGORY = "place_category"

        fun buildRequest(
            spotId: String,
            lat: Double,
            lon: Double,
            address: AddressInfo?,
            placeInfo: PlaceInfo?,
        ): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ReportSpotWorker>()
                .setInputData(
                    workDataOf(
                        KEY_SPOT_ID to spotId,
                        KEY_LAT to lat,
                        KEY_LON to lon,
                        KEY_TIMESTAMP to Clock.System.now().toEpochMilliseconds(),
                        KEY_ADDRESS_STREET to address?.street,
                        KEY_ADDRESS_CITY to address?.city,
                        KEY_ADDRESS_REGION to address?.region,
                        KEY_ADDRESS_COUNTRY to address?.country,
                        KEY_PLACE_NAME to placeInfo?.name,
                        KEY_PLACE_CATEGORY to placeInfo?.category?.name,
                    )
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()

        private fun Data.toAddressInfo(): AddressInfo? {
            val street = getString(KEY_ADDRESS_STREET)
            val city = getString(KEY_ADDRESS_CITY)
            val region = getString(KEY_ADDRESS_REGION)
            val country = getString(KEY_ADDRESS_COUNTRY)
            return if (street != null || city != null || region != null || country != null)
                AddressInfo(street = street, city = city, region = region, country = country)
            else null
        }

        private fun Data.toPlaceInfo(): PlaceInfo? {
            val name = getString(KEY_PLACE_NAME) ?: return null
            val cat = getString(KEY_PLACE_CATEGORY) ?: return null
            return runCatching { PlaceInfo(name, PlaceCategory.valueOf(cat)) }.getOrNull()
        }
    }
}

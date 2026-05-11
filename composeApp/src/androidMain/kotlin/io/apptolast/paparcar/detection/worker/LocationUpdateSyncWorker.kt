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
import io.apptolast.paparcar.data.datasource.remote.UserProfileDataSource
import io.apptolast.paparcar.data.datasource.remote.dto.AddressDto
import io.apptolast.paparcar.data.datasource.remote.dto.PlaceInfoDto
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.util.PaparcarLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Pushes geocoder address + POI data to Firestore for an existing parking session.
 *
 * Enqueued by [WorkManagerParkingSyncScheduler.scheduleLocationUpdate] when
 * [UserParkingRepositoryImpl.updateLocationInfo] finishes its Room update. The
 * repository call is now Room-only; this worker handles the Firestore side so
 * [EnrichParkingSessionWorker] is no longer blocked on a network write. [PIPE-002]
 *
 * Input data: [KEY_USER_ID], [KEY_SESSION_ID], plus individual address/placeInfo fields.
 * Constraints: NETWORK_CONNECTED. Backoff: exponential 30 s base.
 */
class LocationUpdateSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val userProfileDataSource: UserProfileDataSource by inject()

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        val address = inputData.toAddressDto()
        val placeInfo = inputData.toPlaceInfoDto()

        PaparcarLogger.d(TAG, "▶ LocationUpdateSyncWorker.doWork session=$sessionId attempt=$runAttemptCount")

        return runCatching {
            userProfileDataSource.updateParkingSessionLocation(userId, sessionId, address, placeInfo)
        }.fold(
            onSuccess = {
                PaparcarLogger.d(TAG, "■ LocationUpdateSyncWorker SUCCESS session=$sessionId")
                Result.success()
            },
            onFailure = { e ->
                if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    PaparcarLogger.w(TAG, "⚠ retrying session=$sessionId attempt=$runAttemptCount/$MAX_RETRY_ATTEMPTS", e)
                    Result.retry()
                } else {
                    PaparcarLogger.e(TAG, "✗ giving up session=$sessionId after $MAX_RETRY_ATTEMPTS retries", e)
                    Result.failure()
                }
            },
        )
    }

    companion object {
        const val TAG = "PARKDIAG/LocationUpdateSyncWorker"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_BACKOFF_SECONDS = 30L

        private const val KEY_USER_ID = "userId"
        private const val KEY_SESSION_ID = "sessionId"
        private const val KEY_ADDRESS_STREET = "addr_street"
        private const val KEY_ADDRESS_CITY = "addr_city"
        private const val KEY_ADDRESS_REGION = "addr_region"
        private const val KEY_ADDRESS_COUNTRY = "addr_country"
        private const val KEY_PLACE_NAME = "place_name"
        private const val KEY_PLACE_CATEGORY = "place_category"

        fun buildRequest(
            userId: String,
            sessionId: String,
            address: AddressInfo?,
            placeInfo: PlaceInfo?,
        ): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<LocationUpdateSyncWorker>()
                .setInputData(
                    workDataOf(
                        KEY_USER_ID to userId,
                        KEY_SESSION_ID to sessionId,
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
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()

        private fun androidx.work.Data.toAddressDto(): AddressDto? {
            val street = getString(KEY_ADDRESS_STREET)
            val city = getString(KEY_ADDRESS_CITY)
            val region = getString(KEY_ADDRESS_REGION)
            val country = getString(KEY_ADDRESS_COUNTRY)
            return if (street == null && city == null && region == null && country == null) null
            else AddressDto(street = street, city = city, region = region, country = country)
        }

        private fun androidx.work.Data.toPlaceInfoDto(): PlaceInfoDto? {
            val name = getString(KEY_PLACE_NAME) ?: return null
            val category = getString(KEY_PLACE_CATEGORY) ?: return null
            return PlaceInfoDto(name = name, category = category)
        }
    }
}

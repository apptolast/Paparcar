@file:OptIn(kotlin.time.ExperimentalTime::class)

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
import io.apptolast.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import io.apptolast.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.util.PaparcarLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Propagates a confirmed [UserParking] session to Firestore in the background.
 *
 * Off the critical path of [ConfirmParkingUseCase] so the foreground service can
 * stop the moment the local Room insert finishes. Survives process death,
 * network outages, and OEM-aggressive background management because WorkManager
 * persists the request and retries with exponential backoff.
 *
 * Inputs (passed via [androidx.work.Data]):
 * - [KEY_USER_ID] — Firebase auth uid the session belongs to.
 * - [KEY_NEW_SESSION_*] — every field of the new session, since the worker is
 *   self-contained (no Room reads — same pattern as [ReportSpotWorker]).
 * - [KEY_PREVIOUS_SESSION_ID] — id of the previous active session to mark as
 *   `isActive=false` in Firestore (mirrors `dao.clearActive()`). Optional.
 *
 * Constraints: `NETWORK_CONNECTED`. Backoff: exponential 30 s base.
 * Unique work name: `parking_sync_$sessionId`, policy `REPLACE`.
 *
 * @see io.apptolast.paparcar.domain.service.ParkingSyncScheduler
 */
class ParkingSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val userProfileDataSource: RemoteUserProfileDataSource by inject()

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()
        val newSession = inputData.toParkingHistoryDto() ?: return Result.failure()
        val previousSessionId = inputData.getString(KEY_PREVIOUS_SESSION_ID)

        PaparcarLogger.d(TAG, "▶ ParkingSyncWorker.doWork session=${newSession.id} previous=$previousSessionId attempt=$runAttemptCount")

        return runCatching {
            // Mark previous active session as inactive in Firestore via update()
            // (not set()) so we only flip the isActive flag without overwriting
            // existing coordinates or other fields. [PIPE-001 bugfix in PIPE-002]
            previousSessionId?.let { prevId ->
                userProfileDataSource.updateParkingSessionActiveFlag(userId, prevId, false)
            }
            userProfileDataSource.saveParkingSession(userId, newSession)
        }.fold(
            onSuccess = {
                PaparcarLogger.d(TAG, "■ ParkingSyncWorker SUCCESS session=${newSession.id}")
                Result.success()
            },
            onFailure = { e ->
                if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    PaparcarLogger.w(TAG, "⚠ retrying session=${newSession.id} attempt=$runAttemptCount/$MAX_RETRY_ATTEMPTS", e)
                    Result.retry()
                } else {
                    PaparcarLogger.e(TAG, "✗ giving up session=${newSession.id} after $MAX_RETRY_ATTEMPTS retries", e)
                    Result.failure()
                }
            },
        )
    }

    companion object {
        const val TAG = "PARKDIAG/SyncWorker"
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val INITIAL_BACKOFF_SECONDS = 30L

        private const val KEY_USER_ID = "userId"
        private const val KEY_PREVIOUS_SESSION_ID = "previousSessionId"

        // New-session payload — keep in lockstep with [ParkingHistoryDto].
        private const val KEY_NEW_SESSION_ID = "session_id"
        private const val KEY_NEW_SESSION_VEHICLE_ID = "session_vehicle_id"
        private const val KEY_NEW_SESSION_LAT = "session_lat"
        private const val KEY_NEW_SESSION_LON = "session_lon"
        private const val KEY_NEW_SESSION_ACCURACY = "session_accuracy"
        private const val KEY_NEW_SESSION_TIMESTAMP = "session_timestamp"
        private const val KEY_NEW_SESSION_IS_ACTIVE = "session_is_active"
        private const val KEY_NEW_SESSION_SPOT_ID = "session_spot_id"
        private const val KEY_NEW_SESSION_GEOFENCE_ID = "session_geofence_id"
        private const val KEY_NEW_SESSION_DETECTION_RELIABILITY = "session_detection_reliability"

        fun buildRequest(
            userId: String,
            session: UserParking,
            previousSessionId: String?,
        ): OneTimeWorkRequest {
            val data = workDataOf(
                KEY_USER_ID to userId,
                KEY_PREVIOUS_SESSION_ID to previousSessionId,
                KEY_NEW_SESSION_ID to session.id,
                KEY_NEW_SESSION_VEHICLE_ID to session.vehicleId,
                KEY_NEW_SESSION_LAT to session.location.latitude,
                KEY_NEW_SESSION_LON to session.location.longitude,
                KEY_NEW_SESSION_ACCURACY to session.location.accuracy.toDouble(),
                KEY_NEW_SESSION_TIMESTAMP to session.location.timestamp,
                KEY_NEW_SESSION_IS_ACTIVE to session.isActive,
                KEY_NEW_SESSION_SPOT_ID to session.spotId,
                KEY_NEW_SESSION_GEOFENCE_ID to session.geofenceId,
                // NaN sentinel for "absent" — workDataOf does not preserve nulls for primitives,
                // and `detectionReliability` may legitimately be null for manually-reported spots. [MAPPER-003]
                KEY_NEW_SESSION_DETECTION_RELIABILITY to (session.detectionReliability?.toDouble() ?: Double.NaN),
            )
            return OneTimeWorkRequestBuilder<ParkingSyncWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
        }

        private fun androidx.work.Data.toParkingHistoryDto(): ParkingHistoryDto? {
            val id = getString(KEY_NEW_SESSION_ID) ?: return null
            return ParkingHistoryDto(
                id = id,
                userId = getString(KEY_USER_ID) ?: "",
                vehicleId = getString(KEY_NEW_SESSION_VEHICLE_ID),
                latitude = getDouble(KEY_NEW_SESSION_LAT, Double.NaN).takeIf { !it.isNaN() } ?: return null,
                longitude = getDouble(KEY_NEW_SESSION_LON, Double.NaN).takeIf { !it.isNaN() } ?: return null,
                accuracy = getDouble(KEY_NEW_SESSION_ACCURACY, 0.0).toFloat(),
                timestamp = getLong(KEY_NEW_SESSION_TIMESTAMP, 0L),
                isActive = getBoolean(KEY_NEW_SESSION_IS_ACTIVE, false),
                spotId = getString(KEY_NEW_SESSION_SPOT_ID),
                geofenceId = getString(KEY_NEW_SESSION_GEOFENCE_ID),
                detectionReliability = getDouble(KEY_NEW_SESSION_DETECTION_RELIABILITY, Double.NaN)
                    .takeUnless { it.isNaN() }?.toFloat(),
            )
        }
    }
}

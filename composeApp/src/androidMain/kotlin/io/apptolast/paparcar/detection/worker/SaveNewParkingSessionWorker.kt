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
import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import io.apptolast.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
 * The userId is resolved inside [doWork] via [AuthRepository] injected through Koin —
 * this removes the need for a coroutine scope in the scheduler and makes the enqueue
 * path fully synchronous. If the user has logged out between enqueue and execution
 * the worker returns [Result.failure] (data-less retry would be meaningless).
 *
 * Inputs (passed via [androidx.work.Data]):
 * - [KEY_NEW_SESSION_*] — every field of the new session, since the worker is
 *   self-contained (no Room reads — same pattern as [ReportSpotWorker]).
 * - [KEY_PREVIOUS_SESSION_ID] — id of the previous active session to mark as
 *   `isActive=false` in Firestore (mirrors `dao.clearActive()`). Optional.
 *
 * Constraints: `NETWORK_CONNECTED`. Backoff: exponential 30 s base.
 * Unique work name: `parking_chain_$sessionId`, policy `REPLACE`.
 *
 * @see io.apptolast.paparcar.domain.service.ParkingSyncScheduler
 */
class SaveNewParkingSessionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val userProfileDataSource: RemoteUserProfileDataSource by inject()
    private val authRepository: AuthRepository by inject()

    override suspend fun doWork(): Result {
        val userId = authRepository.getCurrentSession()?.userId
            ?: return Result.failure()
        val newSession = inputData.toParkingHistoryDto(userId) ?: return Result.failure()
        val previousSessionId = inputData.getString(KEY_PREVIOUS_SESSION_ID)

        PaparcarLogger.d(TAG, "▶ SaveNewParkingSessionWorker.doWork session=${newSession.id} previous=$previousSessionId attempt=$runAttemptCount")

        return runCatching {
            // NonCancellable: if the OEM kills the WorkManager Job mid-flight, the Firestore
            // writes complete anyway. Without this, JobCancellationException leaves Room and
            // Firestore inconsistent — session saved locally but invisible to other users. [BUG-WORKER-002]
            withContext(NonCancellable) {
                // update() not set() — only flip the isActive flag without overwriting
                // existing coordinates or other fields. [PIPE-001 bugfix in PIPE-002]
                previousSessionId?.let { prevId ->
                    userProfileDataSource.clearParkingSessionActiveFlag(userId, prevId)
                }
                userProfileDataSource.saveParkingSession(userId, newSession)
            }
        }.fold(
            onSuccess = {
                PaparcarLogger.d(TAG, "■ SaveNewParkingSessionWorker SUCCESS session=${newSession.id}")
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
        const val TAG = "PARKDIAG/SaveNewParkingSessionWorker"
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val INITIAL_BACKOFF_SECONDS = 30L

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
        // [BUG-SIZE-PARITY] These were missing — the worker dropped the vehicle size/carbody on the
        // Firestore sync (Room kept them), so parkingHistory and the published Spot showed
        // "size unspecified", and syncFromRemote then overwrote Room's size with null. [VEHICLE-CATEGORIZATION-001]
        private const val KEY_NEW_SESSION_SIZE_CATEGORY = "session_size_category"
        private const val KEY_NEW_SESSION_CARBODY_TYPE = "session_carbody_type"
        // [DET-PIN-PROVENANCE-001] Pin provenance — the ARM trigger + the confirmation PATH that
        // placed this parking, carried to Firestore so a remote diagnostic can attribute the pin.
        private const val KEY_NEW_SESSION_ARM_EVIDENCE = "session_arm_evidence"
        private const val KEY_NEW_SESSION_DETECTION_PATH = "session_detection_path"

        fun buildRequest(
            session: UserParking,
            previousSessionId: String?,
        ): OneTimeWorkRequest {
            val data = workDataOf(
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
                // [BUG-SIZE-PARITY] carry the vehicle size/carbody through to Firestore.
                KEY_NEW_SESSION_SIZE_CATEGORY to session.sizeCategory?.name,
                KEY_NEW_SESSION_CARBODY_TYPE to session.carbodyType?.name,
                // [DET-PIN-PROVENANCE-001] carry the pin provenance through to Firestore.
                KEY_NEW_SESSION_ARM_EVIDENCE to session.armEvidence,
                KEY_NEW_SESSION_DETECTION_PATH to session.detectionPath,
            )
            return OneTimeWorkRequestBuilder<SaveNewParkingSessionWorker>()
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

        private fun androidx.work.Data.toParkingHistoryDto(userId: String): ParkingHistoryDto? {
            val id = getString(KEY_NEW_SESSION_ID) ?: return null
            return ParkingHistoryDto(
                id = id,
                userId = userId,
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
                sizeCategory = getString(KEY_NEW_SESSION_SIZE_CATEGORY),
                carbodyType = getString(KEY_NEW_SESSION_CARBODY_TYPE),
                armEvidence = getString(KEY_NEW_SESSION_ARM_EVIDENCE),
                detectionPath = getString(KEY_NEW_SESSION_DETECTION_PATH),
            )
        }
    }
}

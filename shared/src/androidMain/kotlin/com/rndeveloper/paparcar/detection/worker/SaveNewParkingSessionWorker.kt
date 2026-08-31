@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.detection.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import com.rndeveloper.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import com.rndeveloper.paparcar.data.mapper.toParkingHistoryDto
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Clock
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
 * - [KEY_SESSION_JSON] — the whole [ParkingHistoryDto], serialised. The worker stays
 *   self-contained (no Room reads — same pattern as [ReportSpotWorker]) without a
 *   per-field list to keep in step with the dto. [SYNC-A-PARKING-MUST-TRAVEL-WHOLE-001]
 * - [KEY_PREVIOUS_SESSION_ID] — id of the previous active session to mark as
 *   `isActive=false` in Firestore (mirrors `dao.clearActive()`). Optional.
 *
 * ⚠️ [RemoteUserProfileDataSource.saveParkingSession] writes with `set()` and NO merge, so this
 * payload is not "what to update" — it is the document. Anything it fails to carry is not omitted,
 * it is erased.
 *
 * Constraints: `NETWORK_CONNECTED`. Backoff: exponential 30 s base.
 * Unique work name: `parking_chain_$sessionId`, policy `REPLACE`.
 *
 * @see com.rndeveloper.paparcar.domain.service.ParkingSyncScheduler
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

        /**
         * The whole [ParkingHistoryDto] as JSON. No per-field keys, and therefore no list to keep
         * in lockstep with anything. [SYNC-A-PARKING-MUST-TRAVEL-WHOLE-001]
         *
         * The per-field keys this replaces carried their own scars, and they are the argument:
         * `session_size_category`/`session_carbody_type` were added by [BUG-SIZE-PARITY] after the
         * worker dropped the vehicle size on sync (history and the published Spot read "size
         * unspecified", and `syncFromRemote` then overwrote Room's size with null), and
         * `session_arm_evidence`/`session_detection_path` by [DET-PIN-PROVENANCE-001] after a
         * phantom pin could not be attributed to its trigger. Each fix added the one field it
         * needed. None of them asked why a field had to be remembered at all.
         */
        internal const val KEY_SESSION_JSON = "session_json"

        /**
         * [SYNC-A-PARKING-MUST-TRAVEL-WHOLE-001] The WHOLE dto, as one JSON string.
         *
         * This used to be a hand-written list of 15 `workDataOf` entries, re-assembled field by
         * field on the other side. The ten fields nobody remembered to add — `zoneRadiusMeters`,
         * `spotType`, `address`, `placeInfo`, `routePolyline`, `routeSnapped`,
         * `routeInferredSpans`/`Resolution`, `routeDistanceMeters`, `endedAtMs`, `publishedSpot`,
         * `updatedAt` — were not merely omitted: [RemoteUserProfileDataSource.saveParkingSession]
         * does `set()` WITHOUT merge, so every one of them was WRITTEN as null/false over whatever
         * the document already held.
         *
         * The list was documented as "every field of the new session" and asked to be kept "in
         * lockstep with ParkingHistoryDto". It had already fallen out of lockstep three times —
         * MAPPER-001 (`detectionReliability`), MAPPER-002 (`vehicleId`) and now `zoneRadiusMeters`,
         * whose own KDoc in the dto says the doubt travels to remote. Both earlier fixes added the
         * missing field to the list, which is what guaranteed a fourth. **A comment is not a check.**
         *
         * Serialising the dto itself removes the list: a field added to [ParkingHistoryDto] travels
         * because it is part of the dto, not because someone remembered. The worker stays
         * self-contained (no Room reads) exactly as designed.
         */
        fun buildRequest(
            session: UserParking,
            previousSessionId: String?,
        ): OneTimeWorkRequest {
            // Stamp the enqueue moment as `updatedAt`. The old payload had no field for it, so every
            // document this worker wrote carried `updatedAt = 0L` — and 0 loses every
            // Last-Write-Wins comparison in `UserParkingReconcile`, so the remote mirror could never
            // win over a stale local row. [SYNC-RECONCILE-USERPARKING-001]
            val dto = session.toParkingHistoryDto(
                updatedAt = Clock.System.now().toEpochMilliseconds(),
            )
            val data = workDataOf(
                KEY_PREVIOUS_SESSION_ID to previousSessionId,
                KEY_SESSION_JSON to Json.encodeToString(dto),
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

        /**
         * The dto exactly as it was enqueued, with the CURRENT session's userId stamped on it.
         *
         * The userId is resolved at execution time (not carried in the payload) because the enqueue
         * path is synchronous and has no auth session to read — see the class KDoc. Everything else
         * survives the round-trip untouched, which is the whole point of
         * [SYNC-A-PARKING-MUST-TRAVEL-WHOLE-001].
         */
        internal fun androidx.work.Data.toParkingHistoryDto(userId: String): ParkingHistoryDto? {
            val json = getString(KEY_SESSION_JSON) ?: return null
            val dto = runCatching { Json.decodeFromString<ParkingHistoryDto>(json) }.getOrNull()
                ?: return null
            return dto.copy(userId = userId)
        }
    }
}

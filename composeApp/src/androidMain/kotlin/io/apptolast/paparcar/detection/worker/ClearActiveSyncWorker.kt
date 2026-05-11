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
import io.apptolast.paparcar.domain.util.PaparcarLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Marks a parking session as inactive in Firestore via a targeted update()
 * (only flips the `isActive` field — no other fields overwritten).
 *
 * Enqueued by [WorkManagerParkingSyncScheduler.scheduleClearActive] when
 * [UserParkingRepositoryImpl.clearActive] clears the active session from Room.
 * The repository call is Room-only; this worker handles the Firestore side
 * asynchronously so the release path is never blocked on network I/O. [PIPE-002]
 *
 * Input data: [KEY_USER_ID], [KEY_SESSION_ID].
 * Constraints: NETWORK_CONNECTED. Backoff: exponential 30 s base.
 */
class ClearActiveSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val userProfileDataSource: UserProfileDataSource by inject()

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()

        PaparcarLogger.d(TAG, "▶ ClearActiveSyncWorker.doWork session=$sessionId attempt=$runAttemptCount")

        return runCatching {
            userProfileDataSource.updateParkingSessionActiveFlag(userId, sessionId, false)
        }.fold(
            onSuccess = {
                PaparcarLogger.d(TAG, "■ ClearActiveSyncWorker SUCCESS session=$sessionId")
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
        const val TAG = "PARKDIAG/ClearActiveSyncWorker"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_BACKOFF_SECONDS = 30L

        private const val KEY_USER_ID = "userId"
        private const val KEY_SESSION_ID = "sessionId"

        fun buildRequest(userId: String, sessionId: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ClearActiveSyncWorker>()
                .setInputData(workDataOf(KEY_USER_ID to userId, KEY_SESSION_ID to sessionId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
    }
}

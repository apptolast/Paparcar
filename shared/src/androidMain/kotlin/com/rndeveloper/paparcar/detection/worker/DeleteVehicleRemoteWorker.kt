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
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * [SYNC-A-REMOTE-DELETE-HAS-NO-OUTBOX-BEHIND-IT-001] Deletes a vehicle's remote footprint — the
 * parking-session documents FIRST, then the vehicle document.
 *
 * Enqueued by [WorkManagerParkingSyncScheduler.enqueueDeleteVehicleRemote] when
 * [VehicleRepositoryImpl.deleteVehicle] has already removed the vehicle and its history from Room.
 * This is the one remote write class that cannot ride the repository's fire-and-forget `syncScope`:
 * a delete leaves NO local row to mark `pendingSync` on, so if the process dies before the remote
 * delete lands, nothing remembers it was owed — and the inbound reconcile then pulls the surviving
 * documents straight back into Room. The user's irreversible delete undoes itself. WorkManager
 * persists the request across process death, which is the entire point.
 *
 * The ORDER inside the job is load-bearing (sessions before vehicle): surviving session docs are
 * exactly what the reconcile resurrects as history of a car that no longer exists. On a retry the
 * whole job reruns from the top — both remote deletes are idempotent, so re-deleting already-gone
 * sessions costs nothing and the order still holds.
 *
 * The userId is resolved inside [doWork] via [AuthRepository] injected through Koin. If the user
 * has logged out between enqueue and execution the worker returns [Result.failure]: their remote
 * data is no longer ours to touch, and the account-delete path sweeps everything anyway.
 *
 * Input data: [KEY_VEHICLE_ID].
 * Constraints: NETWORK_CONNECTED. Backoff: exponential 30 s base.
 */
class DeleteVehicleRemoteWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val userProfileDataSource: RemoteUserProfileDataSource by inject()
    private val authRepository: AuthRepository by inject()

    override suspend fun doWork(): Result {
        val userId = authRepository.getCurrentSession()?.userId
            ?: return Result.failure()
        val vehicleId = inputData.getString(KEY_VEHICLE_ID) ?: return Result.failure()

        PaparcarLogger.d(TAG, "▶ DeleteVehicleRemoteWorker.doWork vehicle=$vehicleId attempt=$runAttemptCount")

        return runCatching {
            // NonCancellable: if the OEM kills the WorkManager Job mid-flight, the Firestore
            // deletes complete anyway — a half-deleted footprint is precisely the state whose
            // surviving half the reconcile resurrects. [BUG-WORKER-002]
            withContext(NonCancellable) {
                userProfileDataSource.deleteParkingSessionsForVehicle(userId, vehicleId)
                userProfileDataSource.deleteVehicle(userId, vehicleId)
            }
        }.fold(
            onSuccess = {
                PaparcarLogger.d(TAG, "■ DeleteVehicleRemoteWorker SUCCESS vehicle=$vehicleId")
                Result.success()
            },
            onFailure = { e ->
                if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    PaparcarLogger.w(TAG, "⚠ retrying vehicle=$vehicleId attempt=$runAttemptCount/$MAX_RETRY_ATTEMPTS", e)
                    Result.retry()
                } else {
                    PaparcarLogger.e(TAG, "✗ giving up vehicle=$vehicleId after $MAX_RETRY_ATTEMPTS retries", e)
                    Result.failure()
                }
            },
        )
    }

    companion object {
        const val TAG = "PARKDIAG/DeleteVehicleRemoteWorker"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_BACKOFF_SECONDS = 30L

        private const val KEY_VEHICLE_ID = "vehicleId"

        fun buildRequest(vehicleId: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<DeleteVehicleRemoteWorker>()
                .setInputData(workDataOf(KEY_VEHICLE_ID to vehicleId))
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

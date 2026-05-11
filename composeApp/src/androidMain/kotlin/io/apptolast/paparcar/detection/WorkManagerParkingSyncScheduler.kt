@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.detection

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.detection.worker.ClearActiveSyncWorker
import io.apptolast.paparcar.detection.worker.LocationUpdateSyncWorker
import io.apptolast.paparcar.detection.worker.ParkingSyncWorker
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.service.ParkingSyncScheduler
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android implementation of [ParkingSyncScheduler] backed by WorkManager.
 *
 * All three methods are fire-and-forget: they launch a detached coroutine to
 * resolve the current userId, then enqueue a unique WorkManager request.
 * `ExistingWorkPolicy.REPLACE` makes duplicate enqueues idempotent.
 */
class WorkManagerParkingSyncScheduler(
    private val context: Context,
    private val authRepository: AuthRepository,
) : ParkingSyncScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun schedule(session: UserParking, previousSessionId: String?) {
        scope.launch {
            val userId = authRepository.getCurrentSession()?.userId
            if (userId == null) {
                PaparcarLogger.w(TAG, "schedule() skipped — no auth session for ${session.id}")
                return@launch
            }
            WorkManager.getInstance(context).enqueueUniqueWork(
                "parking_sync_${session.id}",
                ExistingWorkPolicy.REPLACE,
                ParkingSyncWorker.buildRequest(userId, session, previousSessionId),
            )
            PaparcarLogger.d(TAG, "schedule() enqueued session=${session.id} previous=$previousSessionId")
        }
    }

    override fun scheduleClearActive(sessionId: String) {
        scope.launch {
            val userId = authRepository.getCurrentSession()?.userId
            if (userId == null) {
                PaparcarLogger.w(TAG, "scheduleClearActive() skipped — no auth session for $sessionId")
                return@launch
            }
            WorkManager.getInstance(context).enqueueUniqueWork(
                "parking_clear_active_$sessionId",
                ExistingWorkPolicy.REPLACE,
                ClearActiveSyncWorker.buildRequest(userId, sessionId),
            )
            PaparcarLogger.d(TAG, "scheduleClearActive() enqueued session=$sessionId")
        }
    }

    override fun scheduleLocationUpdate(sessionId: String, address: AddressInfo?, placeInfo: PlaceInfo?) {
        scope.launch {
            val userId = authRepository.getCurrentSession()?.userId
            if (userId == null) {
                PaparcarLogger.w(TAG, "scheduleLocationUpdate() skipped — no auth session for $sessionId")
                return@launch
            }
            WorkManager.getInstance(context).enqueueUniqueWork(
                "parking_location_update_$sessionId",
                ExistingWorkPolicy.REPLACE,
                LocationUpdateSyncWorker.buildRequest(userId, sessionId, address, placeInfo),
            )
            PaparcarLogger.d(TAG, "scheduleLocationUpdate() enqueued session=$sessionId")
        }
    }

    private companion object {
        const val TAG = "PARKDIAG/SyncScheduler"
    }
}

@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.detection

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.detection.worker.ParkingSyncWorker
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
 * `enqueueUniqueWork` with `REPLACE` policy keyed by the new session id makes
 * a duplicate enqueue (e.g. retry after a process restart) overwrite cleanly.
 */
class WorkManagerParkingSyncScheduler(
    private val context: Context,
    private val authRepository: AuthRepository,
) : ParkingSyncScheduler {

    // Detached scope: schedule() is non-suspending but needs an auth lookup to
    // resolve the current userId. The lookup must not block the caller (this
    // runs inside the foreground service confirm path), so we fire-and-forget
    // a coroutine on IO. Worker submission is idempotent — if the device dies
    // between Room insert and worker enqueue, the next confirm-parking call
    // will not lose the previous session because Room is the source of truth.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun schedule(session: UserParking, previousSessionId: String?) {
        scope.launch {
            val userId = authRepository.getCurrentSession()?.userId
            if (userId == null) {
                PaparcarLogger.w(TAG, "schedule() skipped — no authenticated session for ${session.id}")
                return@launch
            }
            WorkManager.getInstance(context).enqueueUniqueWork(
                "parking_sync_${session.id}",
                ExistingWorkPolicy.REPLACE,
                ParkingSyncWorker.buildRequest(userId, session, previousSessionId),
            )
            PaparcarLogger.d(TAG, "schedule() enqueued session=${session.id} previous=$previousSessionId user=$userId")
        }
    }

    private companion object {
        const val TAG = "PARKDIAG/SyncScheduler"
    }
}

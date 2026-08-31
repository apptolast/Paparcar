@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.detection

import com.apptolast.customlogin.domain.AuthRepository
import com.rndeveloper.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import com.rndeveloper.paparcar.data.mapper.toAddressDto
import com.rndeveloper.paparcar.data.mapper.toParkingHistoryDto
import com.rndeveloper.paparcar.data.mapper.toPlaceInfoDto
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.service.ParkingSyncScheduler
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * iOS implementation of [ParkingSyncScheduler].
 *
 * Mirrors Android's WorkManager-backed implementation using a coroutine scope
 * with exponential backoff. Same limitations as [IosParkingEnrichmentScheduler]:
 * no cross-process-death persistence — if the app is killed mid-flight the pending
 * write is lost. BGTaskScheduler integration is tracked as a future improvement
 * once the Kotlin/Native ↔ Swift bridge for background tasks is solidified.
 * [IOS-SYNC-001]
 */
class IosParkingSyncScheduler(
    private val remoteDataSource: RemoteUserProfileDataSource,
    private val authRepository: AuthRepository,
) : ParkingSyncScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun enqueueSaveNewParkingSession(session: UserParking, previousSessionId: String?) {
        scope.launch {
            val userId = authRepository.getCurrentSession()?.userId
            if (userId == null) {
                PaparcarLogger.w(TAG, "enqueueSaveNewParkingSession() skipped — no auth session for ${session.id}")
                return@launch
            }
            retrying("saveNewParkingSession:${session.id}") {
                previousSessionId?.let { prevId ->
                    remoteDataSource.clearParkingSessionActiveFlag(userId, prevId)
                }
                // [SYNC-A-PARKING-MUST-TRAVEL-WHOLE-001] Stamp a real `updatedAt`. The default is
                // 0, and 0 loses every Last-Write-Wins comparison in `UserParkingReconcile`, so the
                // document this writes could never win over a stale local row. The Android lane had
                // the same hole and it is fixed there; this is the same invariant, not a copy.
                remoteDataSource.saveParkingSession(
                    userId,
                    session.toParkingHistoryDto(updatedAt = Clock.System.now().toEpochMilliseconds()),
                )
            }
        }
    }

    override fun enqueueClearActiveParkingSession(sessionId: String) {
        scope.launch {
            val userId = authRepository.getCurrentSession()?.userId
            if (userId == null) {
                PaparcarLogger.w(TAG, "enqueueClearActiveParkingSession() skipped — no auth session for $sessionId")
                return@launch
            }
            retrying("clearActiveParkingSession:$sessionId") {
                remoteDataSource.clearParkingSessionActiveFlag(userId, sessionId)
            }
        }
    }

    override fun enqueueUpdateParkingSessionAddressAndPlace(sessionId: String, address: AddressInfo?, placeInfo: PlaceInfo?) {
        scope.launch {
            val userId = authRepository.getCurrentSession()?.userId
            if (userId == null) {
                PaparcarLogger.w(TAG, "enqueueUpdateParkingSessionAddressAndPlace() skipped — no auth session for $sessionId")
                return@launch
            }
            retrying("updateParkingSessionAddressAndPlace:$sessionId") {
                remoteDataSource.updateParkingSessionAddressAndPlace(
                    userId,
                    sessionId,
                    address?.toAddressDto(),
                    placeInfo?.toPlaceInfoDto(),
                )
            }
        }
    }

    private suspend fun retrying(label: String, block: suspend () -> Unit) {
        var attempt = 0
        while (attempt < MAX_RETRIES) {
            val ok = runCatching { block() }.isSuccess
            if (ok) {
                PaparcarLogger.d(TAG, "■ $label success")
                return
            }
            attempt++
            val backoffMs = INITIAL_BACKOFF_MS shl (attempt - 1)
            PaparcarLogger.w(TAG, "⚠ $label attempt $attempt/$MAX_RETRIES — retry in ${backoffMs}ms")
            delay(backoffMs)
        }
        PaparcarLogger.e(TAG, "✗ $label exhausted retries")
    }

    private companion object {
        const val TAG = "PARKDIAG/SyncScheduler"
        const val MAX_RETRIES = 5
        const val INITIAL_BACKOFF_MS = 30_000L
    }
}

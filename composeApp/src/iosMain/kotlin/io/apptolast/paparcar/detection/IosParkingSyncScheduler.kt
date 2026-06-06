@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.detection

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import io.apptolast.paparcar.data.mapper.toAddressDto
import io.apptolast.paparcar.data.mapper.toParkingHistoryDto
import io.apptolast.paparcar.data.mapper.toPlaceInfoDto
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.service.ParkingSyncScheduler
import io.apptolast.paparcar.domain.util.PaparcarLogger
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
                remoteDataSource.saveParkingSession(userId, session.toParkingHistoryDto())
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

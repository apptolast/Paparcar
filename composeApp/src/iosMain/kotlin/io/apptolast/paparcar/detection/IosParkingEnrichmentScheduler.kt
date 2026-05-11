package io.apptolast.paparcar.detection

import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.service.ParkingEnrichmentScheduler
import io.apptolast.paparcar.domain.usecase.location.GetLocationInfoUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * iOS implementation of [ParkingEnrichmentScheduler].
 *
 * Mirrors the work of Android's `EnrichParkingSessionWorker` but executes inline
 * inside a SupervisorJob-backed coroutine scope instead of going through WorkManager.
 *
 * **Limitations vs WorkManager:**
 * - **No cross-process-death persistence.** If the app is killed while a coroutine
 *   is mid-flight, the work is lost. The local [UserParking] row is still saved
 *   (just without geocoded address). A future task can wire up
 *   `BGProcessingTaskRequest` + a small persistent queue to recover from process death.
 * - **No de-duplication.** Two schedules for the same session run twice; the second
 *   call overwrites with fresh data. Cheap enough not to bother gating.
 * - **No system-managed retry-on-network.** We retry up to [MAX_RETRIES] times with
 *   exponential backoff, but the app has to be alive to consume them.
 *
 * The work itself is portable — both [GetLocationInfoUseCase] and
 * [UserParkingRepository] live in commonMain.
 */
class IosParkingEnrichmentScheduler(
    private val getLocationInfo: GetLocationInfoUseCase,
    private val userParkingRepository: UserParkingRepository,
) : ParkingEnrichmentScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun schedule(sessionId: String, lat: Double, lon: Double) {
        scope.launch { runWithRetry(sessionId, lat, lon) }
    }

    private suspend fun runWithRetry(sessionId: String, lat: Double, lon: Double) {
        var attempt = 0
        while (attempt < MAX_RETRIES) {
            if (runEnrichment(sessionId, lat, lon)) return
            attempt++
            val backoffMs = INITIAL_BACKOFF_MS shl (attempt - 1)
            PaparcarLogger.w(TAG, "Enrichment attempt $attempt failed for $sessionId — retry in ${backoffMs}ms")
            delay(backoffMs)
        }
        PaparcarLogger.e(TAG, "Enrichment exhausted retries for $sessionId")
    }

    private suspend fun runEnrichment(sessionId: String, lat: Double, lon: Double): Boolean {
        var addressSaved = false
        getLocationInfo(lat, lon)
            .catch { PaparcarLogger.w(TAG, "Geocoder failure for $sessionId", it) }
            .collect { info ->
                userParkingRepository
                    .updateLocationInfo(sessionId, info.address, info.placeInfo)
                    .onSuccess { addressSaved = true }
            }
        return addressSaved
    }

    private companion object {
        const val TAG = "IosParkingEnrichmentScheduler"
        const val MAX_RETRIES = 3
        const val INITIAL_BACKOFF_MS = 30_000L
    }
}

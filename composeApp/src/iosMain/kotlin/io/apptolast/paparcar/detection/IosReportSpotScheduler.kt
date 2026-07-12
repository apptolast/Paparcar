@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.detection

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotTtlPolicy
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.repository.SpotRepository
import io.apptolast.paparcar.domain.service.ReportSpotScheduler
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * iOS implementation of [ReportSpotScheduler].
 *
 * Mirrors Android's `ReportSpotWorker` but executes inline inside a SupervisorJob
 * coroutine scope. See [IosParkingEnrichmentScheduler] kdoc for the shared
 * limitations vs WorkManager — same trade-offs apply here.
 *
 * Network-required behaviour: where Android sets `NetworkType.CONNECTED` as a
 * WorkManager constraint, on iOS we simply attempt the Firestore write and rely
 * on the exponential backoff to ride out brief outages. Sustained offline is
 * lost — a later task can persist requests into a queue and submit a
 * `BGAppRefreshTaskRequest` for replay when connectivity returns.
 *
 * TTLs match the Android worker: 2 h for auto-detected spots, 15 min for
 * manual reports.
 */
class IosReportSpotScheduler(
    private val spotRepository: SpotRepository,
) : ReportSpotScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun enqueueReportSpot(
        spotId: String,
        lat: Double,
        lon: Double,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
        spotType: SpotType,
        confidence: Float,
        sizeCategory: VehicleSize?,
        carbodyType: CarbodyType?,
        reportedBy: String?,
    ) {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val expiresAt = nowMs + SpotTtlPolicy.ttlMsForType(spotType) // [AUDIT-ARCH-001 M13]
        val spot = Spot(
            id = spotId,
            location = GpsPoint(
                latitude = lat,
                longitude = lon,
                accuracy = 0f,
                timestamp = nowMs,
                speed = 0f,
            ),
            reportedBy = reportedBy ?: "", // [AUDIT-RULES-001 C4] uid identity
            address = address,
            placeInfo = placeInfo,
            type = spotType,
            confidence = confidence,
            sizeCategory = sizeCategory,
            carbodyType = carbodyType,
            expiresAt = expiresAt,
        )

        scope.launch { reportWithRetry(spot) }
    }

    private suspend fun reportWithRetry(spot: Spot) {
        var attempt = 0
        while (attempt < MAX_RETRIES) {
            val outcome = spotRepository.reportSpotReleased(spot)
            if (outcome.isSuccess) return
            attempt++
            val backoffMs = INITIAL_BACKOFF_MS shl (attempt - 1)
            PaparcarLogger.w(TAG, "ReportSpot attempt $attempt failed for ${spot.id} — retry in ${backoffMs}ms")
            delay(backoffMs.milliseconds)
        }
        PaparcarLogger.e(TAG, "ReportSpot exhausted retries for ${spot.id}")
    }

    private companion object {
        const val TAG = "IosReportSpotScheduler"
        const val MAX_RETRIES = 5
        const val INITIAL_BACKOFF_MS = 30_000L
        // [AUDIT-ARCH-001 M13] Spot TTLs now live in the shared domain SpotTtlPolicy.
        // [AUDIT-RULES-001] REPORTED_BY_ANONYMOUS removed — reportedBy is now the uid.
    }
}

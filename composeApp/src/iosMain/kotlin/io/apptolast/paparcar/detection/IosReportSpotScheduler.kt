@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.detection

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.Spot
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
        reporterName: String?,
    ) {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val expiresAt = nowMs + ttlForType(spotType)
        val spot = Spot(
            id = spotId,
            location = GpsPoint(
                latitude = lat,
                longitude = lon,
                accuracy = 0f,
                timestamp = nowMs,
                speed = 0f,
            ),
            reportedBy = reporterName ?: "",
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

    // Mirrors Android's ReportSpotWorker: manual reports get a short TTL; everything
    // else (auto-detected and home-geofence spots) uses the longer auto TTL.
    private fun ttlForType(type: SpotType): Long =
        if (type == SpotType.MANUAL_REPORT) MANUAL_SPOT_TTL_MS else AUTO_SPOT_TTL_MS

    private companion object {
        const val TAG = "IosReportSpotScheduler"
        const val MAX_RETRIES = 5
        const val INITIAL_BACKOFF_MS = 30_000L
        const val REPORTED_BY_ANONYMOUS = "anonymous"

        /** TTL for auto-detected spots: 2 hours. */
        const val AUTO_SPOT_TTL_MS = 2 * 60 * 60 * 1_000L
        /** TTL for manually reported spots: 15 minutes. */
        const val MANUAL_SPOT_TTL_MS = 15 * 60 * 1_000L
    }
}

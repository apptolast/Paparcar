package io.apptolast.paparcar.domain.usecase.spot

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.service.ReportSpotScheduler
import io.apptolast.paparcar.domain.usecase.location.GetLocationInfoUseCase
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Geocodes the given coordinates (best-effort, inline) and schedules a guaranteed
 * Firebase upload via [ReportSpotScheduler].
 *
 * Fire-and-forget: the caller does not need to handle a Result — the WorkManager job
 * persists across process death and retries automatically when the network is available.
 *
 * Geocoding is bounded by [GEOCODE_TIMEOUT_MS] so [ReportSpotScheduler.schedule] is
 * always called regardless of network conditions — even if the geocoder or Overpass API
 * are unreachable.
 */
class ReportSpotReleasedUseCase(
    private val reportSpotScheduler: ReportSpotScheduler,
    private val getLocationInfo: GetLocationInfoUseCase,
) {
    suspend operator fun invoke(
        lat: Double,
        lon: Double,
        spotId: String,
        spotType: SpotType = SpotType.AUTO_DETECTED,
        confidence: Float = 1f,
        sizeCategory: VehicleSize? = null,
    ) {
        var address: AddressInfo? = null
        var placeInfo: PlaceInfo? = null
        withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
            getLocationInfo(lat, lon)
                .catch { /* best-effort: schedule with whatever info we have */ }
                .collect { info ->
                    address = info.address
                    placeInfo = info.placeInfo ?: placeInfo
                }
        }
        reportSpotScheduler.schedule(spotId, lat, lon, address, placeInfo, spotType, confidence, sizeCategory)
    }

    companion object {
        // Overpass API: 6 s connect + 8 s read = 14 s max. Cap at 5 s so the WorkManager
        // job is always enqueued quickly, even on slow or unreachable networks.
        private const val GEOCODE_TIMEOUT_MS = 5_000L
    }
}

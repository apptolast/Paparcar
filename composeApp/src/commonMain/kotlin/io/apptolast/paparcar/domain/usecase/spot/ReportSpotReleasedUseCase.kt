package io.apptolast.paparcar.domain.usecase.spot

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.service.ReportSpotScheduler
import io.apptolast.paparcar.domain.usecase.location.GetLocationInfoUseCase
import kotlinx.coroutines.flow.catch

/**
 * Geocodes the given coordinates (best-effort, inline) and schedules a guaranteed
 * Firebase upload via [ReportSpotScheduler].
 *
 * Fire-and-forget: the caller does not need to handle a Result — the WorkManager job
 * persists across process death and retries automatically when the network is available.
 */
class ReportSpotReleasedUseCase(
    private val reportSpotScheduler: ReportSpotScheduler,
    private val getLocationInfo: GetLocationInfoUseCase,
) {
    suspend operator fun invoke(lat: Double, lon: Double, spotId: String) {
        var address: AddressInfo? = null
        var placeInfo: PlaceInfo? = null
        getLocationInfo(lat, lon)
            .catch { /* best-effort: schedule with whatever info we have */ }
            .collect { info ->
                address = info.address
                placeInfo = info.placeInfo ?: placeInfo
            }
        reportSpotScheduler.schedule(spotId, lat, lon, address, placeInfo)
    }
}

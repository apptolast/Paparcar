package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.geocoder.GeocoderPort
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.LocationInfo
import io.apptolast.paparcar.domain.places.PlacesPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Streams a [LocationInfo] in two steps:
 *
 * 1. **Address** (local geocoder, no network) — emitted immediately.
 * 2. **Place** (network, best-effort) — if found, a second emission updates [LocationInfo.placeInfo].
 *
 * Collectors receive the address quickly and can update the UI/DB right away.
 * The place lookup runs after the first emission without blocking the address result.
 */
class GetLocationInfoUseCase(
    private val geocoder: GeocoderPort,
    private val placesPort: PlacesPort,
) {
    operator fun invoke(lat: Double, lon: Double): Flow<LocationInfo> = flow {
        // Phase 1 — address (local geocoder, instant, no network required).
        val address = geocoder.getAddress(lat, lon)
            .getOrElse { AddressInfo(null, null, null, null) }
        emit(LocationInfo(address = address, placeInfo = null))

        // Phase 2 — POI (network, best-effort). Only emits if a place is found.
        val placeInfo = runCatching { placesPort.getNearbyPlace(lat, lon).getOrNull() }.getOrNull()
        if (placeInfo != null) {
            emit(LocationInfo(address = address, placeInfo = placeInfo))
        }
    }
}
package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.geocoder.GeocoderPort
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.LocationInfo
import io.apptolast.paparcar.domain.places.PlacesPort
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Combines geocoding and POI lookup into a single [LocationInfo].
 *
 * Both calls run in parallel via [coroutineScope] + [async]. If the POI
 * lookup fails or finds nothing, [LocationInfo.placeInfo] will be null.
 * If geocoding fails, an empty [AddressInfo] is returned as fallback.
 */
class GetLocationInfoUseCase(
    private val geocoder: GeocoderPort,
    private val placesPort: PlacesPort,
) {
    suspend operator fun invoke(lat: Double, lon: Double): Result<LocationInfo> =
        coroutineScope {
            val addressDeferred = async { geocoder.getAddress(lat, lon) }
            val placeDeferred = async { placesPort.getNearbyPlace(lat, lon) }

            val address = addressDeferred.await()
                .getOrElse { AddressInfo(null, null, null, null) }
            val place = placeDeferred.await().getOrNull()

            Result.success(LocationInfo(address = address, placeInfo = place))
        }
}

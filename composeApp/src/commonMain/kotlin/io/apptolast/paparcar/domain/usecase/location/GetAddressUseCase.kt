package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.geocoder.GeocoderPort
import io.apptolast.paparcar.domain.model.AddressInfo

class GetAddressUseCase(private val geocoder: GeocoderPort) {
    suspend operator fun invoke(lat: Double, lon: Double): Result<AddressInfo> =
        geocoder.getAddress(lat, lon)
}

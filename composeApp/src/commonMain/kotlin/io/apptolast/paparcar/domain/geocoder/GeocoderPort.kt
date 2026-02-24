package io.apptolast.paparcar.domain.geocoder

import io.apptolast.paparcar.domain.model.AddressInfo

interface GeocoderPort {
    suspend fun getAddress(lat: Double, lon: Double): Result<AddressInfo>
}

package io.apptolast.paparcar.domain.geocoder

import io.apptolast.paparcar.domain.model.AddressInfo

interface GeocoderDataSource {
    suspend fun getAddress(lat: Double, lon: Double): Result<AddressInfo>
}

package io.apptolast.paparcar.domain.geocoder

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.SearchResult

interface GeocoderDataSource {
    suspend fun getAddress(lat: Double, lon: Double): Result<AddressInfo>
    suspend fun searchByName(query: String, maxResults: Int = 5): Result<List<SearchResult>>
}

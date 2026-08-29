package com.rndeveloper.paparcar.domain.geocoder

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.SearchResult

interface GeocoderDataSource {
    suspend fun getAddress(lat: Double, lon: Double): Result<AddressInfo>
    suspend fun searchByName(query: String, maxResults: Int = 5): Result<List<SearchResult>>
}

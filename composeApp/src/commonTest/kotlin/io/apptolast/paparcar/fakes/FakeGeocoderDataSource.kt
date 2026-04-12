package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.geocoder.GeocoderDataSource
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.SearchResult

class FakeGeocoderDataSource : GeocoderDataSource {

    var addressResult: Result<AddressInfo> = Result.success(AddressInfo(null, null, null, null))
    var searchResults: Result<List<SearchResult>> = Result.success(emptyList())
    var lastSearchQuery: String? = null

    override suspend fun getAddress(lat: Double, lon: Double): Result<AddressInfo> = addressResult

    override suspend fun searchByName(query: String, maxResults: Int): Result<List<SearchResult>> {
        lastSearchQuery = query
        return searchResults
    }
}

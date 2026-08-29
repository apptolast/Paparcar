package com.rndeveloper.paparcar.domain.usecase.location

import com.rndeveloper.paparcar.domain.geocoder.GeocoderDataSource
import com.rndeveloper.paparcar.domain.model.SearchResult

class SearchAddressUseCase(private val geocoder: GeocoderDataSource) {
    suspend operator fun invoke(query: String): Result<List<SearchResult>> =
        geocoder.searchByName(query.trim())
}

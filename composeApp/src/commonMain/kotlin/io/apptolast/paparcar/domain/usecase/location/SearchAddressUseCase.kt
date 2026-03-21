package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.geocoder.GeocoderDataSource
import io.apptolast.paparcar.domain.model.SearchResult

class SearchAddressUseCase(private val geocoder: GeocoderDataSource) {
    suspend operator fun invoke(query: String): Result<List<SearchResult>> =
        geocoder.searchByName(query.trim())
}

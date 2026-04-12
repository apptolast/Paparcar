package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.model.SearchResult
import io.apptolast.paparcar.fakes.FakeGeocoderDataSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchAddressUseCaseTest {

    private val geocoder = FakeGeocoderDataSource()
    private val useCase = SearchAddressUseCase(geocoder)

    @Test
    fun `should_returnSearchResults_from_geocoder`() = runTest {
        val results = listOf(SearchResult("Madrid, Spain", 40.416775, -3.703790))
        geocoder.searchResults = Result.success(results)

        val outcome = useCase("Madrid")

        assertEquals(results, outcome.getOrNull())
    }

    @Test
    fun `should_trimQuery_before_searching`() = runTest {
        useCase("  Madrid  ")

        assertEquals("Madrid", geocoder.lastSearchQuery)
    }

    @Test
    fun `should_returnEmptyList_when_geocoderReturnsNone`() = runTest {
        geocoder.searchResults = Result.success(emptyList())

        val outcome = useCase("XYZ")

        assertEquals(emptyList(), outcome.getOrNull())
    }

    @Test
    fun `should_returnFailure_when_geocoderFails`() = runTest {
        geocoder.searchResults = Result.failure(RuntimeException("Network error"))

        val outcome = useCase("Madrid")

        assertTrue(outcome.isFailure)
    }
}

@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.model.SearchResult
import io.apptolast.paparcar.domain.usecase.location.SearchAddressUseCase
import io.apptolast.paparcar.fakes.FakeGeocoderDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [HomeSearchController] — the debounced address-search pipeline. A single
 * [StandardTestDispatcher] is shared between the controller's scope and [runTest] so the 300 ms
 * debounce can be driven deterministically with `advanceTimeBy`/`advanceUntilIdle`. The blank-filter
 * and debounce were previously untested (only the immediate per-keystroke state writes, which live in
 * the VM, had coverage).
 */
class HomeSearchControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var scope: CoroutineScope

    private val geocoder = FakeGeocoderDataSource()
    private val searchAddress = SearchAddressUseCase(geocoder)

    private var searchingCallCount = 0
    private var lastResults: List<SearchResult>? = null
    private var emptyOrErrorCallCount = 0

    @BeforeTest
    fun setUp() {
        scope = CoroutineScope(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun buildController() = HomeSearchController(
        scope = scope,
        searchAddress = searchAddress,
        onSearching = { searchingCallCount++ },
        onResults = { results -> lastResults = results },
        onEmptyOrError = { emptyOrErrorCallCount++ },
    ).also { it.start() }

    @Test
    fun `should_emit_results_after_the_debounce_for_a_valid_query`() = runTest(testDispatcher) {
        val expected = listOf(SearchResult("Madrid", 40.0, -3.0))
        geocoder.searchResults = Result.success(expected)
        val controller = buildController()

        controller.onQueryChanged("Madrid")
        advanceUntilIdle()

        assertEquals(1, searchingCallCount)
        assertEquals(expected, lastResults)
        assertEquals("Madrid", geocoder.lastSearchQuery)
    }

    @Test
    fun `should_ignore_blank_queries`() = runTest(testDispatcher) {
        val controller = buildController()

        controller.onQueryChanged("   ")
        advanceUntilIdle()

        assertEquals(0, searchingCallCount)
        assertNull(lastResults)
        assertNull(geocoder.lastSearchQuery)
    }

    @Test
    fun `should_debounce_rapid_typing_and_only_search_the_final_query`() = runTest(testDispatcher) {
        geocoder.searchResults = Result.success(emptyList())
        val controller = buildController()

        controller.onQueryChanged("M")
        advanceTimeBy(100)
        controller.onQueryChanged("Ma")
        advanceTimeBy(100)
        controller.onQueryChanged("Mad")
        advanceUntilIdle()

        // Only the last query survives the debounce → a single search.
        assertEquals(1, searchingCallCount)
        assertEquals("Mad", geocoder.lastSearchQuery)
    }

    @Test
    fun `should_report_empty_on_a_search_failure`() = runTest(testDispatcher) {
        geocoder.searchResults = Result.failure(RuntimeException("network down"))
        val controller = buildController()

        controller.onQueryChanged("Madrid")
        advanceUntilIdle()

        assertEquals(1, searchingCallCount)
        assertEquals(1, emptyOrErrorCallCount)
        assertNull(lastResults)
    }
}

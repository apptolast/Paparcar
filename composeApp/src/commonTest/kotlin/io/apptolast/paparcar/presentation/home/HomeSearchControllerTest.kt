@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.model.SearchResult
import io.apptolast.paparcar.domain.usecase.location.SearchAddressUseCase
import io.apptolast.paparcar.fakes.FakeGeocoderDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [HomeSearchController] — the debounced address-search pipeline, exposed as the cold
 * [HomeSearchController.updates] flow of [SearchUpdate] lifecycle events. A [StandardTestDispatcher]
 * shared between the collector and [runTest] drives the 300 ms debounce deterministically with
 * `advanceTimeBy`/`advanceUntilIdle`.
 */
class HomeSearchControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var scope: CoroutineScope

    private val geocoder = FakeGeocoderDataSource()
    private val searchAddress = SearchAddressUseCase(geocoder)

    /** Every emission of the collected updates flow, in order. */
    private val received = mutableListOf<SearchUpdate>()

    @BeforeTest
    fun setUp() {
        scope = CoroutineScope(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun startController(): HomeSearchController {
        val controller = HomeSearchController(searchAddress)
        scope.launch { controller.updates.collect { received.add(it) } }
        return controller
    }

    @Test
    fun `should_emit_searching_then_results_after_the_debounce_for_a_valid_query`() = runTest(testDispatcher) {
        val expected = listOf(SearchResult("Madrid", 40.0, -3.0))
        geocoder.searchResults = Result.success(expected)
        val controller = startController()

        controller.onQueryChanged("Madrid")
        advanceUntilIdle()

        assertEquals(listOf(SearchUpdate.Searching, SearchUpdate.Success(expected)), received)
        assertEquals("Madrid", geocoder.lastSearchQuery)
    }

    @Test
    fun `should_ignore_blank_queries`() = runTest(testDispatcher) {
        val controller = startController()

        controller.onQueryChanged("   ")
        advanceUntilIdle()

        assertTrue(received.isEmpty())
        assertNull(geocoder.lastSearchQuery)
    }

    @Test
    fun `should_debounce_rapid_typing_and_only_search_the_final_query`() = runTest(testDispatcher) {
        geocoder.searchResults = Result.success(emptyList())
        val controller = startController()

        controller.onQueryChanged("M")
        advanceTimeBy(100)
        controller.onQueryChanged("Ma")
        advanceTimeBy(100)
        controller.onQueryChanged("Mad")
        advanceUntilIdle()

        // Only the last query survives the debounce → a single search lifecycle.
        assertEquals(1, received.count { it is SearchUpdate.Searching })
        assertEquals("Mad", geocoder.lastSearchQuery)
    }

    @Test
    fun `should_emit_failure_on_a_search_error`() = runTest(testDispatcher) {
        geocoder.searchResults = Result.failure(RuntimeException("network down"))
        val controller = startController()

        controller.onQueryChanged("Madrid")
        advanceUntilIdle()

        assertEquals(listOf(SearchUpdate.Searching, SearchUpdate.Failure), received)
    }
}

package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.model.SearchResult
import io.apptolast.paparcar.domain.usecase.location.SearchAddressUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.milliseconds

/**
 * Owns the debounced address-search pipeline behind the home search bar: a query stream that fires the
 * [SearchAddressUseCase] once the user stops typing, and funnels the loading flag + results back to the
 * VM through callbacks.
 *
 * The immediate state writes on each keystroke ([HomeState.searchQuery], [HomeState.isSearchActive],
 * the blank-query reset) intentionally stay in the VM's `onSearchQueryChanged` — this controller owns
 * only the async debounced tail. Like [HomeGeocodingController] it is presentation-layer-agnostic (no
 * [HomeState], no [HomeViewModel]) so the VM stays the single writer of state.
 */
@OptIn(FlowPreview::class)
class HomeSearchController(
    private val scope: CoroutineScope,
    private val searchAddress: SearchAddressUseCase,
    private val onSearching: () -> Unit,
    private val onResults: (List<SearchResult>) -> Unit,
    private val onEmptyOrError: () -> Unit,
    private val tag: String = TAG,
) {

    private val queryFlow = MutableStateFlow("")

    /** Launches the debounced search pipeline on [scope]. Call once from the VM's init. */
    fun start() {
        queryFlow
            .debounce(SEARCH_DEBOUNCE_MS.milliseconds)
            .filter { it.isNotBlank() }
            .onEach { query ->
                onSearching()
                searchAddress(query)
                    .onSuccess { results -> onResults(results) }
                    .onFailure { onEmptyOrError() }
            }
            .catch { e -> PaparcarLogger.w(tag, "Search query flow error", e) }
            .launchIn(scope)
    }

    /** Feeds a new query into the debounced pipeline. */
    fun onQueryChanged(query: String) {
        queryFlow.value = query
    }

    private companion object {
        const val TAG = "HomeSearchController"
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

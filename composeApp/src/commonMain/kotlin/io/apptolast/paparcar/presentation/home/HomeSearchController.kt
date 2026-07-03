package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.model.SearchResult
import io.apptolast.paparcar.domain.usecase.location.SearchAddressUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.transform
import kotlin.time.Duration.Companion.milliseconds

/** Lifecycle of one debounced address search, in emission order: [Searching] → [Success] | [Failure]. */
sealed interface SearchUpdate {
    /** The debounce settled and the geocoder request is in flight — drives the loading spinner. */
    data object Searching : SearchUpdate
    data class Success(val results: List<SearchResult>) : SearchUpdate
    /** The lookup failed — the UI clears the results and stops the spinner. */
    data object Failure : SearchUpdate
}

/**
 * Self-contained owner of the debounced address-search pipeline behind the home search bar: a query
 * stream that fires the [SearchAddressUseCase] once the user stops typing, exposed as a **cold**
 * [updates] flow of [SearchUpdate] lifecycle events the ViewModel collects into state.
 *
 * The immediate state writes on each keystroke ([HomeState.searchQuery], [HomeState.isSearchActive],
 * the blank-query reset) intentionally stay in the VM's `onSearchQueryChanged` — this controller owns
 * only the async debounced tail. Built by Koin with its own use case; no scope, no callbacks.
 */
@OptIn(FlowPreview::class)
class HomeSearchController(
    private val searchAddress: SearchAddressUseCase,
    private val tag: String = TAG,
) {

    private val queryFlow = MutableStateFlow("")

    /** Cold flow of search lifecycle events for the current (debounced, non-blank) query. */
    val updates: Flow<SearchUpdate> = queryFlow
        .debounce(SEARCH_DEBOUNCE_MS.milliseconds)
        .filter { it.isNotBlank() }
        .transform { query ->
            emit(SearchUpdate.Searching)
            emit(
                searchAddress(query).fold(
                    onSuccess = { results -> SearchUpdate.Success(results) },
                    onFailure = { SearchUpdate.Failure },
                ),
            )
        }
        .catch { e -> PaparcarLogger.w(tag, "Search query flow error", e) }

    /** Feeds a new query into the debounced pipeline. */
    fun onQueryChanged(query: String) {
        queryFlow.value = query
    }

    private companion object {
        const val TAG = "HomeSearchController"
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

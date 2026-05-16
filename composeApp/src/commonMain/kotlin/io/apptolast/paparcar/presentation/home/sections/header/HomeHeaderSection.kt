package io.apptolast.paparcar.presentation.home.sections.header

import io.apptolast.paparcar.presentation.home.sections.header.components.HomeGpsAccuracyBanner
import io.apptolast.paparcar.presentation.home.sections.header.components.HomeSearchBar
import io.apptolast.paparcar.presentation.home.sections.header.components.MapTypePicker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.domain.model.SearchResult
import io.apptolast.paparcar.presentation.home.HomeState

/**
 * Top floating layer of HomeScreen: search bar + map-type picker + GPS
 * accuracy banner stacked above the map. Sits over the map tiles, not
 * inside the bottom sheet — pure presentation, no drag/state coordination.
 */
@Composable
internal fun HomeHeaderSection(
    state: HomeState,
    onSearchQueryChanged: (String) -> Unit,
    onSearchResultClick: (SearchResult) -> Unit,
    onSearchClear: () -> Unit,
    onMapTypeSelected: (MapType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            HomeSearchBar(
                query = state.searchQuery,
                results = state.searchResults,
                isActive = state.isSearchActive,
                isSearching = state.isSearching,
                onQueryChange = onSearchQueryChanged,
                onResultClick = onSearchResultClick,
                onClear = onSearchClear,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            MapTypePicker(
                currentType = state.mapType,
                onTypeSelected = onMapTypeSelected,
            )
        }
        HomeGpsAccuracyBanner(
            accuracy = state.userGpsPoint?.accuracy,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

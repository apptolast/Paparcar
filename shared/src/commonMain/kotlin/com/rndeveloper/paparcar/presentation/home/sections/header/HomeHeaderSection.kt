package com.rndeveloper.paparcar.presentation.home.sections.header

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swmansion.kmpmaps.core.MapType
import com.rndeveloper.paparcar.domain.model.SearchResult
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.presentation.home.HomeHeaderSlice
import com.rndeveloper.paparcar.presentation.home.sections.header.components.HomeSearchBar
import com.rndeveloper.paparcar.presentation.util.MAP_FLOATING_SIDE_INSET_DP
import com.rndeveloper.paparcar.presentation.home.sections.header.components.MapTypeToggle
import com.rndeveloper.paparcar.ui.theme.PapMotion
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.AddZoneChip
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.ZoneChip

@Composable
internal fun HomeHeaderSection(
    slice: HomeHeaderSlice,
    onSearchQueryChanged: (String) -> Unit,
    onSearchResultClick: (SearchResult) -> Unit,
    onSearchClear: () -> Unit,
    onMapTypeSelected: (MapType) -> Unit,
    onSelectZone: (String) -> Unit,
    onAddZone: () -> Unit,
    onEditZone: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Status-bar inset is applied by the parent column in HomeScreen (which also hosts the
    // persistent detection banner above this header). [DET-READY-001g]
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MAP_FLOATING_SIDE_INSET_DP.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Two controls, and only two: the search bar and the map-type toggle. A third 56dp
            // circle here squeezed the search bar below what its placeholder needs and wrapped it
            // to two lines — measured, and the reason nothing else is allowed into this row.
            // [UI-HOME-SEARCH-BAR-MUST-STAY-ONE-LINE-001]
            HomeSearchBar(
                query = slice.searchQuery,
                results = slice.searchResults,
                isActive = slice.isSearchActive,
                isSearching = slice.isSearching,
                showNoResults = slice.searchNoResults,
                onQueryChange = onSearchQueryChanged,
                onResultClick = onSearchResultClick,
                onClear = onSearchClear,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            MapTypeToggle(
                currentType = slice.mapType,
                onTypeSelected = onMapTypeSelected,
            )
        }
        AnimatedVisibility(
            visible = slice.hasCorePermissions,
            // Chips hang under the search bar → grow/collapse from the top edge.
            enter = fadeIn(PapMotion.medium()) + expandVertically(PapMotion.medium(), expandFrom = Alignment.Top),
            exit = fadeOut(PapMotion.medium()) + shrinkVertically(PapMotion.medium(), shrinkTowards = Alignment.Top),
        ) {
            // ONE row in every state. The empty state used to be a separate composable with its
            // own padding, so the add control sat at a different y depending on whether you had
            // zones — and jumped the moment you saved your first one. The invitation is now just
            // the same trailing chip, expanded. [UI-THE-ADD-ZONE-CHIP-IS-ONE-CHIP-IN-EVERY-STATE-001]
            //
            // Session-scoped dismissal (no preference plumbing): an invitation may come back on a
            // fresh launch. [UI-SHEET-003]
            var invitationDismissed by rememberSaveable { mutableStateOf(false) }
            HeaderZoneChips(
                zones = slice.zones,
                inviteToAddZone = slice.zones.isEmpty() && !invitationDismissed,
                onSelectZone = onSelectZone,
                onAddZone = onAddZone,
                onEditZone = onEditZone,
                onDismissInvitation = { invitationDismissed = true },
            )
        }
        // Two lanes, and only two. The GPS pill used to hold a third one — 34dp reserved full time
        // so that a degraded fix arriving could not push the header down. Reserving was the right
        // fix for a lane INSIDE this measured column; the mistake was the column. The pill now
        // floats in the map's FAB band, where appearing pushes nobody, there is nothing to reserve,
        // and it survives the pin modes this header hides itself for.
        // [UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001] [UI-HEADER-PILLS-SHARE-ONE-GEOMETRY-001]
    }
}

@Composable
private fun HeaderZoneChips(
    zones: List<Zone>,
    /** Show the add chip expanded into its invitation — only while there is no zone to point at. */
    inviteToAddZone: Boolean,
    onSelectZone: (String) -> Unit,
    onAddZone: () -> Unit,
    onEditZone: (String) -> Unit,
    onDismissInvitation: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Inset lives in contentPadding (not an outer padding) so the FAB shadow of
        // the first/last chip has room and isn't clipped at the row edge; chips also
        // scroll edge-to-edge. [MAP-GLASS-001]
        contentPadding = PaddingValues(
            horizontal = MAP_FLOATING_SIDE_INSET_DP.dp,
            vertical = CHIP_SHADOW_ROOM_DP.dp,
        ),
    ) {
        items(zones, key = { it.id }) { zone ->
            ZoneChip(
                label = zone.name,
                iconKey = zone.iconKey,
                isPrivate = zone.isPrivate,
                onClick = remember(zone.id, onSelectZone) { { onSelectZone(zone.id) } },
                onEdit = remember(zone.id, onEditZone) { { onEditZone(zone.id) } },
            )
        }
        item("add_zone") {
            AddZoneChip(
                expanded = inviteToAddZone,
                onAddZone = onAddZone,
                onDismiss = onDismissInvitation,
            )
        }
    }
}

private const val CHIP_SHADOW_ROOM_DP = 8


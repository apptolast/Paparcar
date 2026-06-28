package io.apptolast.paparcar.presentation.home.sections.header

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.domain.model.SearchResult
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.home.sections.header.components.HomeGpsAccuracyBanner
import io.apptolast.paparcar.presentation.home.sections.header.components.HomeSearchBar
import io.apptolast.paparcar.presentation.home.sections.header.components.MapTypePicker
import io.apptolast.paparcar.presentation.home.sections.sheet.components.ZoneChip
import io.apptolast.paparcar.ui.components.GlassSurface
import io.apptolast.paparcar.ui.components.chips.PaparcarAddChip
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_header_add_zone
import paparcar.composeapp.generated.resources.home_header_add_zone_hint

@Composable
internal fun HomeHeaderSection(
    state: HomeState,
    onSearchQueryChanged: (String) -> Unit,
    onSearchResultClick: (SearchResult) -> Unit,
    onSearchClear: () -> Unit,
    onMapTypeSelected: (MapType) -> Unit,
    onSelectZone: (String) -> Unit,
    onAddZone: () -> Unit,
    onDeleteZone: (String) -> Unit,
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
                .padding(horizontal = 14.dp),
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
        AnimatedVisibility(
            visible = state.hasCorePermissions,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            if (state.zones.isNotEmpty()) {
                HeaderZoneChips(
                    zones = state.zones,
                    onSelectZone = onSelectZone,
                    onAddZone = onAddZone,
                    onDeleteZone = onDeleteZone,
                    onEditZone = onEditZone,
                )
            } else {
                HeaderAddZoneChip(onAddZone = onAddZone)
            }
        }
        HomeGpsAccuracyBanner(
            accuracy = state.userGpsPoint?.accuracy,
            modifier = Modifier.padding(start = 14.dp, top = 6.dp),
        )
    }
}

@Composable
private fun HeaderZoneChips(
    zones: List<Zone>,
    onSelectZone: (String) -> Unit,
    onAddZone: () -> Unit,
    onDeleteZone: (String) -> Unit,
    onEditZone: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
    ) {
        items(zones, key = { it.id }) { zone ->
            ZoneChip(
                label = zone.name,
                iconKey = zone.iconKey,
                onClick = remember(zone.id, onSelectZone) { { onSelectZone(zone.id) } },
                onDelete = remember(zone.id, onDeleteZone) { { onDeleteZone(zone.id) } },
                onLongPress = remember(zone.id, onEditZone) { { onEditZone(zone.id) } },
            )
        }
        item("add_zone") {
            PaparcarAddChip(
                onClick = onAddZone,
                iconSize = CHIP_ICON_DP.dp,
                horizontalPad = 8.dp,
                verticalPad = 8.dp,
            )
        }
    }
}

@Composable
private fun HeaderAddZoneChip(onAddZone: () -> Unit) {
    GlassSurface(
        shape = RoundedCornerShape(28.dp),
        shadowElevation = FLOATING_SHADOW_ELEVATION,
        onClick = onAddZone,
        modifier = Modifier.padding(start = 14.dp, top = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(CHIP_ICON_BOX_DP.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(CHIP_ICON_DP.dp),
                    )
                }
            }
            Column {
                Text(
                    text = stringResource(Res.string.home_header_add_zone),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.home_header_add_zone_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = HINT_ALPHA),
                )
            }
        }
    }
}

private val FLOATING_SHADOW_ELEVATION = 6.dp
private const val CHIP_ICON_BOX_DP = 28
private const val CHIP_ICON_DP = 16
private const val HINT_ALPHA = 0.5f

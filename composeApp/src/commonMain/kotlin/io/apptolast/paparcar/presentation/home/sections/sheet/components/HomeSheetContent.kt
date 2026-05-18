
package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.presentation.home.HomeIntent
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.locationDisplayText
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_feed_nearby
import paparcar.composeapp.generated.resources.home_feed_nearby_with_count
import paparcar.composeapp.generated.resources.home_my_car_section_header
import paparcar.composeapp.generated.resources.home_size_filter_all
import paparcar.composeapp.generated.resources.vehicle_size_large
import paparcar.composeapp.generated.resources.vehicle_size_medium
import paparcar.composeapp.generated.resources.vehicle_size_moto
import paparcar.composeapp.generated.resources.vehicle_size_small
import paparcar.composeapp.generated.resources.vehicle_size_van

/**
 * Emits the sheet content items into a [LazyListScope].
 *
 * Section order (Home v1 design):
 *  1. **Zone chips** — habitual-place shortcuts at the top of the sheet so the
 *     discovery flow (zone → spots) reads top-down. Falls through to a
 *     zones empty CTA card when none saved.
 *  2. **"TU COCHE" header + parking row** — personal block: header always
 *     visible, then either the populated parking row or the manual-park
 *     empty card.
 *  3. **"PLAZAS LIBRES CERCA · N" header + filter bar + spots list** — the
 *     community discovery feed, capped by a "Report a free spot" CTA after
 *     the list.
 */
internal fun LazyListScope.homeSheetItems(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onCameraMove: (Double, Double) -> Unit,
    onParkingClick: () -> Unit,
    onManualPark: () -> Unit,
    onSpotSelect: (lat: Double, lon: Double, spotId: String) -> Unit,
) {
    val selectedSpotId = state.selectedItemId?.takeIf { it != HomeState.PARKING_ITEM_ID }
    val filteredSpots = state.filteredNearbySpots
    val showPersonalBlocks = state.allPermissionsGranted
    val showZoneChips = state.allPermissionsGranted && state.zones.isNotEmpty()
    val showZonesEmpty = state.allPermissionsGranted && state.zones.isEmpty()
    val showFilterBar = state.allPermissionsGranted && state.nearbySpots.isNotEmpty()

    // ── 1. Zone chips (discovery navigation, top of sheet) ─────────────────
    if (showZoneChips) {
        item("zones_chips") {
            HomeZoneChips(
                zones = state.zones,
                onSelectZone = { id -> onIntent(HomeIntent.SelectZone(id)) },
                onAddZone = { onIntent(HomeIntent.EnterAddZoneMode) },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    } else if (showZonesEmpty) {
        item("zones_empty_card") {
            HomeZonesEmptyCard(
                onAddZone = { onIntent(HomeIntent.EnterAddZoneMode) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
        }
    }

    // ── 2. "TU COCHE" header + parking row (personal block) ────────────────
    if (showPersonalBlocks) {
        item("my_car_header") {
            HomeSectionHeader(
                title = stringResource(Res.string.home_my_car_section_header).uppercase(),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        parkingSection(state, onParkingClick, onManualPark)
    }

    // ── 3. Spots (header + filter bar + list + report CTA) ─────────────────
    spotsSection(
        state = state,
        onIntent = onIntent,
        onCameraMove = onCameraMove,
        onSpotSelect = onSpotSelect,
        selectedSpotId = selectedSpotId,
        filteredSpots = filteredSpots,
        showFilterBar = showFilterBar,
    )
}

/**
 * Returns the absolute item index of a spot card in the LazyColumn emitted
 * by [homeSheetItems], or -1 if the spot is not part of the current list.
 */
internal fun homeSheetSpotItemIndex(state: HomeState, spotId: String): Int {
    val filteredSpots = state.filteredNearbySpots
    val spotIdx = filteredSpots.indexOfFirst { it.id == spotId }
    if (spotIdx < 0) return -1

    val showPersonalBlocks = state.allPermissionsGranted
    val showZoneChips = state.allPermissionsGranted && state.zones.isNotEmpty()
    val showZonesEmpty = state.allPermissionsGranted && state.zones.isEmpty()
    val showFilterBar = state.allPermissionsGranted && state.nearbySpots.isNotEmpty()

    var base = 1 // handle / top item in LazyColumn
    if (showZoneChips) base += 1        // zones_chips
    else if (showZonesEmpty) base += 1  // zones_empty_card
    if (showPersonalBlocks) {
        base += 1                       // my_car_header
        base += if (state.userParking != null) 1 else 1 // parking_banner OR parking_empty (no separate header now)
    }
    base += 1                           // spots_header
    if (showFilterBar) base += 1        // filter_bar
    // report_spot_cta sits AFTER the spot list, so it does not shift indices.
    return base + spotIdx
}

// ─────────────────────────────────────────────────────────────────────────────
// Sections
// ─────────────────────────────────────────────────────────────────────────────

private fun LazyListScope.parkingSection(
    state: HomeState,
    onParkingClick: () -> Unit,
    onManualPark: () -> Unit,
) {
    if (state.userParking != null) {
        item("parking_banner") {
            HomeParkingRow(
                parking = state.userParking,
                userLocation = state.userGpsPoint?.let { Pair(it.latitude, it.longitude) },
                isSelected = state.selectedItemId == HomeState.PARKING_ITEM_ID,
                onSelect = onParkingClick,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    } else {
        item("parking_empty") {
            HomeParkingEmptyCard(
                onManualPark = onManualPark,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

private fun LazyListScope.spotsSection(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onCameraMove: (Double, Double) -> Unit,
    onSpotSelect: (lat: Double, lon: Double, spotId: String) -> Unit,
    selectedSpotId: String?,
    filteredSpots: List<Spot>,
    showFilterBar: Boolean,
) {
    item("spots_header") {
        HomeSectionHeader(
            title = if (filteredSpots.isNotEmpty())
                stringResource(Res.string.home_feed_nearby_with_count, filteredSpots.size)
            else
                stringResource(Res.string.home_feed_nearby),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
    }

    if (showFilterBar) {
        item("filter_bar") {
            HomeSizeFilterBar(
                selectedSize = state.sizeFilter,
                onFilterSelect = { size -> onIntent(HomeIntent.SetSizeFilter(size)) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }

    when {
        !state.allPermissionsGranted -> item("permissions") {
            HomePermissionsCard(
                onRequestPermissions = { onIntent(HomeIntent.LoadNearbySpots) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        state.isLoading -> item("skeleton") { SpotsSkeletonList() }
        filteredSpots.isEmpty() && state.sizeFilter != null && state.nearbySpots.isNotEmpty() ->
            item("empty_filtered") {
                HomeEmptyFilteredSpots(
                    onClearFilter = { onIntent(HomeIntent.SetSizeFilter(null)) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        filteredSpots.isEmpty() -> item("empty") {
            HomeEmptySpots(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        else -> itemsIndexed(filteredSpots, key = { _, spot -> spot.id }) { index, spot ->
            Column {
                HomeSpotRow(
                    spot = spot,
                    userLocation = state.userGpsPoint?.let { Pair(it.latitude, it.longitude) },
                    isSelected = spot.id == selectedSpotId,
                    onSelect = {
                        onCameraMove(spot.location.latitude, spot.location.longitude)
                        onSpotSelect(spot.location.latitude, spot.location.longitude, spot.id)
                    },
                )
                if (index < filteredSpots.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 70.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                    )
                }
            }
        }
    }

    if (state.allPermissionsGranted) {
        item("report_spot_cta") {
            HomeReportSpotCard(
                onReport = { onIntent(HomeIntent.EnterReportMode) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading skeleton
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpotsSkeletonList(
    itemCount: Int = SKELETON_ITEM_COUNT,
) {
    val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val alpha by transition.animateFloat(
        initialValue = SKELETON_ALPHA_MIN,
        targetValue = SKELETON_ALPHA_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SKELETON_ANIM_MS),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton_alpha",
    )
    val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)

    Column {
        repeat(itemCount) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(shimmerColor),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .width(SKELETON_TITLE_WIDTH.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerColor),
                    )
                    Box(
                        modifier = Modifier
                            .width(SKELETON_SUBTITLE_WIDTH.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerColor.copy(alpha = shimmerColor.alpha * SKELETON_SUBTITLE_ALPHA_FACTOR)),
                    )
                }
            }
            if (index < itemCount - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 70.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                )
            }
        }
    }
}

private const val SKELETON_ITEM_COUNT = 4
private const val SKELETON_ALPHA_MIN = 0.06f
private const val SKELETON_ALPHA_MAX = 0.16f
private const val SKELETON_ANIM_MS = 900
private const val SKELETON_TITLE_WIDTH = 140
private const val SKELETON_SUBTITLE_WIDTH = 100
private const val SKELETON_SUBTITLE_ALPHA_FACTOR = 0.7f

// ─────────────────────────────────────────────────────────────────────────────
// Size filter bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeSizeFilterBar(
    selectedSize: VehicleSize?,
    onFilterSelect: (VehicleSize?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allLabel    = stringResource(Res.string.home_size_filter_all)
    val motoLabel   = stringResource(Res.string.vehicle_size_moto)
    val smallLabel  = stringResource(Res.string.vehicle_size_small)
    val mediumLabel = stringResource(Res.string.vehicle_size_medium)
    val largeLabel  = stringResource(Res.string.vehicle_size_large)
    val vanLabel    = stringResource(Res.string.vehicle_size_van)

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = selectedSize == null,
            onClick = { onFilterSelect(null) },
            label = { Text(allLabel, style = MaterialTheme.typography.labelSmall) },
        )
        listOf(
            VehicleSize.MOTO   to motoLabel,
            VehicleSize.SMALL  to smallLabel,
            VehicleSize.MEDIUM to mediumLabel,
            VehicleSize.LARGE  to largeLabel,
            VehicleSize.VAN    to vanLabel,
        ).forEach { (size, label) ->
            FilterChip(
                selected = selectedSize == size,
                onClick = { onFilterSelect(if (selectedSize == size) null else size) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        title,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        letterSpacing = 0.8.sp,
    )
}

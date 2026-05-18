
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
import io.apptolast.paparcar.presentation.util.toReliabilityUiState
import io.apptolast.paparcar.ui.components.ParkingSpotItem
import io.apptolast.paparcar.ui.components.SpotUiState
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_feed_nearby
import paparcar.composeapp.generated.resources.home_feed_nearby_with_count
import paparcar.composeapp.generated.resources.home_size_filter_all
import paparcar.composeapp.generated.resources.home_where_to_park_title
import paparcar.composeapp.generated.resources.vehicle_size_large
import paparcar.composeapp.generated.resources.vehicle_size_medium
import paparcar.composeapp.generated.resources.vehicle_size_moto
import paparcar.composeapp.generated.resources.vehicle_size_small
import paparcar.composeapp.generated.resources.vehicle_size_van

/**
 * Emits the sheet content items into a [LazyListScope].
 *
 * ## Section order
 *
 *  1. **Parking** (personal block) — my car status. Banner when parked;
 *     header + empty CTA when not parked. Visible once permissions are granted.
 *  2. **Thin divider** — separates personal data from community discovery.
 *  3. **Zone chips** (discovery block, when zones exist) — horizontal row that
 *     moves the camera to a saved habitual area. Sits immediately above the
 *     spots list so the zona → búsqueda de spots flow is visually obvious.
 *  4. **Spots** — zone empty CTA (when no zones) followed by the spots header,
 *     size filter, report CTA, and the spot list. The zone empty card lives
 *     inside the discovery block so both states (chips / empty) occupy the
 *     same visual position relative to spots.
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
    val filteredSpots = if (state.sizeFilter == null) {
        state.nearbySpots
    } else {
        state.nearbySpots.filter { it.sizeCategory == null || it.sizeCategory == state.sizeFilter }
    }
    val showParkingFirst = state.allPermissionsGranted
    val showZoneChips = state.allPermissionsGranted && state.zones.isNotEmpty()
    val showZonesEmpty = state.allPermissionsGranted && state.zones.isEmpty()
    val showFilterBar = state.allPermissionsGranted && state.nearbySpots.isNotEmpty()

    // ── 1. Personal block: parking ──────────────────────────────────────────
    if (showParkingFirst) {
        parkingSection(state, onParkingClick, onManualPark)
    }

    // ── 2. Divider: personal → discovery ───────────────────────────────────
    if (showParkingFirst) {
        item("mid_divider") {
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.07f),
            )
        }
    }

    // ── 3. Zone chips (discovery navigation, above spots) ──────────────────
    if (showZoneChips) {
        item("zones_chips") {
            HomeZoneChips(
                zones = state.zones,
                onSelectZone = { id -> onIntent(HomeIntent.SelectZone(id)) },
                onAddZone = { onIntent(HomeIntent.EnterAddZoneMode) },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    // ── 4. Spots (with zone empty CTA at top when no zones) ────────────────
    spotsSection(
        state = state,
        onIntent = onIntent,
        onCameraMove = onCameraMove,
        onSpotSelect = onSpotSelect,
        selectedSpotId = selectedSpotId,
        filteredSpots = filteredSpots,
        showFilterBar = showFilterBar,
        showZonesEmpty = showZonesEmpty,
        onAddZone = { onIntent(HomeIntent.EnterAddZoneMode) },
    )
}

/**
 * Returns the absolute item index of a spot card in the LazyColumn emitted
 * by [homeSheetItems], or -1 if the spot is not part of the current list.
 */
internal fun homeSheetSpotItemIndex(state: HomeState, spotId: String): Int {
    val filteredSpots = if (state.sizeFilter == null) {
        state.nearbySpots
    } else {
        state.nearbySpots.filter { it.sizeCategory == null || it.sizeCategory == state.sizeFilter }
    }
    val spotIdx = filteredSpots.indexOfFirst { it.id == spotId }
    if (spotIdx < 0) return -1

    val showParkingFirst = state.allPermissionsGranted
    val showZoneChips = state.allPermissionsGranted && state.zones.isNotEmpty()
    val showZonesEmpty = state.allPermissionsGranted && state.zones.isEmpty()
    val showFilterBar = state.allPermissionsGranted && state.nearbySpots.isNotEmpty()

    var base = 1 // handle / top item in LazyColumn
    if (showParkingFirst) {
        base += if (state.userParking != null) 1 else 2 // banner OR (header + empty)
        base += 1 // mid_divider
    }
    if (showZoneChips) base += 1 // zones_chips
    if (showZonesEmpty) base += 1 // zones_empty_card (inside spotsSection)
    base += 1 // spots_header
    if (showFilterBar) base += 1 // filter_bar
    if (state.allPermissionsGranted) base += 1 // report_spot_cta
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
    } else if (state.allPermissionsGranted) {
        item("parking_header") {
            HomeSectionHeader(
                title = stringResource(Res.string.home_where_to_park_title),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            )
        }
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
    showZonesEmpty: Boolean,
    onAddZone: () -> Unit,
) {
    // Zone empty card — onboarding prompt when no zones, at the top of the
    // discovery block so it reads "here's where your zones will appear above spots"
    if (showZonesEmpty) {
        item("zones_empty_card") {
            HomeZonesEmptyCard(
                onAddZone = onAddZone,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
        }
    }

    item("spots_header") {
        HomeSectionHeader(
            title = if (filteredSpots.isNotEmpty())
                stringResource(Res.string.home_feed_nearby_with_count, filteredSpots.size)
            else
                stringResource(Res.string.home_feed_nearby),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
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

    if (state.allPermissionsGranted) {
        item("report_spot_cta") {
            HomeReportSpotCard(
                onReport = { onIntent(HomeIntent.EnterReportMode) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
                ParkingSpotItem(
                    data = SpotUiState(
                        id = spot.id,
                        displayLocation = locationDisplayText(
                            spot.placeInfo, spot.address,
                            spot.location.latitude, spot.location.longitude,
                            showEmoji = false,
                        ),
                        distanceMeters = state.userGpsPoint?.let { gps ->
                            distanceMeters(
                                gps.latitude, gps.longitude,
                                spot.location.latitude, spot.location.longitude,
                            )
                        },
                        reportedAtMs = spot.location.timestamp,
                        reliability = spot.toReliabilityUiState(),
                        enRouteCount = spot.enRouteCount,
                        expiresAt = spot.expiresAt,
                    ),
                    isSelected = spot.id == selectedSpotId,
                    onClick = {
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

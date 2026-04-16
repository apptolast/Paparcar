
package io.apptolast.paparcar.presentation.home.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.presentation.home.HomeIntent
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.locationDisplayText
import io.apptolast.paparcar.presentation.util.toReliabilityUiState
import io.apptolast.paparcar.ui.components.ParkingSpotItem
import io.apptolast.paparcar.ui.components.SpotCardData
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_feed_nearby
import paparcar.composeapp.generated.resources.home_parked_section
import paparcar.composeapp.generated.resources.home_feed_nearby_with_count
import paparcar.composeapp.generated.resources.home_size_filter_all
import paparcar.composeapp.generated.resources.vehicle_size_large
import paparcar.composeapp.generated.resources.vehicle_size_medium
import paparcar.composeapp.generated.resources.vehicle_size_moto
import paparcar.composeapp.generated.resources.vehicle_size_small
import paparcar.composeapp.generated.resources.vehicle_size_van

@Composable
internal fun HomeSheetContent(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onCameraMove: (Double, Double) -> Unit,
    onParkingClick: () -> Unit,
    onManualPark: () -> Unit,
    onSpotSelect: (lat: Double, lon: Double, spotId: String) -> Unit,
    scrollState: ScrollState,
    spotScrollPositions: MutableMap<String, Int>,
) {
    val selectedSpotId = state.selectedItemId?.takeIf { it != HomeState.PARKING_ITEM_ID }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .navigationBarsPadding()
            .padding(top = 4.dp, bottom = 40.dp),
    ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.07f))

            // ── Section order: spots are the primary goal when no car is parked.
            // When the user has an active parking, show it first (it needs action to release).
            // When there is no parking, show spots first so the user doesn't have to scroll past
            // an empty/CTA card to reach actionable content.
            if (state.userParking != null) {
                ParkingSection(
                    state = state,
                    onParkingClick = onParkingClick,
                    onManualPark = onManualPark,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.07f),
                )
                SpotsSection(
                    state = state,
                    onIntent = onIntent,
                    onCameraMove = onCameraMove,
                    onSpotSelect = onSpotSelect,
                    selectedSpotId = selectedSpotId,
                    spotScrollPositions = spotScrollPositions,
                )
            } else {
                SpotsSection(
                    state = state,
                    onIntent = onIntent,
                    onCameraMove = onCameraMove,
                    onSpotSelect = onSpotSelect,
                    selectedSpotId = selectedSpotId,
                    spotScrollPositions = spotScrollPositions,
                )
                // Only show the manual-park CTA after spots — the user needs the map first,
                // and the CTA is a fallback action, not the primary one.
                if (state.allPermissionsGranted) {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.07f),
                    )
                    ParkingSection(
                        state = state,
                        onParkingClick = onParkingClick,
                        onManualPark = onManualPark,
                    )
                }
            }
        }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section composables (extracted to keep HomeSheetContent readable)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ParkingSection(
    state: HomeState,
    onParkingClick: () -> Unit,
    onManualPark: () -> Unit,
) {
    HomeSectionHeader(
        title = stringResource(Res.string.home_parked_section),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
    if (state.userParking != null) {
        HomeParkingRow(
            parking = state.userParking,
            userLocation = state.userGpsPoint?.let { Pair(it.latitude, it.longitude) },
            isSelected = state.selectedItemId == HomeState.PARKING_ITEM_ID,
            onSelect = onParkingClick,
        )
    } else if (state.allPermissionsGranted) {
        HomeParkingEmptyCard(
            onManualPark = onManualPark,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SpotsSection(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onCameraMove: (Double, Double) -> Unit,
    onSpotSelect: (lat: Double, lon: Double, spotId: String) -> Unit,
    selectedSpotId: String?,
    spotScrollPositions: MutableMap<String, Int>,
) {
    val filteredSpots = if (state.sizeFilter == null) {
        state.nearbySpots
    } else {
        state.nearbySpots.filter { it.sizeCategory == null || it.sizeCategory == state.sizeFilter }
    }

    HomeSectionHeader(
        title = if (filteredSpots.isNotEmpty())
            stringResource(Res.string.home_feed_nearby_with_count, filteredSpots.size)
        else
            stringResource(Res.string.home_feed_nearby),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )

    if (state.allPermissionsGranted && state.nearbySpots.isNotEmpty()) {
        HomeSizeFilterBar(
            selectedSize = state.sizeFilter,
            onFilterSelect = { size -> onIntent(HomeIntent.SetSizeFilter(size)) },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }

    when {
        !state.allPermissionsGranted -> HomePermissionsCard(
            onRequestPermissions = { onIntent(HomeIntent.LoadNearbySpots) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        state.isLoading -> SpotsSkeletonList()
        filteredSpots.isEmpty() -> HomeEmptySpots(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        else -> filteredSpots.forEachIndexed { index, spot ->
            ParkingSpotItem(
                data = SpotCardData(
                    id = spot.id,
                    displayLocation = locationDisplayText(
                        spot.placeInfo, spot.address,
                        spot.location.latitude, spot.location.longitude,
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
                modifier = Modifier.onGloballyPositioned { coords ->
                    spotScrollPositions[spot.id] = coords.positionInParent().y.toInt()
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
    val allLabel   = stringResource(Res.string.home_size_filter_all)
    val motoLabel  = stringResource(Res.string.vehicle_size_moto)
    val smallLabel = stringResource(Res.string.vehicle_size_small)
    val mediumLabel= stringResource(Res.string.vehicle_size_medium)
    val largeLabel = stringResource(Res.string.vehicle_size_large)
    val vanLabel   = stringResource(Res.string.vehicle_size_van)

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState()),
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
    badge: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            // Raised from 0.5f — section headers were nearly invisible before
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            letterSpacing = 0.8.sp,
        )
        if (badge != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

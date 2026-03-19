
package io.apptolast.paparcar.presentation.home.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.presentation.home.HomeIntent
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.home.PARKING_ITEM_ID
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_feed_nearby
import paparcar.composeapp.generated.resources.home_parked_section
import paparcar.composeapp.generated.resources.home_stats_free_spots_badge

@Composable
internal fun HomeSheetContent(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onCameraMove: (Double, Double) -> Unit,
    onParkingClick: () -> Unit,
    onManualPark: () -> Unit,
    onSpotSelect: (lat: Double, lon: Double, spotId: String) -> Unit,
    scrollState: ScrollState,
    selectedSpotId: String? = null,
    spotScrollPositions: MutableMap<String, Int>,
) {
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
            isSelected = state.selectedItemId == PARKING_ITEM_ID,
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
    HomeSectionHeader(
        title = stringResource(Res.string.home_feed_nearby),
        badge = if (state.nearbySpots.isNotEmpty())
            stringResource(Res.string.home_stats_free_spots_badge, state.nearbySpots.size)
        else null,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
    when {
        !state.allPermissionsGranted -> HomePermissionsCard(
            onRequestPermissions = { onIntent(HomeIntent.LoadNearbySpots) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        state.nearbySpots.isEmpty() -> HomeEmptySpots(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        else -> state.nearbySpots.forEachIndexed { index, spot ->
            HomeSpotRow(
                spot = spot,
                userLocation = state.userGpsPoint?.let { Pair(it.latitude, it.longitude) },
                isSelected = spot.id == selectedSpotId,
                onSelect = {
                    onCameraMove(spot.location.latitude, spot.location.longitude)
                    onSpotSelect(spot.location.latitude, spot.location.longitude, spot.id)
                },
                modifier = Modifier.onGloballyPositioned { coords ->
                    spotScrollPositions[spot.id] = coords.positionInParent().y.toInt()
                },
            )
            // Skip divider after the last item — no trailing separator
            if (index < state.nearbySpots.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                )
            }
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

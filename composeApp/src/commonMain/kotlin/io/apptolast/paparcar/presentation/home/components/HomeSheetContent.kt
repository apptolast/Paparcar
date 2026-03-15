
package io.apptolast.paparcar.presentation.home.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_feed_activity
import paparcar.composeapp.generated.resources.home_feed_nearby
import paparcar.composeapp.generated.resources.home_parked_section
import paparcar.composeapp.generated.resources.home_stats_free_spots_badge

@Composable
internal fun HomeSheetContent(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onCameraMove: (Double, Double) -> Unit,
    onParkingClick: () -> Unit,
    onParkingRelease: () -> Unit,
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
            .padding(bottom = 64.dp),
    ) {

        // ── Parking row — solo si está aparcado ───────────────────────────
        state.userParking?.let { parking ->
            HomeSectionHeader(
                title = stringResource(Res.string.home_parked_section),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
            HomeParkingRow(
                parking = parking,
                userLocation = state.userGpsPoint?.let { Pair(it.latitude, it.longitude) },
                onClick = onParkingClick,
                onRelease = onParkingRelease,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.07f),
            )
        }

        // ── Sección: Cerca de ti ──────────────────────────────────────────
        HomeSectionHeader(
            title = stringResource(Res.string.home_feed_nearby),
            badge = if (state.nearbySpots.isNotEmpty())
                stringResource(Res.string.home_stats_free_spots_badge, state.nearbySpots.size)
            else null,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        )

        when {
            !state.allPermissionsGranted -> HomePermissionsCard(
                onRequestPermissions = { onIntent(HomeIntent.LoadNearbySpots) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            state.nearbySpots.isEmpty() -> HomeEmptySpots(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            else -> state.nearbySpots.forEach { spot ->
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
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                )
            }
        }

        // ── Sección: Actividad ────────────────────────────────────────────
        if (state.nearbySpots.isNotEmpty()) {
            HomeSectionHeader(
                title = stringResource(Res.string.home_feed_activity),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
            )
            state.nearbySpots.take(5).forEach { spot ->
                HomeActivityRow(
                    spot = spot,
                    onClick = {
                        onCameraMove(spot.location.latitude, spot.location.longitude)
                        onSpotSelect(spot.location.latitude, spot.location.longitude, spot.id)
                    },
                )
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            letterSpacing = 0.8.sp,
        )
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

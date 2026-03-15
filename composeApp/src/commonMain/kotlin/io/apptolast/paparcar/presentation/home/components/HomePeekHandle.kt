package io.apptolast.paparcar.presentation.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.formatCoords
import io.apptolast.paparcar.presentation.util.formatDistance
import io.apptolast.paparcar.presentation.util.formatDriveTime
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_address_loading
import paparcar.composeapp.generated.resources.home_stats_free_spots_badge

@Composable
internal fun HomePeekHandle(
    state: HomeState,
    onParkingClick: () -> Unit,
    selectedSpotId: String? = null,
) {
    val freeCount = state.nearbySpots.size
    val selectedSpot = selectedSpotId?.let { id -> state.nearbySpots.find { it.id == id } }

    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {

        // Drag pill
        Box(
            modifier = Modifier
                .padding(top = 10.dp, bottom = 8.dp)
                .size(width = 32.dp, height = 4.dp)
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    CircleShape,
                )
                .align(Alignment.CenterHorizontally),
        )

        // Animated switch: selected spot info ↔ current location
        AnimatedContent(
            targetState = selectedSpot,
            transitionSpec = {
                if (targetState != null) {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut())
                } else {
                    (slideInVertically { -it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { it / 2 } + fadeOut())
                }
            },
            label = "peek_content",
        ) { spot ->
            if (spot != null) {
                SpotPeekRow(
                    spot = spot,
                    userLocation = state.userGpsPoint?.let { Pair(it.latitude, it.longitude) },
                )
            } else {
                LocationPeekRow(state = state, freeCount = freeCount)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Spot selected row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpotPeekRow(
    spot: Spot,
    userLocation: Pair<Double, Double>?,
) {
    val distM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, spot.location.latitude, spot.location.longitude)
    }
    val place = spot.placeInfo?.let { "${it.category.emoji} ${it.name}" }
    val address = spot.address?.displayLine
    val displayText = place ?: address ?: formatCoords(spot.location.latitude, spot.location.longitude)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.RadioButtonChecked,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
            if (distM != null) {
                Text(
                    text = "${formatDistance(distM)}  ·  ${formatDriveTime(distM)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Default location row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LocationPeekRow(state: HomeState, freeCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.userLocationInfo?.address?.displayLine
                    ?: stringResource(Res.string.home_address_loading),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
            val cityRegion = listOfNotNull(
                state.userLocationInfo?.address?.city,
                state.userLocationInfo?.address?.region,
            ).joinToString(", ")
            if (cityRegion.isNotEmpty()) {
                Text(
                    text = cityRegion,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Free spots badge
        Surface(
            color = if (freeCount > 0) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (freeCount > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        ),
                )
                Text(
                    stringResource(Res.string.home_stats_free_spots_badge, freeCount),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (freeCount > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

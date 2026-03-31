@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.driveTimeString
import io.apptolast.paparcar.presentation.util.distanceString
import io.apptolast.paparcar.presentation.util.locationDisplayText
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_empty_subtitle
import paparcar.composeapp.generated.resources.home_empty_title
import paparcar.composeapp.generated.resources.home_spot_freshness_hours
import paparcar.composeapp.generated.resources.home_spot_freshness_minutes
import paparcar.composeapp.generated.resources.home_spot_freshness_under_min

// ─────────────────────────────────────────────────────────────────────────────
// Freshness thresholds
// ─────────────────────────────────────────────────────────────────────────────

private const val FRESH_MINUTES = 5L
private const val RECENT_MINUTES = 15L

// ─────────────────────────────────────────────────────────────────────────────
// Spot row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun HomeSpotRow(
    spot: Spot,
    userLocation: Pair<Double, Double>?,
    onSelect: () -> Unit,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = { onSelect() },
        modifier = modifier,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                else Color.Transparent,
    ) {
        SpotRowContent(
            spot = spot,
            userLocation = userLocation,
            isSelected = isSelected,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Spot row — glass variant
//
// Same data as HomeSpotRow but rendered as a frosted-glass card:
//  • Semi-transparent rounded surface with a subtle border
//  • Items separated by vertical spacing instead of HorizontalDivider
//  • Intended to be used inside a Column with Arrangement.spacedBy(8.dp)
//    and horizontal padding of 12.dp instead of the default 0.dp
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun HomeSpotRowGlass(
    spot: Spot,
    userLocation: Pair<Double, Double>?,
    onSelect: () -> Unit,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    else
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)

    val cardBackground = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)

    Surface(
        onClick = onSelect,
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = cardBackground,
    ) {
        SpotRowContent(
            spot = spot,
            userLocation = userLocation,
            isSelected = isSelected,
            iconStyle = if (isSelected) SpotIconStyle.Selected else SpotIconStyle.Glass,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared row content
// ─────────────────────────────────────────────────────────────────────────────

private enum class SpotIconStyle { Default, Selected, Glass }

@Composable
private fun SpotRowContent(
    spot: Spot,
    userLocation: Pair<Double, Double>?,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    iconStyle: SpotIconStyle = if (isSelected) SpotIconStyle.Selected else SpotIconStyle.Default,
) {
    val distanceM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, spot.location.latitude, spot.location.longitude)
    }
    val displayText = locationDisplayText(
        placeInfo = spot.placeInfo,
        address = spot.address,
        lat = spot.location.latitude,
        lon = spot.location.longitude,
    )
    val ageMinutes = remember(spot.location.timestamp) {
        (Clock.System.now().toEpochMilliseconds() - spot.location.timestamp) / 60_000L
    }

    val iconBackground = when (iconStyle) {
        SpotIconStyle.Default  -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        SpotIconStyle.Selected -> MaterialTheme.colorScheme.primary
        SpotIconStyle.Glass    -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    }
    val iconTextColor = when (iconStyle) {
        SpotIconStyle.Default  -> MaterialTheme.colorScheme.primary
        SpotIconStyle.Selected -> MaterialTheme.colorScheme.onPrimary
        SpotIconStyle.Glass    -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }
    val subtitleAlpha = if (iconStyle == SpotIconStyle.Glass) 0.5f else 0.55f

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // "P" badge — consistent with the spot marker on the map
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "P",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = iconTextColor,
            )
        }

        // Text column — location (WHERE) on L1, distance + drive time on L2.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (distanceM != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${distanceString(distanceM)}  ·  ${driveTimeString(distanceM)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = subtitleAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Freshness chip — age of spot
        SpotFreshnessChip(ageMinutes = ageMinutes)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Freshness chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SpotFreshnessChip(ageMinutes: Long) {
    val containerColor = when {
        ageMinutes < FRESH_MINUTES  -> MaterialTheme.colorScheme.primaryContainer
        ageMinutes < RECENT_MINUTES -> MaterialTheme.colorScheme.secondaryContainer
        else                        -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        ageMinutes < FRESH_MINUTES  -> MaterialTheme.colorScheme.primary
        ageMinutes < RECENT_MINUTES -> MaterialTheme.colorScheme.secondary
        else                        -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }
    val label = when {
        ageMinutes < 1L  -> stringResource(Res.string.home_spot_freshness_under_min)
        ageMinutes < 60L -> stringResource(Res.string.home_spot_freshness_minutes, ageMinutes.toInt())
        else             -> stringResource(Res.string.home_spot_freshness_hours, (ageMinutes / 60L).toInt())
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Activity row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun HomeActivityRow(
    spot: Spot,
    onClick: () -> Unit,
) {
    val displayText = locationDisplayText(
        placeInfo = spot.placeInfo,
        address = spot.address,
        lat = spot.location.latitude,
        lon = spot.location.longitude,
    )

    Surface(
        onClick = onClick,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                // LocalParking icon removed — "P" badge consistent with spot markers
                Text(
                    "P",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun HomeEmptySpots(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            modifier = Modifier.size(32.dp),
        )
        Text(
            stringResource(Res.string.home_empty_title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Text(
            stringResource(Res.string.home_empty_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
    }
}
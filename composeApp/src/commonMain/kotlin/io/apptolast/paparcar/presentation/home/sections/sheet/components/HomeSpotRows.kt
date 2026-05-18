@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.presentation.util.SpotReliabilityUiState
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.distanceString
import io.apptolast.paparcar.presentation.util.driveTimeString
import io.apptolast.paparcar.presentation.util.locationDisplayText
import io.apptolast.paparcar.presentation.util.toReliabilityUiState
import io.apptolast.paparcar.ui.components.EnRouteIndicator
import io.apptolast.paparcar.ui.components.TTLIndicator
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_empty_subtitle
import paparcar.composeapp.generated.resources.home_empty_title
import paparcar.composeapp.generated.resources.home_filter_empty_clear
import paparcar.composeapp.generated.resources.home_filter_empty_subtitle
import paparcar.composeapp.generated.resources.home_filter_empty_title
import paparcar.composeapp.generated.resources.home_report_fab_cd
import paparcar.composeapp.generated.resources.home_spot_reliability_high
import paparcar.composeapp.generated.resources.home_spot_reliability_low
import paparcar.composeapp.generated.resources.home_spot_reliability_manual
import paparcar.composeapp.generated.resources.home_spot_reliability_medium

/**
 * Spot row (v1 redesign).
 *
 *  - 3dp left selection indicator (primary) so the row keeps its neutral fill.
 *  - Circular "P" badge whose colour mirrors the map marker tier
 *    (HIGH=primary, MEDIUM=secondary, LOW=error, MANUAL=tertiary).
 *  - Meta row: UPPERCASE reliability label + distance + drive time.
 */
@Composable
internal fun HomeSpotRow(
    spot: Spot,
    userLocation: Pair<Double, Double>?,
    onSelect: () -> Unit,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val reliability = spot.toReliabilityUiState()
    val palette = reliability.palette()

    val rowBg = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = SELECTED_ROW_BG_ALPHA)
    else
        Color.Transparent

    Surface(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth(),
        color = rowBg,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(SELECTION_INDICATOR_W_DP.dp)
                    .height(SELECTION_INDICATOR_H_DP.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    ),
            )
            SpotRowContent(
                spot = spot,
                userLocation = userLocation,
                palette = palette,
                modifier = Modifier
                    .padding(start = 13.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun SpotRowContent(
    spot: Spot,
    userLocation: Pair<Double, Double>?,
    palette: ReliabilityPalette,
    modifier: Modifier = Modifier,
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

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(BADGE_DP.dp)
                .clip(CircleShape)
                .background(palette.badgeBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "P",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = palette.badgeFg,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    palette.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.badgeBg,
                    maxLines = 1,
                )
                Text(
                    "  ·  ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = META_SEPARATOR_ALPHA),
                )
                if (distanceM != null) {
                    Text(
                        distanceString(distanceM),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = META_VALUE_ALPHA),
                    )
                    Text(
                        "  ·  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = META_SEPARATOR_ALPHA),
                    )
                    Text(
                        driveTimeString(distanceM),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = META_MUTED_ALPHA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        EnRouteIndicator(count = spot.enRouteCount)

        if (spot.expiresAt > 0L) {
            TTLIndicator(expiresAtMs = spot.expiresAt)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reliability palette — aligns spot rows with map markers.
// ─────────────────────────────────────────────────────────────────────────────

private data class ReliabilityPalette(
    val badgeBg: Color,
    val badgeFg: Color,
    val label: String,
)

@Composable
private fun SpotReliabilityUiState.palette(): ReliabilityPalette {
    val cs = MaterialTheme.colorScheme
    return when (this) {
        SpotReliabilityUiState.HIGH   -> ReliabilityPalette(cs.primary,   cs.onPrimary,   stringResource(Res.string.home_spot_reliability_high))
        SpotReliabilityUiState.MEDIUM -> ReliabilityPalette(cs.secondary, cs.onSecondary, stringResource(Res.string.home_spot_reliability_medium))
        SpotReliabilityUiState.LOW    -> ReliabilityPalette(cs.error,     cs.onError,     stringResource(Res.string.home_spot_reliability_low))
        SpotReliabilityUiState.MANUAL -> ReliabilityPalette(cs.tertiary,  cs.onTertiary,  stringResource(Res.string.home_spot_reliability_manual))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty states — surface card with centred icon/title/subtitle.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun HomeEmptySpots(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PapShapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = EMPTY_BG_ALPHA),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_ICON_ALPHA),
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(Res.string.home_empty_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(Res.string.home_empty_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_SUBTITLE_ALPHA),
            )
        }
    }
}

@Composable
internal fun HomeEmptyFilteredSpots(
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PapShapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = EMPTY_BG_ALPHA),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Outlined.FilterAltOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_ICON_ALPHA),
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(Res.string.home_filter_empty_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(Res.string.home_filter_empty_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_SUBTITLE_ALPHA),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(Res.string.home_filter_empty_clear),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onClearFilter),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Report spot CTA — primary full-width button placed at the end of the spot
// list. Mirrors the v1 button style (44dp / 12dp radius / primary fill).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun HomeReportSpotCard(
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onReport,
        modifier = modifier
            .fillMaxWidth()
            .height(REPORT_BUTTON_HEIGHT_DP.dp),
        shape = RoundedCornerShape(REPORT_BUTTON_CORNER_DP.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(
            Icons.Outlined.Campaign,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(Res.string.home_report_fab_cd),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Outlined.Add,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
    }
}

private const val SELECTION_INDICATOR_W_DP = 3
private const val SELECTION_INDICATOR_H_DP = 56
private const val BADGE_DP = 42
private const val REPORT_BUTTON_HEIGHT_DP = 44
private const val REPORT_BUTTON_CORNER_DP = 12

private const val SELECTED_ROW_BG_ALPHA = 0.18f
private const val META_SEPARATOR_ALPHA = 0.3f
private const val META_VALUE_ALPHA = 0.6f
private const val META_MUTED_ALPHA = 0.55f
private const val EMPTY_BG_ALPHA = 0.4f
private const val EMPTY_ICON_ALPHA = 0.25f
private const val EMPTY_SUBTITLE_ALPHA = 0.5f

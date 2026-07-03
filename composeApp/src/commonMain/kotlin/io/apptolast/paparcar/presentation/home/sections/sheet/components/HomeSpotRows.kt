@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.layout.PaddingValues
import io.apptolast.paparcar.ui.components.PapIconTile
import io.apptolast.paparcar.ui.components.PapListItem
import io.apptolast.paparcar.ui.components.PapOutlinedCard
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.FilterAltOff
import io.apptolast.paparcar.ui.illustrations.EmptySpotsIllustration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import io.apptolast.paparcar.ui.icons.icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.ui.theme.PaparcarType
import io.apptolast.paparcar.ui.theme.stateColors
import io.apptolast.paparcar.presentation.util.SpotReliabilityUiState
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.distanceString
import io.apptolast.paparcar.presentation.util.driveTimeString
import io.apptolast.paparcar.presentation.util.locationDisplayText
import io.apptolast.paparcar.presentation.util.toReliabilityUiState
import io.apptolast.paparcar.ui.components.EnRouteIndicator
import io.apptolast.paparcar.ui.components.PapEmptyStateCard
import io.apptolast.paparcar.ui.components.SpotPuckIcon
import io.apptolast.paparcar.ui.components.TTLIndicator
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_empty_subtitle
import paparcar.composeapp.generated.resources.home_empty_title
import paparcar.composeapp.generated.resources.home_filter_empty_clear
import paparcar.composeapp.generated.resources.home_filter_empty_subtitle
import paparcar.composeapp.generated.resources.home_filter_empty_title
import paparcar.composeapp.generated.resources.home_report_fab_cd
import paparcar.composeapp.generated.resources.home_report_subtitle
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
        // Same puck as the map marker, tail-less — one shared component keeps list and map in sync,
        // with the reliability tier encoded by colour/ring/badge. [HOME-PUCK-001]
        SpotPuckIcon(
            reliability = spot.toReliabilityUiState(),
            enRouteCount = spot.enRouteCount,
            modifier = Modifier.size(BADGE_DP.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(POI_ICON_GAP_DP.dp),
            ) {
                spot.placeInfo?.let { place ->
                    Icon(
                        imageVector = place.category.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(POI_ICON_DP.dp),
                    )
                }
                Text(
                    text = displayText,
                    style = PaparcarType.current.body,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(2.dp))
            // Data-dense meta line ("FIABLE · 80 m · 1 min") — condensed per the typography
            // mechanism: data tokens repeated in rows use Barlow, prose stays Inter. Label and
            // values share the same condensed body so the line reads as one unit. [UI-REGRESSION]
            val type = PaparcarType.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    palette.label.uppercase(),
                    style = type.badge.copy(fontWeight = FontWeight.Bold),
                    color = palette.badgeBg,
                    maxLines = 1,
                )
                Text(
                    META_SEPARATOR,
                    style = type.metadata,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = META_SEPARATOR_ALPHA),
                )
                if (distanceM != null) {
                    Text(
                        distanceString(distanceM),
                        style = type.metadata.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = META_VALUE_ALPHA),
                    )
                    Text(
                        META_SEPARATOR,
                        style = type.metadata,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = META_SEPARATOR_ALPHA),
                    )
                    Text(
                        driveTimeString(distanceM),
                        style = type.metadata,
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
    val sc = stateColors()
    val label = when (this) {
        SpotReliabilityUiState.HIGH   -> stringResource(Res.string.home_spot_reliability_high)
        SpotReliabilityUiState.MEDIUM -> stringResource(Res.string.home_spot_reliability_medium)
        SpotReliabilityUiState.LOW    -> stringResource(Res.string.home_spot_reliability_low)
        SpotReliabilityUiState.MANUAL -> stringResource(Res.string.home_spot_reliability_manual)
    }
    return ReliabilityPalette(sc.bg, sc.on, label)
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty states — surface card with centred icon/title/subtitle.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun HomeEmptySpots(modifier: Modifier = Modifier) {
    PapEmptyStateCard(modifier = modifier) {
        EmptySpotsIllustration(
            modifier = Modifier.size(EMPTY_ILLUSTRATION_W.dp, EMPTY_ILLUSTRATION_H.dp),
        )
        Spacer(Modifier.height(2.dp))
        // Shared empty-state recipe (titleSmall Bold + bodySmall) — same hierarchy as
        // HomeEmptyFilteredSpots so the two states read as one family. [HOME-VEH-REFINE-001]
        Text(
            stringResource(Res.string.home_empty_title),
            style = PaparcarType.current.rowTitle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(Res.string.home_empty_subtitle),
            style = PaparcarType.current.caption,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_SUBTITLE_ALPHA),
        )
    }
}

@Composable
internal fun HomeEmptyFilteredSpots(
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PapEmptyStateCard(modifier = modifier) {
        Icon(
            Icons.Rounded.FilterAltOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_ICON_ALPHA),
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(2.dp))
        // Same recipe as HomeEmptySpots (titleSmall Bold + bodySmall). [HOME-VEH-REFINE-001]
        Text(
            stringResource(Res.string.home_filter_empty_title),
            style = PaparcarType.current.rowTitle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(Res.string.home_filter_empty_subtitle),
            style = PaparcarType.current.caption,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_SUBTITLE_ALPHA),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(Res.string.home_filter_empty_clear),
            style = PaparcarType.current.label,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onClearFilter),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Report spot CTA — same molde as HomeParkingRow (icon box + title + subtitle)
// but with a trailing "+" instead of a chevron. The "+" alone signals the
// add-action; the redundant "Notify the community" pill is dropped.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun HomeReportSpotCard(
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PapOutlinedCard(
        onClick = onReport,
        modifier = modifier.fillMaxWidth(),
        shape = PapShapes.cardSmall,
    ) {
        PapListItem(
            title = stringResource(Res.string.home_report_fab_cd),
            subtitle = stringResource(Res.string.home_report_subtitle),
            titleStyle = PaparcarType.current.rowTitle,
            titleWeight = FontWeight.Bold,
            titleMaxLines = 1,
            subtitleStyle = PaparcarType.current.label,
            subtitleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = PRIMARY_CARD_SUBTITLE_ALPHA),
            subtitleMaxLines = 1,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            gap = 12.dp,
            leading = {
                PapIconTile(
                    icon = Icons.Rounded.Campaign,
                    size = PRIMARY_CARD_ICON_BOX_DP.dp,
                    shape = RoundedCornerShape(PRIMARY_CARD_ICON_CORNER_DP.dp),
                    container = MaterialTheme.colorScheme.surfaceContainer,
                    iconSize = 22.dp,
                )
            },
            trailing = {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            },
        )
    }
}

private const val SELECTION_INDICATOR_W_DP = 3
private const val SELECTION_INDICATOR_H_DP = 56
private const val BADGE_DP = 42
private const val POI_ICON_DP = 15
private const val POI_ICON_GAP_DP = 5
private const val SELECTED_ROW_BG_ALPHA = 0.30f
// Separator between data tokens on the meta line ("FIABLE · 80 m · 1 min").
private const val META_SEPARATOR = "  ·  "
private const val META_SEPARATOR_ALPHA = 0.3f
private const val META_VALUE_ALPHA = 0.6f
private const val META_MUTED_ALPHA = 0.55f
private const val PRIMARY_CARD_SUBTITLE_ALPHA = 0.55f
private const val PRIMARY_CARD_ICON_BOX_DP = 44
private const val PRIMARY_CARD_ICON_CORNER_DP = 14
private const val EMPTY_ICON_ALPHA = 0.25f
private const val EMPTY_SUBTITLE_ALPHA = 0.5f
private const val EMPTY_ILLUSTRATION_W = 180
private const val EMPTY_ILLUSTRATION_H = 154

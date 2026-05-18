@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DirectionsCar
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
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.distanceString
import io.apptolast.paparcar.presentation.util.locationDisplayText
import io.apptolast.paparcar.presentation.util.relativeTimeText
import io.apptolast.paparcar.presentation.util.walkTimeString
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_manual_park_pill
import paparcar.composeapp.generated.resources.home_manual_park_subtitle
import paparcar.composeapp.generated.resources.home_manual_park_title

/**
 * Parking row (v1 redesign) — distinct "card" affordance so the user's session
 * stands out from the spot list below.
 *
 * Container: primary fill (selected) or primaryContainer tint (default) with a
 * 1dp outline; left icon box (44dp, primary bg, white car); single-line subtitle
 * "distance · walk · timeAgo"; trailing chevron as tap affordance.
 */
@Composable
internal fun HomeParkingRow(
    parking: UserParking,
    userLocation: Pair<Double, Double>?,
    isSelected: Boolean = false,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayText = locationDisplayText(
        placeInfo = parking.placeInfo,
        address = parking.address,
        lat = parking.location.latitude,
        lon = parking.location.longitude,
    )
    val distanceM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, parking.location.latitude, parking.location.longitude)
    }
    val timeAgo = relativeTimeText(parking.location.timestamp)

    val cardBg = if (isSelected) MaterialTheme.colorScheme.primary
                 else MaterialTheme.colorScheme.primaryContainer.copy(alpha = SELECTED_BG_ALPHA)
    val borderColor = if (isSelected) Color.Transparent
                      else MaterialTheme.colorScheme.outline.copy(alpha = OUTLINE_ALPHA)
    val titleColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                     else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = SUBTITLE_ALPHA_ON_PRIMARY)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = SUBTITLE_ALPHA_DEFAULT)
    val iconBg = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = ICON_BG_ALPHA_ON_PRIMARY)
                 else MaterialTheme.colorScheme.primary
    val iconTint = MaterialTheme.colorScheme.onPrimary

    Surface(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CARD_CORNER_DP.dp),
        color = cardBg,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(ICON_BOX_DP.dp)
                    .clip(RoundedCornerShape(ICON_BOX_CORNER_DP.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DirectionsCar,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                val subtitle = buildString {
                    if (distanceM != null) {
                        append(distanceString(distanceM))
                        append("  ·  ")
                        append(walkTimeString(distanceM))
                        append("  ·  ")
                    }
                    append(timeAgo)
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CHEVRON_ALPHA),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty parking — CTA to manually register parking
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Empty-state card matching the parking row "molde" but with a softer fill +
 * outline and an inline pill action ("Marcar"). [HOME-UX-006]
 */
@Composable
internal fun HomeParkingEmptyCard(
    onManualPark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onManualPark,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(EMPTY_CARD_CORNER_DP.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = EMPTY_CARD_BG_ALPHA),
        border = BorderStroke(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = OUTLINE_ALPHA),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(ICON_BOX_DP.dp)
                    .clip(RoundedCornerShape(ICON_BOX_CORNER_DP.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DirectionsCar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.home_manual_park_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(Res.string.home_manual_park_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBTITLE_ALPHA_EMPTY),
                )
            }
            Surface(
                shape = RoundedCornerShape(PILL_RADIUS_DP.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        stringResource(Res.string.home_manual_park_pill),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

private const val CARD_CORNER_DP = 14
private const val EMPTY_CARD_CORNER_DP = 16
private const val ICON_BOX_DP = 44
private const val ICON_BOX_CORNER_DP = 14
private const val PILL_RADIUS_DP = 999
private const val SELECTED_BG_ALPHA = 0.35f
private const val OUTLINE_ALPHA = 0.4f
private const val SUBTITLE_ALPHA_ON_PRIMARY = 0.8f
private const val SUBTITLE_ALPHA_DEFAULT = 0.6f
private const val SUBTITLE_ALPHA_EMPTY = 0.55f
private const val ICON_BG_ALPHA_ON_PRIMARY = 0.18f
private const val CHEVRON_ALPHA = 0.6f
private const val EMPTY_CARD_BG_ALPHA = 0.4f

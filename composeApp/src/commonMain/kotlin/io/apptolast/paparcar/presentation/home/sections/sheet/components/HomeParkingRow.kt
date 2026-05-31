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
import androidx.compose.foundation.shape.CircleShape
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
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.presentation.home.VehicleCard
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.distanceString
import io.apptolast.paparcar.presentation.util.locationDisplayText
import io.apptolast.paparcar.presentation.util.relativeTimeText
import io.apptolast.paparcar.presentation.util.walkTimeString
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.ui.theme.vehicleStateColors
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_vehicle_card_park_cta
import paparcar.composeapp.generated.resources.home_vehicle_card_status_empty
import paparcar.composeapp.generated.resources.home_vehicle_fallback_name

/**
 * Unified per-vehicle row used by the "TUS VEHÍCULOS" section. Renders one of
 * two variants based on whether [card] holds an active session:
 *
 *  - **Parked** (`card.session != null`): vehicle name as title, location +
 *    "distance · walk · timeAgo" as subtitle, trailing ChevronRight; tap →
 *    select that session.
 *  - **Empty** (`card.session == null`): vehicle name as title, "Sin aparcar"
 *    subtitle, trailing "Aparcar" pill; tap → enter AddingParking pre-bound
 *    to this vehicle. [MULTI-PARKING-001]
 *
 * Single composable so the icon-box molde, padding, and selection palette are
 * defined once across both states. The row is tappable as a whole — the pill
 * is purely visual and the parent click handler reads [card.session] to
 * decide the right action.
 */
@Composable
internal fun HomeVehicleCard(
    card: VehicleCard,
    userLocation: Pair<Double, Double>?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vehicleName = card.vehicle.displayName(
        fallback = stringResource(Res.string.home_vehicle_fallback_name),
    )
    val session = card.session

    val vc = vehicleStateColors()
    val cardBg = when {
        isSelected -> MaterialTheme.colorScheme.primary
        session != null -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = SELECTED_BG_ALPHA)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderColor = if (isSelected) Color.Transparent
                      else MaterialTheme.colorScheme.outline.copy(alpha = OUTLINE_ALPHA)
    val titleColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                     else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = SUBTITLE_ALPHA_ON_PRIMARY)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = SUBTITLE_ALPHA_DEFAULT)
    val iconBg = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = ICON_BG_ALPHA_ON_PRIMARY)
        session != null -> vc.bg
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val iconTint = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        session != null -> vc.on
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = PapShapes.cardSmall,
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
                    .clip(CircleShape)
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
                    text = vehicleName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (session != null)
                        parkedSubtitle(session, userLocation)
                    else
                        stringResource(Res.string.home_vehicle_card_status_empty),
                    style = MaterialTheme.typography.labelSmall,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (session != null) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = CHEVRON_ALPHA),
                    modifier = Modifier.size(22.dp),
                )
            } else {
                ParkPill()
            }
        }
    }
}

@Composable
private fun ParkPill() {
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
                stringResource(Res.string.home_vehicle_card_park_cta),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun parkedSubtitle(
    parking: UserParking,
    userLocation: Pair<Double, Double>?,
): String {
    val locationText = locationDisplayText(
        placeInfo = parking.placeInfo,
        address = parking.address,
        lat = parking.location.latitude,
        lon = parking.location.longitude,
    )
    val distanceM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, parking.location.latitude, parking.location.longitude)
    }
    val timeAgo = relativeTimeText(parking.location.timestamp)
    return buildString {
        append(locationText)
        if (distanceM != null) {
            append("  ·  ")
            append(distanceString(distanceM))
            append(" · ")
            append(walkTimeString(distanceM))
        }
        append("  ·  ")
        append(timeAgo)
    }
}

private const val ICON_BOX_DP = 44
private const val PILL_RADIUS_DP = 999
private const val SELECTED_BG_ALPHA = 0.35f
private const val OUTLINE_ALPHA = 0.4f
private const val SUBTITLE_ALPHA_ON_PRIMARY = 0.8f
private const val SUBTITLE_ALPHA_DEFAULT = 0.6f
private const val ICON_BG_ALPHA_ON_PRIMARY = 0.18f
private const val CHEVRON_ALPHA = 0.6f

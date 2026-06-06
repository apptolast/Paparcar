package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import io.apptolast.paparcar.presentation.util.SpotReliabilityLevel
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.distanceString
import io.apptolast.paparcar.ui.icons.icon
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.ui.theme.stateColors
import io.apptolast.paparcar.ui.theme.vehicleStateColors
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_peek_parked_label
import paparcar.composeapp.generated.resources.home_vehicle_card_status_empty
import paparcar.composeapp.generated.resources.home_vehicle_chip_badge_active
import paparcar.composeapp.generated.resources.home_vehicle_chip_status_parked
import paparcar.composeapp.generated.resources.home_vehicle_fallback_name

/**
 * Compact chip used in the vehicles LazyRow. Surfaces two orthogonal dimensions:
 *
 *  - **Detection badge**: how the vehicle is tracked — "BT" pill, "Active" pill, or nothing.
 *  - **Session status**: "Parked · Xm" in green when [card.session] is non-null;
 *    "Not parked" (muted) otherwise.
 *
 * Border is PapGreen-toned when parked, neutral otherwise.
 * Tap always fires [onClick]; the caller routes to session peek or AddingParking mode.
 */
@Composable
internal fun HomeVehicleChip(
    card: VehicleCard,
    userLocation: Pair<Double, Double>?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vehicle = card.vehicle
    val session = card.session
    val isParked = session != null
    val badge = vehicle.detectionBadge()

    val vehicleName = vehicle.displayName(fallback = stringResource(Res.string.home_vehicle_fallback_name))
    val badgeLabel = if (badge == DetectionBadge.ACTIVE) stringResource(Res.string.home_vehicle_chip_badge_active) else null
    val statusText = chipStatusText(session, userLocation)

    val parkedAccent = SpotReliabilityLevel.HIGH.stateColors().bg
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isParked   -> parkedAccent
        else       -> MaterialTheme.colorScheme.outline.copy(alpha = OUTLINE_ALPHA)
    }
    val cardBg = if (isSelected) MaterialTheme.colorScheme.primary
                 else MaterialTheme.colorScheme.surfaceContainerHigh
    val nameColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                   else MaterialTheme.colorScheme.onSurface
    val mutedColor = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = MUTED_ALPHA)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED_ALPHA)
    val statusColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = MUTED_ALPHA)
        isParked   -> parkedAccent
        else       -> mutedColor
    }
    val vc = vehicleStateColors()
    val iconBg = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = ICON_BG_ALPHA)
        isParked   -> vc.bg
        else       -> MaterialTheme.colorScheme.surfaceContainer
    }
    val iconTint = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isParked   -> vc.on
        else       -> MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = onClick,
        modifier = modifier.width(CHIP_WIDTH_DP.dp),
        shape = PapShapes.cardSmall,
        color = cardBg,
        border = BorderStroke(BORDER_DP.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(ICON_BOX_DP.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        vehicle.sizeCategory.icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    vehicleName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = nameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(3.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (badge) {
                    DetectionBadge.BLUETOOTH -> DetectionLabel(text = "BT", textColor = mutedColor)
                    DetectionBadge.ACTIVE    -> if (badgeLabel != null) DetectionLabel(text = badgeLabel, textColor = mutedColor)
                    DetectionBadge.NONE      -> Unit
                }
                if (badge != DetectionBadge.NONE) {
                    Text(BULLET, style = MaterialTheme.typography.labelSmall, color = mutedColor)
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DetectionLabel(text: String, textColor: Color) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun chipStatusText(session: UserParking?, userLocation: Pair<Double, Double>?): String {
    if (session == null) return stringResource(Res.string.home_vehicle_card_status_empty)
    val distanceM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, session.location.latitude, session.location.longitude)
    }
    return if (distanceM != null) {
        stringResource(Res.string.home_vehicle_chip_status_parked, distanceString(distanceM))
    } else {
        stringResource(Res.string.home_peek_parked_label)
    }
}

private enum class DetectionBadge { BLUETOOTH, ACTIVE, NONE }

private fun Vehicle.detectionBadge(): DetectionBadge = when {
    bluetoothDeviceId != null -> DetectionBadge.BLUETOOTH
    isActive                  -> DetectionBadge.ACTIVE
    else                      -> DetectionBadge.NONE
}

private const val CHIP_WIDTH_DP = 148
private const val ICON_BOX_DP = 28
private const val BORDER_DP = 1
private const val OUTLINE_ALPHA = 0.4f
private const val MUTED_ALPHA = 0.6f
private const val ICON_BG_ALPHA = 0.18f
private const val BULLET = "·"

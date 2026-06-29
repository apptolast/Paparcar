package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.presentation.home.VehicleCard
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.distanceString
import io.apptolast.paparcar.ui.components.VehicleBadgeTone
import io.apptolast.paparcar.ui.components.VehicleGlyph
import io.apptolast.paparcar.ui.components.vehicleBadgeAccent
import io.apptolast.paparcar.ui.theme.PapOutlineVariantLight
import io.apptolast.paparcar.ui.theme.PapShapes
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_peek_parked_label
import paparcar.composeapp.generated.resources.home_vehicle_card_status_empty
import paparcar.composeapp.generated.resources.home_vehicle_chip_badge_active
import paparcar.composeapp.generated.resources.home_vehicle_chip_status_parked
import paparcar.composeapp.generated.resources.home_vehicle_fallback_name

/**
 * Compact chip in the vehicles LazyRow. [MULTI-PARKING-001] [DET-READY-001k]
 *
 * Uses the bare full-colour car ([VehicleGlyph], no disc) — the same pictogram as the parked peek
 * and (framed in its tag) the map marker; an inactive vehicle reads dimmed. The second row
 * surfaces the two orthogonal facts the user needs at a glance: how the vehicle is tracked
 * (**BT** / **Active**) and its park status (**Parked · Xm** in green, or a muted **Not parked**).
 * No selection ring: tapping a chip transforms the sheet to the vehicle's state.
 */
@Composable
internal fun HomeVehicleChip(
    card: VehicleCard,
    userLocation: Pair<Double, Double>?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vehicle = card.vehicle
    val session = card.session
    val isParked = session != null
    val isBtPaired = vehicle.bluetoothDeviceId != null
    val isActive = vehicle.isActive
    val cs = MaterialTheme.colorScheme

    // Vehicle identity tone — mirrors the on-map marker so the chip reads the same: BT blue,
    // inactive grey (even when parked), active green. The "Parked · Xm" status text below stays
    // green regardless, to signal that the parking SESSION is active. [MOTION-POLISH-001]
    val tone = when {
        isBtPaired -> VehicleBadgeTone.Bluetooth
        !isActive  -> VehicleBadgeTone.Inactive
        isParked   -> VehicleBadgeTone.Parked
        else       -> VehicleBadgeTone.Idle
    }
    val parkedColor = vehicleBadgeAccent(VehicleBadgeTone.Parked)
    // Border colour for a PARKED chip — solid state colour at the same weight as the green, mirroring
    // the map marker: BT blue, inactive solid grey (NOT the faded "no session" outline), active green.
    // Non-parked chips keep the faded neutral outline below. [MOTION-POLISH-001]
    val parkedBorderColor = when (tone) {
        VehicleBadgeTone.Bluetooth -> cs.tertiary
        VehicleBadgeTone.Inactive  -> PapOutlineVariantLight // exact marker inactive grey
        else                       -> cs.primary
    }
    val muted = cs.onSurface.copy(alpha = MUTED_ALPHA)

    val vehicleName = vehicle.displayName(fallback = stringResource(Res.string.home_vehicle_fallback_name))
    val activeLabel = stringResource(Res.string.home_vehicle_chip_badge_active)

    Surface(
        onClick = onClick,
        modifier = modifier.width(CHIP_WIDTH_DP.dp),
        shape = PapShapes.cardSmall,
        // Only a parked chip earns a coloured border; everything else stays neutral (no amber). [DET-READY-001k]
        border = BorderStroke(
            BORDER_DP.dp,
            if (isParked) parkedBorderColor else cs.outline.copy(alpha = OUTLINE_ALPHA),
        ),
        color = cs.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                VehicleGlyph(
                    carbody = vehicle.carbodyType,
                    size = vehicle.sizeCategory,
                    glyphSize = ICON_BOX_DP.dp,
                    color = vehicle.color,
                )
                Text(
                    vehicleName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                // Reserve the pill's height so chips stay equal-height whether or not the
                // status row renders a DetectionLabel pill (active/BT) vs. plain text
                // ("Sin aparcar"). LazyRow measures items independently, so IntrinsicSize
                // can't equalize them — a fixed min on the tallest variant does. [VEH-CHIP-HEIGHT]
                modifier = Modifier.heightIn(min = STATUS_ROW_MIN_HEIGHT_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Detection indicator: BT supersedes Active; inactive vehicles show nothing.
                val indicator = when {
                    isBtPaired -> BT_LABEL
                    isActive -> activeLabel
                    else -> null
                }
                if (indicator != null) {
                    DetectionLabel(text = indicator, textColor = muted)
                    Text(BULLET, style = MaterialTheme.typography.labelSmall, color = muted)
                }
                Text(
                    text = statusText(session, userLocation),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isParked) FontWeight.SemiBold else FontWeight.Normal,
                    // Parked → always green (active parking session), even if the vehicle is inactive.
                    color = if (isParked) parkedColor else muted,
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
private fun statusText(session: UserParking?, userLocation: Pair<Double, Double>?): String {
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

private const val CHIP_WIDTH_DP = 148
// Min height of the status row — sized to the DetectionLabel pill (labelSmall + 1dp
// vertical padding ≈ 18dp) so the plain-text variant reserves the same height. [VEH-CHIP-HEIGHT]
private const val STATUS_ROW_MIN_HEIGHT_DP = 20
private const val ICON_BOX_DP = 28
private const val BORDER_DP = 1
private const val OUTLINE_ALPHA = 0.4f
private const val MUTED_ALPHA = 0.6f
private const val BT_LABEL = "BT"
private const val BULLET = "·"

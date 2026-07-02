package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.model.VehicleMonitoringStatus
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.domain.model.monitoringStatus
import io.apptolast.paparcar.presentation.home.VehicleCard
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.distanceString
import io.apptolast.paparcar.ui.components.VehicleGlyph
import io.apptolast.paparcar.ui.theme.PapDriveBlue
import io.apptolast.paparcar.ui.theme.PapOutlineVariantLight
import io.apptolast.paparcar.ui.theme.PapShapes
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_peek_parked_label
import paparcar.composeapp.generated.resources.home_vehicle_chip_badge_active
import paparcar.composeapp.generated.resources.home_vehicle_chip_badge_bt
import paparcar.composeapp.generated.resources.home_vehicle_chip_mark_parking
import paparcar.composeapp.generated.resources.home_vehicle_chip_status_candidate
import paparcar.composeapp.generated.resources.home_vehicle_chip_status_driving
import paparcar.composeapp.generated.resources.home_vehicle_chip_status_parked
import paparcar.composeapp.generated.resources.home_vehicle_fallback_name
import paparcar.composeapp.generated.resources.home_vehicle_status_inactive

/**
 * Compact chip in the vehicles LazyRow — an identity column of **status eyebrow → name → parking
 * line**, next to the vehicle glyph. The light eyebrow ([ChipEyebrow]) carries the monitoring state
 * (Active / BT / Inactive) as a small accent dot + label, so its colour is never mistaken for the
 * parking status. A live trip overrides the eyebrow with a Driving / Parking… label + radar halo.
 * The parking session is also signalled by the border colour. In single-vehicle mode ([fillWidth])
 * the card fills the row width with a roomier layout + trailing chevron.
 * No selection ring: tapping a chip transforms the sheet to the vehicle's state.
 * [MULTI-PARKING-001] [HOME-CARDS-001]
 */
@Composable
internal fun HomeVehicleChip(
    card: VehicleCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDriving: Boolean = false,
    // Candidate phase: the trip stopped and the user is walking away — show a distinct
    // "parking…" label (green, hinting the upcoming parked state) instead of "Driving". [DET-PHASE-001]
    isCandidate: Boolean = false,
    // Single-vehicle mode: the card fills the row width with a roomier layout (bigger glyph + name,
    // trailing chevron) instead of the compact strip chip. [HOME-CARDS-001]
    fillWidth: Boolean = false,
    // User position, to show the parked distance ("Aparcado · 120 m"). Null → plain "Aparcado".
    userLocation: Pair<Double, Double>? = null,
) {
    val vehicle = card.vehicle
    val session = card.session
    val isParked = session != null
    val cs = MaterialTheme.colorScheme
    val monitoring = vehicle.monitoringStatus()

    // Border encodes the parking session at a glance (the eyebrow encodes the monitoring config):
    // driving = live blue; parked = the state colour (BT blue / inactive grey / active green);
    // otherwise a faint neutral outline. [CHIP-DRIVING-001] [MOTION-POLISH-001]
    val borderColor = when {
        isDriving -> PapDriveBlue
        isParked -> when (monitoring) {
            is VehicleMonitoringStatus.Bluetooth -> cs.tertiary
            VehicleMonitoringStatus.Inactive     -> PapOutlineVariantLight
            VehicleMonitoringStatus.Active        -> cs.primary
        }
        else -> cs.outline.copy(alpha = OUTLINE_ALPHA)
    }

    val vehicleName = vehicle.displayName(fallback = stringResource(Res.string.home_vehicle_fallback_name))
    val glyphSize = if (fillWidth) ICON_BOX_WIDE_DP else ICON_BOX_DP

    Surface(
        onClick = onClick,
        // Full width for a single vehicle; otherwise adaptive width so the name breathes without
        // truncating in the horizontal strip (users rarely have >2–3 cars). [HOME-CARDS-001]
        modifier = if (fillWidth) modifier.fillMaxWidth()
        else modifier.widthIn(min = CHIP_MIN_WIDTH_DP.dp, max = CHIP_MAX_WIDTH_DP.dp),
        shape = PapShapes.cardSmall,
        border = BorderStroke(if (isDriving) DRIVING_BORDER_DP.dp else BORDER_DP.dp, borderColor),
        color = cs.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (fillWidth) WIDE_H_PAD_DP.dp else 10.dp,
                vertical = if (fillWidth) WIDE_V_PAD_DP.dp else 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (fillWidth) 14.dp else 10.dp),
        ) {
            // While driving, a pulsing "radar" halo expands behind the car — the same en-route
            // blue as its map puck, so the eye is drawn to the vehicle in motion. [CHIP-DRIVING-001]
            Box(contentAlignment = Alignment.Center) {
                if (isDriving) DrivingRadarHalo(diameter = glyphSize.dp)
                VehicleGlyph(
                    carbody = vehicle.carbodyType,
                    size = vehicle.sizeCategory,
                    glyphSize = glyphSize.dp,
                    color = vehicle.color,
                )
            }
            // Identity column: status eyebrow → name (hero) → parking line. On Home the parking
            // state is the actionable fact (size lives in the Vehicles screen). [HOME-CARDS-001]
            Column(modifier = Modifier.weight(1f, fill = fillWidth)) {
                VehicleChipEyebrow(monitoring = monitoring, isDriving = isDriving, isCandidate = isCandidate)
                Spacer(Modifier.height(EYEBROW_NAME_GAP_DP.dp))
                Text(
                    vehicleName,
                    style = if (fillWidth) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(NAME_PARK_GAP_DP.dp))
                if (session != null) {
                    // Parked — calm, factual (the border already carries the state colour). Shows the
                    // distance to the car when the user's position is known.
                    val distanceText = userLocation?.let { (uLat, uLon) ->
                        distanceString(
                            distanceMeters(uLat, uLon, session.location.latitude, session.location.longitude),
                        )
                    }
                    Text(
                        text = if (distanceText != null)
                            stringResource(Res.string.home_vehicle_chip_status_parked, distanceText)
                        else stringResource(Res.string.home_peek_parked_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                    )
                } else {
                    // Not parked — a primary-accent CTA hinting the tap action (tap enters "mark parking").
                    Text(
                        text = stringResource(Res.string.home_vehicle_chip_mark_parking),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Trailing chevron only in the full-width single-vehicle card — signals the card is
            // tappable and fills the extra width so the content isn't left-stranded. [HOME-CARDS-001]
            if (fillWidth) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(CHEVRON_DP.dp),
                )
            }
        }
    }
}

/**
 * The status overline above the name. A LIGHT variant of the Vehicles hero pill: a small accent
 * dot / BT glyph + a short uppercase label in the accent colour, with NO filled background — so the
 * vehicle NAME stays the visual hero and isn't pushed down by a heavy pill. A live trip
 * (driving / candidate) overrides the monitoring state. [HOME-CARDS-001]
 */
@Composable
private fun VehicleChipEyebrow(
    monitoring: VehicleMonitoringStatus,
    isDriving: Boolean,
    isCandidate: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    when {
        isDriving -> ChipEyebrow(
            text = stringResource(
                if (isCandidate) Res.string.home_vehicle_chip_status_candidate
                else Res.string.home_vehicle_chip_status_driving,
            ),
            accent = if (isCandidate) cs.primary else PapDriveBlue,
        )
        monitoring is VehicleMonitoringStatus.Bluetooth -> ChipEyebrow(
            text = stringResource(Res.string.home_vehicle_chip_badge_bt),
            accent = cs.tertiary,
            bluetooth = true,
        )
        monitoring is VehicleMonitoringStatus.Active -> ChipEyebrow(
            text = stringResource(Res.string.home_vehicle_chip_badge_active),
            accent = cs.primary,
        )
        else -> ChipEyebrow( // Inactive
            text = stringResource(Res.string.home_vehicle_status_inactive),
            accent = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChipEyebrow(text: String, accent: Color, bluetooth: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EYEBROW_MARKER_GAP_DP.dp),
    ) {
        if (bluetooth) {
            Icon(
                imageVector = Icons.Rounded.Bluetooth,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(EYEBROW_BT_ICON_DP.dp),
            )
        } else {
            Box(
                Modifier
                    .size(EYEBROW_DOT_DP.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = EYEBROW_TEXT_SP.sp,
            lineHeight = EYEBROW_TEXT_SP.sp,
            letterSpacing = EYEBROW_TRACKING_SP.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Pulsing "radar" halo behind the car glyph while a trip is being detected — two en-route-blue rings
 * expanding outward and fading, half a period out of phase. Contained within [diameter] so it never
 * shifts the chip layout; reads as "this car is live / in motion". [CHIP-DRIVING-001]
 */
@Composable
private fun DrivingRadarHalo(diameter: Dp) {
    val transition = rememberInfiniteTransition(label = "driving_radar")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(RADAR_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "driving_radar_progress",
    )
    Canvas(Modifier.size(diameter)) {
        val maxR = size.minDimension / 2f
        val stroke = RADAR_STROKE.toPx()
        listOf(progress, (progress + RADAR_PHASE_OFFSET) % 1f).forEach { p ->
            drawCircle(
                color = PapDriveBlue.copy(alpha = (1f - p) * RADAR_MAX_ALPHA),
                radius = maxR * (RADAR_MIN_FRACTION + p * (1f - RADAR_MIN_FRACTION)),
                style = Stroke(width = stroke),
            )
        }
    }
}

// Adaptive width bounds — card grows to fit "Toyota Corolla" without truncating, capped so a long
// name never dominates the LazyRow. [HOME-CARDS-001]
private const val CHIP_MIN_WIDTH_DP = 150
private const val CHIP_MAX_WIDTH_DP = 240
private const val ICON_BOX_DP = 34
// Full-width single-vehicle card — roomier glyph + padding + a trailing chevron. [HOME-CARDS-001]
private const val ICON_BOX_WIDE_DP = 44
private const val WIDE_H_PAD_DP = 14
private const val WIDE_V_PAD_DP = 12
private const val CHEVRON_DP = 22
private const val EYEBROW_NAME_GAP_DP = 2
private const val NAME_PARK_GAP_DP = 2
private const val EYEBROW_MARKER_GAP_DP = 4
private const val EYEBROW_DOT_DP = 5
private const val EYEBROW_BT_ICON_DP = 11
private const val EYEBROW_TEXT_SP = 9f
private const val EYEBROW_TRACKING_SP = 0.4f
private const val BORDER_DP = 1
private const val DRIVING_BORDER_DP = 1.5f // thicker live-blue border while driving [CHIP-DRIVING-001]
private const val OUTLINE_ALPHA = 0.4f

// Driving "radar" halo animation tuning. [CHIP-DRIVING-001]
private const val RADAR_PERIOD_MS = 1600
private const val RADAR_PHASE_OFFSET = 0.5f  // second ring half a cycle behind the first
private const val RADAR_MIN_FRACTION = 0.45f // rings start at 45% of the glyph radius, expand to full
private const val RADAR_MAX_ALPHA = 0.45f
private val RADAR_STROKE = 1.5.dp

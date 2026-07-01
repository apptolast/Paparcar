package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.presentation.home.VehicleCard
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.distanceString
import io.apptolast.paparcar.ui.components.VehicleBadgeTone
import io.apptolast.paparcar.ui.components.VehicleGlyph
import io.apptolast.paparcar.ui.components.vehicleBadgeAccent
import io.apptolast.paparcar.ui.theme.PapDriveBlue
import io.apptolast.paparcar.ui.theme.PapOutlineVariantLight
import io.apptolast.paparcar.ui.theme.PapShapes
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_peek_parked_label
import paparcar.composeapp.generated.resources.home_vehicle_card_status_empty
import paparcar.composeapp.generated.resources.home_vehicle_chip_badge_active
import paparcar.composeapp.generated.resources.home_vehicle_chip_status_candidate
import paparcar.composeapp.generated.resources.home_vehicle_chip_status_driving
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
    isDriving: Boolean = false,
    // Candidate phase: the trip stopped and the user is walking away — show a distinct
    // "parking…" label (green, hinting the upcoming parked state) instead of "Driving". [DET-PHASE-001]
    isCandidate: Boolean = false,
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
    // Driving is a LIVE state and supersedes the rest: vivid en-route blue border (matches the
    // driving puck on the map) so the chip in motion grabs the eye. [CHIP-DRIVING-001]
    val borderColor = when {
        isDriving -> PapDriveBlue
        isParked  -> parkedBorderColor
        else      -> cs.outline.copy(alpha = OUTLINE_ALPHA)
    }
    val muted = cs.onSurface.copy(alpha = MUTED_ALPHA)

    val vehicleName = vehicle.displayName(fallback = stringResource(Res.string.home_vehicle_fallback_name))
    val activeLabel = stringResource(Res.string.home_vehicle_chip_badge_active)

    Surface(
        onClick = onClick,
        modifier = modifier.width(CHIP_WIDTH_DP.dp),
        shape = PapShapes.cardSmall,
        border = BorderStroke(if (isDriving) DRIVING_BORDER_DP.dp else BORDER_DP.dp, borderColor),
        color = cs.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // While driving, a pulsing "radar" halo expands behind the car — the same en-route
                // blue as its map puck, so the eye is drawn to the vehicle in motion. [CHIP-DRIVING-001]
                Box(contentAlignment = Alignment.Center) {
                    if (isDriving) DrivingRadarHalo(diameter = ICON_BOX_DP.dp)
                    VehicleGlyph(
                        carbody = vehicle.carbodyType,
                        size = vehicle.sizeCategory,
                        glyphSize = ICON_BOX_DP.dp,
                        color = vehicle.color,
                    )
                }
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
                if (isDriving) {
                    // Live "driving" state supersedes the BT/Active pill: a pulsing dot + the
                    // en-route-blue label signal a trip in progress, not yet parked. [CHIP-DRIVING-001]
                    // In the candidate phase (stopped + walking away) the label flips to "Parking…" in
                    // the brand green, hinting the transition into the parked state. [DET-PHASE-001]
                    // Live-dot accent tracks the phase like the banner: en-route blue while driving,
                    // brand green once confirming a spot ("Parking…"). [DET-PHASE-001]
                    DrivingLiveDot(color = if (isCandidate) cs.primary else PapDriveBlue)
                    Text(
                        text = stringResource(
                            if (isCandidate) Res.string.home_vehicle_chip_status_candidate
                            else Res.string.home_vehicle_chip_status_driving
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        // Neutral label — the phase colour is carried by the dot/halo/border, not the text.
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
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

/** Small breathing dot prefixing the "Driving"/"Parking…" label — a calm "live" indicator, tinted
 *  with the phase [color] (blue driving, green candidate). [CHIP-DRIVING-001] [DET-PHASE-001] */
@Composable
private fun DrivingLiveDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "driving_dot")
    val alpha by transition.animateFloat(
        initialValue = LIVE_DOT_ALPHA_MIN,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(LIVE_DOT_PERIOD_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "driving_dot_alpha",
    )
    Box(
        Modifier
            .size(LIVE_DOT_SIZE)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
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
private const val DRIVING_BORDER_DP = 1.5f // thicker live-blue border while driving [CHIP-DRIVING-001]
private const val OUTLINE_ALPHA = 0.4f
private const val MUTED_ALPHA = 0.6f
private const val BT_LABEL = "BT"
private const val BULLET = "·"

// Driving "radar" halo + live dot animation tuning. [CHIP-DRIVING-001]
private const val RADAR_PERIOD_MS = 1600
private const val RADAR_PHASE_OFFSET = 0.5f  // second ring half a cycle behind the first
private const val RADAR_MIN_FRACTION = 0.45f // rings start at 45% of the glyph radius, expand to full
private const val RADAR_MAX_ALPHA = 0.45f
private val RADAR_STROKE = 1.5.dp
private const val LIVE_DOT_PERIOD_MS = 900
private const val LIVE_DOT_ALPHA_MIN = 0.35f
private val LIVE_DOT_SIZE = 6.dp

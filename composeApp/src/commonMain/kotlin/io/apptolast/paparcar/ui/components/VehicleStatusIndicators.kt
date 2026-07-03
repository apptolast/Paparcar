package io.apptolast.paparcar.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.TripOrigin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.VehicleMonitoringStatus
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.PapDriveBlue
import io.apptolast.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_vehicle_status_inactive
import paparcar.composeapp.generated.resources.vehicle_card_detection_bt
import paparcar.composeapp.generated.resources.vehicle_status_active

/**
 * Shared status language for the vehicle chip / card / ficha. The monitoring state is carried by an
 * ICON (or a marker + label) whose COLOUR is the whole message — green = actively detected, blue =
 * detected via Bluetooth (same priority as active, just a different method), grey = inactive. No
 * method text ("Geofence", BT device id) is ever shown; the colour + glyph already says it.
 * [HOME-VEH-REFINE-001]
 */

/** Accent colour for a monitoring status. Green (active) / blue (Bluetooth) / grey (inactive). */
@Composable
fun vehicleStatusAccent(status: VehicleMonitoringStatus): Color = when (status) {
    is VehicleMonitoringStatus.Bluetooth -> MaterialTheme.colorScheme.tertiary
    VehicleMonitoringStatus.Active       -> MaterialTheme.colorScheme.primary
    VehicleMonitoringStatus.Inactive     -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Localized pin label for a monitoring status ("Active" / "Bluetooth" / "Inactive"). */
@Composable
fun vehicleStatusPinLabel(status: VehicleMonitoringStatus): String = when (status) {
    is VehicleMonitoringStatus.Bluetooth -> stringResource(Res.string.vehicle_card_detection_bt)
    VehicleMonitoringStatus.Active       -> stringResource(Res.string.vehicle_status_active)
    VehicleMonitoringStatus.Inactive     -> stringResource(Res.string.home_vehicle_status_inactive)
}

/**
 * Card border colour encoding the vehicle's state — the design's muted "green-line"/"blue-line":
 * the status accent dimmed so it frames the card without going neon. Inactive stays neutral;
 * a live trip overrides with the en-route blue. Shared by the ficha, the Home chips and the
 * single-vehicle card so the border speaks the same language everywhere. [HOME-VEH-REFINE-001]
 */
@Composable
fun vehicleStatusBorderColor(
    status: VehicleMonitoringStatus,
    isDriving: Boolean = false,
    neutral: Color = MaterialTheme.colorScheme.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA),
): Color = when {
    isDriving -> PapDriveBlue
    status is VehicleMonitoringStatus.Bluetooth -> MaterialTheme.colorScheme.tertiary.copy(alpha = STATUS_BORDER_ALPHA)
    status is VehicleMonitoringStatus.Active -> MaterialTheme.colorScheme.primary.copy(alpha = STATUS_BORDER_ALPHA)
    else -> neutral
}

/**
 * The status glyph shown immediately before the vehicle name in the compact Home chip. Placed inline
 * (not as a corner badge, which collides with the illustrative car glyph): a filled target for Active
 * (evokes "being tracked"), the Bluetooth mark for BT, a hollow ring for Inactive. Colour = state.
 */
@Composable
fun VehicleStatusLeadingIcon(
    status: VehicleMonitoringStatus,
    modifier: Modifier = Modifier,
    tint: Color = vehicleStatusAccent(status),
) {
    val icon = when (status) {
        is VehicleMonitoringStatus.Bluetooth -> Icons.Rounded.Bluetooth
        VehicleMonitoringStatus.Active       -> Icons.Rounded.TripOrigin
        VehicleMonitoringStatus.Inactive     -> Icons.Rounded.RadioButtonUnchecked
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(STATUS_ICON_DP.dp),
    )
}

/**
 * THE single status badge for a vehicle card — a tonal pill (icon + short uppercase label) tinted by
 * the monitoring accent (green = active, blue = Bluetooth, grey = inactive). It is deliberately the
 * ONLY boxed element on the card row: the dynamic, decision-relevant state earns the container, while
 * static description (carbody · size) drops to quiet subtitle text beside it. Tonal fill (accent at
 * low alpha), never the neon accent — same muted language as the card border. [CARD-ONE-BADGE-001]
 */
@Composable
fun VehicleStatusBadge(
    status: VehicleMonitoringStatus,
    label: String,
    modifier: Modifier = Modifier,
) {
    val accent = vehicleStatusAccent(status)
    val icon = when (status) {
        is VehicleMonitoringStatus.Bluetooth -> Icons.Rounded.Bluetooth
        VehicleMonitoringStatus.Active       -> Icons.Rounded.TripOrigin
        VehicleMonitoringStatus.Inactive     -> Icons.Rounded.RadioButtonUnchecked
    }
    PapBadge(
        label = label.uppercase(),
        containerColor = accent.copy(alpha = STATUS_BADGE_BG_ALPHA),
        contentColor = accent,
        modifier = modifier,
        icon = icon,
        // Repeating data token that competes horizontally with the name → DATA role (Barlow).
        textStyle = PaparcarType.current.badge,
    )
}

/**
 * "Not marked" glyph — a dashed hollow ring with a centred "+", signalling the vehicle has no active
 * parking session yet (tapping it enters mark-parking). Drawn in Compose because a VectorDrawable
 * can't express a dashed stroke. [HOME-VEH-REFINE-001]
 */
@Composable
fun UnmarkedParkingIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = modifier.size(UNMARKED_ICON_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(UNMARKED_ICON_DP.dp)) {
            val stroke = UNMARKED_STROKE_DP.toPx()
            val dash = PathEffect.dashPathEffect(
                floatArrayOf(UNMARKED_DASH_ON.toPx(), UNMARKED_DASH_OFF.toPx()),
                0f,
            )
            drawCircle(
                color = tint,
                radius = (size.minDimension - stroke) / 2f,
                style = Stroke(width = stroke, pathEffect = dash),
            )
        }
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(UNMARKED_PLUS_DP.dp),
        )
    }
}

/**
 * Pulsing "radar" halo behind a car glyph while a trip is being detected — two en-route-blue rings
 * expanding outward and fading, half a period out of phase. Contained within [diameter] so it never
 * shifts the layout; reads as "this car is live / in motion". [CHIP-DRIVING-001]
 */
@Composable
fun DrivingRadarHalo(diameter: androidx.compose.ui.unit.Dp) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "driving_radar")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                RADAR_PERIOD_MS,
                easing = androidx.compose.animation.core.LinearEasing,
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
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

private const val STATUS_ICON_DP = 16
private const val STATUS_BORDER_ALPHA = 0.45f // muted "green-line" frame, never the neon accent
private const val STATUS_BADGE_BG_ALPHA = 0.14f // tonal fill for the single status badge, not neon

// Driving "radar" halo animation tuning. [CHIP-DRIVING-001]
private const val RADAR_PERIOD_MS = 1600
private const val RADAR_PHASE_OFFSET = 0.5f  // second ring half a cycle behind the first
private const val RADAR_MIN_FRACTION = 0.45f // rings start at 45% of the glyph radius, expand to full
private const val RADAR_MAX_ALPHA = 0.45f
private val RADAR_STROKE = 1.5.dp
private const val UNMARKED_ICON_DP = 20
private val UNMARKED_STROKE_DP = 1.5.dp
private val UNMARKED_DASH_ON = 2.dp
private val UNMARKED_DASH_OFF = 2.5.dp
private const val UNMARKED_PLUS_DP = 12

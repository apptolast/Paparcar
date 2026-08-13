package io.apptolast.paparcar.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Radar
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
import io.apptolast.paparcar.ui.theme.VehicleWatch
import io.apptolast.paparcar.ui.theme.vehicleIdentityColor
import io.apptolast.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_vehicle_status_inactive
import paparcar.composeapp.generated.resources.vehicle_card_detection_bt
import paparcar.composeapp.generated.resources.vehicle_status_active

/**
 * Shared CHASSIS language for the vehicle chip / card / ficha: how this car is watched, drawn as a
 * glyph before the name plus the card border, both in the vehicle's identity colour
 * ([vehicleIdentityColor]: green = active detection, blue = Bluetooth, grey = unwatched).
 *
 * The colour is deliberately the METHOD, never the state — what a car is *doing* is plain
 * `onSurface` text (plus animation while driving). No method text ("Geofence", BT device id) is
 * ever shown; the glyph already says which tier. [HOME-VEH-REFINE-001] [UI-COLOR-DOCTRINE-001]
 */

/** Localized pin label for a watch tier ("Active" / "Bluetooth" / "Inactive"). */
@Composable
fun vehicleWatchPinLabel(watch: VehicleWatch): String = when (watch) {
    VehicleWatch.Bluetooth -> stringResource(Res.string.vehicle_card_detection_bt)
    VehicleWatch.Assisted  -> stringResource(Res.string.vehicle_status_active)
    VehicleWatch.Off       -> stringResource(Res.string.home_vehicle_status_inactive)
}

/**
 * The watch glyph shown immediately before the vehicle name. Placed inline (not as a corner badge,
 * which collides with the illustrative car glyph): the Bluetooth mark for the deterministic tier,
 * a radar (geofence sweep) for the assisted tier, a hollow ring for unwatched.
 */
@Composable
fun VehicleWatchLeadingIcon(
    watch: VehicleWatch,
    modifier: Modifier = Modifier,
    tint: Color = vehicleIdentityColor(watch),
) {
    val icon = when (watch) {
        VehicleWatch.Bluetooth -> Icons.Rounded.Bluetooth
        VehicleWatch.Assisted  -> Icons.Rounded.Radar
        VehicleWatch.Off       -> Icons.Rounded.RadioButtonUnchecked
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(STATUS_ICON_DP.dp),
    )
}

/**
 * THE single watch badge for a vehicle card — a tonal pill (icon + short uppercase label) tinted by
 * the vehicle's identity colour (method). It is deliberately the ONLY boxed element on the card row:
 * the decision-relevant fact earns the container, while static description (carbody · size) drops to
 * quiet subtitle text beside it. Tonal fill (identity colour at low alpha), never the full accent —
 * same muted language as the card border. [CARD-ONE-BADGE-001]
 */
@Composable
fun VehicleWatchBadge(
    watch: VehicleWatch,
    label: String,
    modifier: Modifier = Modifier,
) {
    val accent = vehicleIdentityColor(watch)
    val icon = when (watch) {
        VehicleWatch.Bluetooth -> Icons.Rounded.Bluetooth
        VehicleWatch.Assisted  -> Icons.Rounded.Radar
        VehicleWatch.Off       -> Icons.Rounded.RadioButtonUnchecked
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
 * Pulsing "radar" halo behind a car glyph while a trip is being detected — two rings in the
 * vehicle's identity colour expanding outward and fading, half a period out of phase. Contained
 * within [diameter] so it never shifts the layout; motion (not a different hue) is what says
 * "this car is live / in motion". [CHIP-DRIVING-001] [UI-COLOR-DOCTRINE-001]
 */
@Composable
fun DrivingRadarHalo(diameter: androidx.compose.ui.unit.Dp, color: Color) {
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
                color = color.copy(alpha = (1f - p) * RADAR_MAX_ALPHA),
                radius = maxR * (RADAR_MIN_FRACTION + p * (1f - RADAR_MIN_FRACTION)),
                style = Stroke(width = stroke),
            )
        }
    }
}

/**
 * Breathing alpha for the *state* words while a trip is in motion ("En ruta" / "Aparcando…") —
 * the state machine never wears colour ([UI-COLOR-DOCTRINE-001]), so its liveness is told by this
 * slow pulse instead. Apply to the state `Text`'s colour: `onSurface.copy(alpha = pulse)`.
 */
@Composable
fun rememberDrivingStatePulse(): Float {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "state_pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = STATE_PULSE_MIN_ALPHA,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                STATE_PULSE_PERIOD_MS,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "state_pulse_alpha",
    )
    return alpha
}

private const val STATUS_ICON_DP = 16
private const val STATUS_BADGE_BG_ALPHA = 0.14f // tonal fill for the single watch badge, not neon

// Breathing pulse for the driving-state words. [UI-COLOR-DOCTRINE-001]
private const val STATE_PULSE_PERIOD_MS = 900
private const val STATE_PULSE_MIN_ALPHA = 0.4f

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

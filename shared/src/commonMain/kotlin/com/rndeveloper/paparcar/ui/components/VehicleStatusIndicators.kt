package com.rndeveloper.paparcar.ui.components

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import com.rndeveloper.paparcar.ui.theme.PaparcarType
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
 * Route glyph for the **state line of a vehicle chip while a trip is running** — a short S-shaped
 * route that draws itself from origin to destination, a dot riding its head, over a faint ghost of
 * the full path so the shape still reads on frame 0 (and in static previews). When the head lands,
 * it restarts: the drawing IS the message, "something is running right now".
 *
 * It replaces the location pin in that slot: a pin is a *place*, and a trip in motion has no place
 * yet. Drawn in Compose because neither Material nor a VectorDrawable can trim a stroke over time —
 * same reason [UnmarkedParkingIcon] is a Canvas. Carries the vehicle's identity colour, exactly like
 * the pin it stands in for; the state *words* beside it stay `onSurface` with their pulse.
 * [UI-CHIP-ROUTE-GLYPH-001] [UI-COLOR-DOCTRINE-001]
 */
@Composable
fun DrivingRouteGlyph(
    color: Color,
    modifier: Modifier = Modifier,
    glyphSize: androidx.compose.ui.unit.Dp = ROUTE_GLYPH_DP.dp,
) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "driving_route")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                ROUTE_DRAW_PERIOD_MS,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
        ),
        label = "driving_route_progress",
    )
    // Path objects are reused across frames — a glyph this small redrawing at 60 fps has no business
    // allocating.
    val routePath = remember { androidx.compose.ui.graphics.Path() }
    val drawnPath = remember { androidx.compose.ui.graphics.Path() }
    val measure = remember { androidx.compose.ui.graphics.PathMeasure() }

    Canvas(modifier.size(glyphSize)) {
        val w = size.width
        val h = size.height
        routePath.rewind()
        // An S from bottom-left to top-right: two bends read as "a route", one reads as a swoosh.
        routePath.moveTo(w * ROUTE_START_X, h * ROUTE_START_Y)
        routePath.cubicTo(
            w * ROUTE_START_X, h * ROUTE_C1_Y,
            w * ROUTE_END_X, h * ROUTE_C2_Y,
            w * ROUTE_END_X, h * ROUTE_END_Y,
        )
        measure.setPath(routePath, forceClosed = false)
        val stroke = Stroke(width = ROUTE_STROKE.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)

        // Ghost of the whole route — keeps the glyph legible before the head gets there.
        drawPath(routePath, color = color.copy(alpha = ROUTE_GHOST_ALPHA), style = stroke)

        val travelled = measure.length * progress
        drawnPath.rewind()
        if (measure.getSegment(0f, travelled, drawnPath, startWithMoveTo = true)) {
            drawPath(drawnPath, color = color, style = stroke)
        }
        // The head: what the eye actually tracks.
        drawCircle(
            color = color,
            radius = ROUTE_HEAD_RADIUS.toPx(),
            center = measure.getPosition(travelled),
        )
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
// Self-drawing route glyph for the "en route" state line. [UI-CHIP-ROUTE-GLYPH-001]
private const val ROUTE_GLYPH_DP = 16
private const val ROUTE_DRAW_PERIOD_MS = 1600
// 0.22 vanished outright on surfaceContainerHigh in dark: the un-drawn path has to survive the
// container it sits on, or the glyph blinks empty once per cycle. Measured on device. [UI-CHIP-ROUTE-GLYPH-001]
private const val ROUTE_GHOST_ALPHA = 0.35f
// 1.6 read lighter than the solid location pin of the chip beside it — same slot, same weight.
private val ROUTE_STROKE = 1.8.dp
private val ROUTE_HEAD_RADIUS = 1.9.dp
// S-curve control points, as fractions of the glyph box: origin bottom-left → destination top-right.
private const val ROUTE_START_X = 0.20f
private const val ROUTE_START_Y = 0.84f
private const val ROUTE_C1_Y = 0.42f
private const val ROUTE_C2_Y = 0.58f
private const val ROUTE_END_X = 0.80f
private const val ROUTE_END_Y = 0.16f

private const val UNMARKED_ICON_DP = 20
private val UNMARKED_STROKE_DP = 1.5.dp
private val UNMARKED_DASH_ON = 2.dp
private val UNMARKED_DASH_OFF = 2.5.dp
private const val UNMARKED_PLUS_DP = 12

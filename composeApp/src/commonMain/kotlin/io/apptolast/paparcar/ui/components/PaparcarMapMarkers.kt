package io.apptolast.paparcar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.presentation.util.SpotReliabilityLevel
import io.apptolast.paparcar.ui.theme.rememberOutfitFontFamily
import kotlinx.coroutines.launch

/**
 * Paparcar map markers — three-marker family sharing a coherent circle visual language.
 *
 * | Marker             | Shape  | Colour          | Content            |
 * |--------------------|--------|-----------------|--------------------|
 * | Parked vehicle     | Circle | Amber           | DirectionsCar icon |
 * | Free spot          | Circle | Green           | LocalParking icon  |
 * | Zone (saved place) | Circle | surfaceContainer| Zone preset icon   |
 *
 * Rendered as bitmaps by the kmpmaps library via `customMarkerContent` in [PaparcarMapView].
 * Marker anchor = (0.5f, 1f): the bottom-centre of each bitmap pins the geographic coordinate.
 */

// ─── Shared palette ──────────────────────────────────────────────────────────

private object MarkerColors {
    // Free spot — classic parking green
    val SpotGreen     = Color(0xFF22C55E)
    val SpotOnGreen   = Color(0xFF052E16)

    // Reliability palette
    val SpotHigh      = Color(0xFF22C55E) // Green
    val SpotMedium    = Color(0xFFF59E0B) // Amber
    val SpotLow       = Color(0xFFEF4444) // Red
    val SpotManual    = Color(0xFF3B82F6) // Blue
    val SpotOnDark    = Color(0xFFFFFFFF)
    val SpotOnLight   = Color(0xFF052E16)

    // Parked vehicle — amber/orange ("mine, active")
    val PlateAmber    = Color(0xFFF59E0B)
    val PlateAmberDk  = Color(0xFFD97706)
    val PlateOnAmber  = Color(0xFF451A03)

    // MyVehicle fallback teardrop (ParkingLocationScreen) — kept separate
    val LegacyGreen   = Color(0xFF25F48C)
    val LegacyForest  = Color(0xFF0D1C14)

    // Selection halo — two-pass so it reads on any map tile background
    val SelectionRing   = Color(0xFFE8F5EC)
    val SelectionShadow = Color(0xFF000000)
}

private const val HALO_OUTER_EXPAND = 6f
private const val HALO_INNER_EXPAND = 4f
private const val HALO_OUTER_STROKE = 4.5f
private const val HALO_INNER_STROKE = 2.5f
private const val HALO_OUTER_ALPHA  = 0.55f
private const val HALO_INNER_ALPHA  = 0.95f

private const val GROUND_SHADOW_ALPHA = 0.35f

// ─── Marker 1 — Parked vehicle (VehicleBadgeMarker) ─────────────────────────

/**
 * Amber circle badge for the user's parked vehicle.
 *
 * Same circle pattern as [FreeSpotMarker] and [ZoneMarker] — one coherent family
 * on the map. Amber + car icon reads immediately as "mine, active". Slightly
 * larger than [FreeSpotMarker] (46 dp vs 42 dp) so the user's car dominates.
 *
 * @param selected when true the border turns white to indicate selection.
 */
@Composable
fun VehicleBadgeMarker(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val shadowColor = ink.copy(alpha = GROUND_SHADOW_ALPHA)

    Box(
        modifier = modifier.size(
            width  = BADGE_DIAM,
            height = BADGE_DIAM + BADGE_GROUND_GAP,
        ),
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = BADGE_SHADOW_W, height = BADGE_SHADOW_H),
        ) { drawOval(color = shadowColor) }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(BADGE_DIAM)
                .background(color = MarkerColors.PlateAmber, shape = CircleShape)
                .border(
                    width = if (selected) BADGE_SEL_STROKE else BADGE_STROKE,
                    color = if (selected) Color.White else MarkerColors.PlateAmberDk,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = MarkerColors.PlateOnAmber,
                modifier = Modifier.size(BADGE_ICON_SIZE),
            )
        }
    }
}

private val BADGE_DIAM       = 46.dp
private val BADGE_ICON_SIZE  = 26.dp
private val BADGE_STROKE     = 2.dp
private val BADGE_SEL_STROKE = 3.dp
private val BADGE_SHADOW_W   = 22.dp
private val BADGE_SHADOW_H   = 5.dp
private val BADGE_GROUND_GAP = 4.dp

// ─── MyVehicle marker — legacy fallback (ParkingLocationScreen) ──────────────

/**
 * Fallback teardrop marker used by screens that have a parking location but no
 * [io.apptolast.paparcar.domain.model.ParkedVehicleView] context (e.g.
 * ParkingLocationScreen). Home screen uses [VehicleBadgeMarker] instead.
 */
@Composable
fun MyVehicleMarker(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val shadowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = GROUND_SHADOW_ALPHA)
    Canvas(modifier = modifier.size(MY_VEHICLE_W, MY_VEHICLE_H)) {
        drawMyVehicle(selected = selected, shadowColor = shadowColor)
    }
}

private fun DrawScope.drawMyVehicle(selected: Boolean, shadowColor: Color) {
    val w = size.width
    val pad = MY_VEHICLE_VIEWPORT_PAD
    val scale = w / (64f + pad * 2f)
    val padPx = pad * scale

    translate(left = padPx, top = padPx) {
        if (selected) {
            val haloOuter = teardropPath(cx = 32f, w = 64f, h = 80f, expand = HALO_OUTER_EXPAND, scale = scale)
            drawPath(
                haloOuter,
                color = MarkerColors.SelectionShadow.copy(alpha = HALO_OUTER_ALPHA),
                style = Stroke(width = HALO_OUTER_STROKE * scale),
            )
            val haloInner = teardropPath(cx = 32f, w = 64f, h = 80f, expand = HALO_INNER_EXPAND, scale = scale)
            drawPath(
                haloInner,
                color = MarkerColors.SelectionRing.copy(alpha = HALO_INNER_ALPHA),
                style = Stroke(width = HALO_INNER_STROKE * scale),
            )
        }

        drawOval(
            color = shadowColor,
            topLeft = Offset(20f * scale, 73.5f * scale),
            size = Size(24f * scale, 5f * scale),
        )

        val pin = teardropPath(cx = 32f, w = 64f, h = 80f, expand = 0f, scale = scale)
        drawPath(pin, color = MarkerColors.LegacyGreen)
        drawPath(pin, color = MarkerColors.LegacyForest.copy(alpha = 0.35f), style = Stroke(width = 2f * scale))

        drawCircle(
            color = MarkerColors.LegacyForest,
            radius = 20f * scale,
            center = Offset(32f * scale, 32f * scale),
        )

        translate(left = 20f * scale, top = 20f * scale) {
            drawCarIcon(scale = scale, color = MarkerColors.LegacyGreen)
        }
    }
}

private const val MY_VEHICLE_VIEWPORT_PAD = 7f
private val MY_VEHICLE_W = 46.dp
private val MY_VEHICLE_H = 55.dp

// ─── Marker 2 — Free spot (FreeSpotMarker) ───────────────────────────────────

/**
 * Free-spot marker. Circle with a parking "P" icon — same circle pattern
 * as [ZoneMarker] and [VehicleBadgeMarker] for a coherent three-marker family.
 * Color signals reliability level.
 *
 * @param selected when true the border turns white to indicate selection.
 * @param reliability reliability tier determining the marker color.
 */
@Composable
fun FreeSpotMarker(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    reliability: SpotReliabilityLevel = SpotReliabilityLevel.HIGH,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val shadowColor = ink.copy(alpha = GROUND_SHADOW_ALPHA)

    val (fill, onFill) = when (reliability) {
        SpotReliabilityLevel.HIGH -> MarkerColors.SpotHigh to MarkerColors.SpotOnLight
        SpotReliabilityLevel.MEDIUM -> MarkerColors.SpotMedium to MarkerColors.SpotOnLight
        SpotReliabilityLevel.LOW -> MarkerColors.SpotLow to MarkerColors.SpotOnDark
        SpotReliabilityLevel.MANUAL -> MarkerColors.SpotManual to MarkerColors.SpotOnDark
    }

    Box(
        modifier = modifier.size(
            width  = FREE_SPOT_MARKER_DIAM,
            height = FREE_SPOT_MARKER_DIAM + FREE_SPOT_MARKER_GROUND_GAP,
        ),
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = FREE_SPOT_MARKER_SHADOW_W, height = FREE_SPOT_MARKER_SHADOW_H),
        ) { drawOval(color = shadowColor) }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(FREE_SPOT_MARKER_DIAM)
                .background(color = fill, shape = CircleShape)
                .border(
                    width = if (selected) FREE_SPOT_SEL_STROKE else FREE_SPOT_MARKER_STROKE,
                    color = if (selected) Color.White else onFill.copy(alpha = 0.3f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalParking,
                contentDescription = null,
                tint = onFill,
                modifier = Modifier.size(FREE_SPOT_MARKER_ICON),
            )
        }
    }
}

private val FREE_SPOT_MARKER_DIAM       = 42.dp
private val FREE_SPOT_MARKER_ICON       = 24.dp
private val FREE_SPOT_MARKER_STROKE     = 2.dp
private val FREE_SPOT_SEL_STROKE        = 3.dp
private val FREE_SPOT_MARKER_SHADOW_W   = 20.dp
private val FREE_SPOT_MARKER_SHADOW_H   = 5.dp
private val FREE_SPOT_MARKER_GROUND_GAP = 4.dp

// ─── Marker 3 — Zone (ZoneMarker) ────────────────────────────────────────────

/**
 * On-map marker for a saved [io.apptolast.paparcar.domain.model.Zone]. Same
 * visual language as [ZoneCenterPin] (circle + chosen icon) so the user
 * recognises the placed marker as the locked-in counterpart of the pin they
 * dragged in AddingZone mode. Lighter weight (smaller diameter, no bounce,
 * static shadow) so multiple zones on the map don't compete with spot markers.
 *
 * @param icon resolved [ImageVector] for the zone's `iconKey`.
 */
@Composable
fun ZoneMarker(
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val fill = MaterialTheme.colorScheme.surfaceContainer
    val accent = MaterialTheme.colorScheme.primary
    val shadowColor = ink.copy(alpha = GROUND_SHADOW_ALPHA)

    Box(
        modifier = modifier.size(
            width  = ZONE_MARKER_DIAM,
            height = ZONE_MARKER_DIAM + ZONE_MARKER_GROUND_GAP,
        ),
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = ZONE_MARKER_SHADOW_W, height = ZONE_MARKER_SHADOW_H),
        ) { drawOval(color = shadowColor) }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(ZONE_MARKER_DIAM)
                .background(color = fill, shape = CircleShape)
                .border(width = ZONE_MARKER_STROKE, color = ink, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(ZONE_MARKER_ICON),
            )
        }
    }
}

private val ZONE_MARKER_DIAM       = 42.dp
private val ZONE_MARKER_ICON       = 22.dp
private val ZONE_MARKER_STROKE     = 2.dp
private val ZONE_MARKER_SHADOW_W   = 20.dp
private val ZONE_MARKER_SHADOW_H   = 5.dp
private val ZONE_MARKER_GROUND_GAP = 4.dp

// ─── Centre-pin family ───────────────────────────────────────────────────────

@Composable
private fun TeardropPinScaffold(
    cameraMoving: Boolean,
    modifier: Modifier = Modifier,
    pinDraw: DrawScope.(scale: Float, ink: Color) -> Unit = { _, _ -> },
    discOverlay: @Composable BoxScope.() -> Unit = {},
) {
    val offsetY = remember { Animatable(0f) }
    val pinScale = remember { Animatable(1f) }
    LaunchedEffect(cameraMoving) {
        val (target, scaleTarget) = if (cameraMoving) {
            TEARDROP_PIN_LIFT_DP to TEARDROP_PIN_LIFT_SCALE
        } else {
            TEARDROP_PIN_REST_DP to TEARDROP_PIN_REST_SCALE
        }
        launch {
            offsetY.animateTo(target, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        launch {
            pinScale.animateTo(scaleTarget, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    val ink = MaterialTheme.colorScheme.onSurface
    val shadowColor = ink.copy(alpha = TEARDROP_SHADOW_ALPHA)

    Box(
        modifier = modifier.size(
            width = TEARDROP_PIN_W,
            height = TEARDROP_PIN_H * 2,
        ),
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = TEARDROP_SHADOW_W, height = TEARDROP_SHADOW_H),
        ) {
            drawOval(color = shadowColor)
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = offsetY.value.dp)
                .scale(pinScale.value)
                .size(width = TEARDROP_PIN_W, height = TEARDROP_PIN_H),
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val scale = size.width / 68f
                val pin = teardropPath(
                    cx = 34f, w = 68f, h = 84f, expand = 0f, scale = scale,
                    top = 4f, bottom = 78f,
                )
                drawPath(pin, color = ink, style = Stroke(width = TEARDROP_STROKE_WIDTH * scale))
                pinDraw(scale, ink)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = TEARDROP_DISC_TOP_PADDING)
                    .size(TEARDROP_DISC_DIAM),
                contentAlignment = Alignment.Center,
                content = discOverlay,
            )
        }
    }
}

@Composable
fun ReportCenterPin(
    cameraMoving: Boolean,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val outfit = rememberOutfitFontFamily()
    val measurer = rememberTextMeasurer()
    val pStyle = remember(outfit, ink) {
        TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
            color = ink,
        )
    }
    TeardropPinScaffold(
        cameraMoving = cameraMoving,
        modifier = modifier,
        pinDraw = { scale, _ ->
            val result = measurer.measure(text = AnnotatedString("P"), style = pStyle)
            val tx = 34f * scale - result.size.width / 2f
            val ty = 32f * scale - result.size.height / 2f
            translate(left = tx, top = ty) {
                drawText(result, color = ink)
            }
        },
    )
}

@Composable
fun ParkingCenterPin(
    cameraMoving: Boolean,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val carBody = MaterialTheme.colorScheme.surface
    TeardropPinScaffold(
        cameraMoving = cameraMoving,
        modifier = modifier,
        pinDraw = { scale, _ ->
            drawCircle(
                color = ink,
                radius = TEARDROP_INNER_DISC_RADIUS * scale,
                center = Offset(34f * scale, 32f * scale),
            )
            translate(left = 22f * scale, top = 20f * scale) {
                drawCarIcon(scale = scale, color = carBody, windshieldColor = ink)
            }
        },
    )
}

@Composable
fun ZoneCenterPin(
    icon: ImageVector,
    cameraMoving: Boolean,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val iconOnInk = MaterialTheme.colorScheme.surface
    TeardropPinScaffold(
        cameraMoving = cameraMoving,
        modifier = modifier,
        pinDraw = { scale, _ ->
            drawCircle(
                color = ink,
                radius = TEARDROP_INNER_DISC_RADIUS * scale,
                center = Offset(34f * scale, 32f * scale),
            )
        },
        discOverlay = {
            Box(
                modifier = Modifier
                    .size(TEARDROP_ICON_HALO_DIAM)
                    .background(iconOnInk.copy(alpha = ZONE_ICON_HALO_ALPHA), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(TEARDROP_ICON_SIZE),
                )
            }
        },
    )
}

private val TEARDROP_PIN_W             = 46.dp
private val TEARDROP_PIN_H             = 57.dp
private val TEARDROP_SHADOW_W          = 22.dp
private val TEARDROP_SHADOW_H          = 5.dp
private val TEARDROP_DISC_DIAM         = 28.dp
private val TEARDROP_DISC_TOP_PADDING  = 8.dp
private val TEARDROP_ICON_HALO_DIAM    = 26.dp
private val TEARDROP_ICON_SIZE         = 18.dp
private const val TEARDROP_INNER_DISC_RADIUS = 16f
private const val TEARDROP_PIN_REST_DP   = 0f
private const val TEARDROP_PIN_LIFT_DP   = -10f
private const val TEARDROP_PIN_REST_SCALE  = 1.0f
private const val TEARDROP_PIN_LIFT_SCALE  = 1.04f
private const val TEARDROP_STROKE_WIDTH    = 3.5f
private const val TEARDROP_SHADOW_ALPHA    = 0.32f
private const val ZONE_ICON_HALO_ALPHA     = 0.95f

// ─── Cluster marker ───────────────────────────────────────────────────────────

@Composable
fun FreeSpotClusterMarker(count: Int, modifier: Modifier = Modifier) {
    val outfit = rememberOutfitFontFamily()
    val measurer = rememberTextMeasurer()
    val style = remember(outfit) {
        TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
        )
    }
    Canvas(modifier.size(CLUSTER_SIZE)) {
        val s = size.minDimension / 68f
        drawCircle(MarkerColors.SpotGreen, radius = 30f * s, center = Offset(34f * s, 34f * s))
        drawCircle(
            MarkerColors.SpotOnGreen,
            radius = 30f * s,
            center = Offset(34f * s, 34f * s),
            style = Stroke(width = 3f * s),
        )
        val result = measurer.measure(text = AnnotatedString(count.toString()), style = style)
        translate(34f * s - result.size.width / 2f, 34f * s - result.size.height / 2f) {
            drawText(result, color = MarkerColors.SpotOnGreen)
        }
    }
}

private val CLUSTER_SIZE = 39.dp

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun teardropPath(
    cx: Float,
    w: Float,
    h: Float,
    expand: Float,
    scale: Float,
    top: Float = 2f,
    bottom: Float = 76f,
): Path {
    val s = scale
    val e = expand
    val mid = (top + bottom) / 2f + 5f
    return Path().apply {
        val (t, b) = (top - e) to (bottom + e)
        val r2 = w / 2f + e
        moveTo(cx * s, t * s)
        cubicTo(
            (cx + 17.67f) * s, t * s,
            (cx + r2) * s, (t + 14.33f) * s,
            (cx + r2) * s, mid * s,
        )
        cubicTo(
            (cx + r2) * s, (b - 26f) * s,
            cx * s, b * s,
            cx * s, b * s,
        )
        cubicTo(
            cx * s, b * s,
            (cx - r2) * s, (b - 26f) * s,
            (cx - r2) * s, mid * s,
        )
        cubicTo(
            (cx - r2) * s, (t + 14.33f) * s,
            (cx - 17.67f) * s, t * s,
            cx * s, t * s,
        )
        close()
    }
}

private fun DrawScope.drawCarIcon(
    scale: Float,
    color: Color,
    windshieldColor: Color = MarkerColors.LegacyForest,
) {
    val s = scale
    val car = Path().apply {
        moveTo(18.92f * s, 6.01f * s)
        cubicTo(18.72f * s, 5.42f * s, 18.16f * s, 5f * s, 17.5f * s, 5f * s)
        relativeLineTo(-11f * s, 0f)
        relativeCubicTo(-0.66f * s, 0f, -1.21f * s, 0.42f * s, -1.42f * s, 1.01f * s)
        lineTo(3f * s, 12f * s)
        relativeLineTo(0f, 8f * s)
        relativeCubicTo(0f, 0.55f * s, 0.45f * s, 1f * s, 1f * s, 1f * s)
        relativeLineTo(1f * s, 0f)
        relativeCubicTo(0.55f * s, 0f, 1f * s, -0.45f * s, 1f * s, -1f * s)
        relativeLineTo(0f, -1f * s)
        relativeLineTo(12f * s, 0f)
        relativeLineTo(0f, 1f * s)
        relativeCubicTo(0f, 0.55f * s, 0.45f * s, 1f * s, 1f * s, 1f * s)
        relativeLineTo(1f * s, 0f)
        relativeCubicTo(0.55f * s, 0f, 1f * s, -0.45f * s, 1f * s, -1f * s)
        relativeLineTo(0f, -8f * s)
        relativeLineTo(-2.08f * s, -5.99f * s)
        close()
    }
    drawPath(car, color)
    drawCircle(color, radius = 1.5f * s, center = Offset(6.5f * s, 14.5f * s))
    drawCircle(color, radius = 1.5f * s, center = Offset(17.5f * s, 14.5f * s))
    val ws = Path().apply {
        moveTo(5f * s, 11f * s)
        lineTo(6.5f * s, 6.5f * s)
        lineTo(17.5f * s, 6.5f * s)
        lineTo(19f * s, 11f * s)
        close()
    }
    drawPath(ws, color = windshieldColor)
}

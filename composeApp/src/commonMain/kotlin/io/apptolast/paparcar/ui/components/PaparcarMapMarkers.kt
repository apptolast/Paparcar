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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
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
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.presentation.util.SpotReliabilityUiState
import io.apptolast.paparcar.ui.icons.PaparcarIcons
import io.apptolast.paparcar.ui.icons.icon
import io.apptolast.paparcar.ui.theme.rememberOutfitFontFamily
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * Paparcar map markers — three-marker family with distinct shapes per role.
 *
 * | Marker             | Shape             | Colour | Content              |
 * |--------------------|-------------------|--------|----------------------|
 * | Parked vehicle     | Amber rect + tip  | Amber  | License plate text   |
 * | Free spot          | Circle            | Green  | "P" parking icon     |
 * | Zone (saved place) | Hexagon           | Blue   | 3-char zone code     |
 *
 * Rendered as bitmaps by the kmpmaps library via `customMarkerContent` in [PaparcarMapView].
 * Marker anchor = (0.5f, 1f): the bottom-centre of each bitmap pins the geographic coordinate.
 */

// ─── Shared palette ──────────────────────────────────────────────────────────

private object MarkerColors {
    // Free spot — one tone per SpotReliabilityUiState tier so the on-map marker
    // matches the peek modal badge (HIGH=green / MEDIUM=amber / LOW=red / MANUAL=blue).
    val SpotGreen     = Color(0xFF22C55E)
    val SpotOnGreen   = Color(0xFF052E16)
    val SpotAmber     = Color(0xFFF59E0B)
    val SpotOnAmber   = Color(0xFF402100)
    val SpotRed       = Color(0xFFEF4444)
    val SpotOnRed     = Color(0xFF450505)
    val SpotBlue      = Color(0xFF3B82F6)
    val SpotOnBlue    = Color(0xFF0B1E3F)

    // License-plate marker — amber rectangle
    val PlateAmber    = Color(0xFFF59E0B)
    val PlateAmberDk  = Color(0xFFD97706)
    val PlateOnAmber  = Color(0xFF1C0900)

    // Zone hexagon — blue
    val ZoneBlue      = Color(0xFF3B82F6)
    val ZoneOnBlue    = Color(0xFFFFFFFF)

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
 * Logo-style badge for the user's parked vehicle: dark PapInk interior + accent
 * ring + accent-tinted carbody icon. Mirrors the Paparcar app-icon language so
 * the user instantly reads "mine, parked" without confusing it with the green
 * free-spot circles around it.
 *
 * Per-vehicle accent comes from [parkedVehicleAccent] keyed by [stableRank]
 * (from `ParkedVehicleSummary.stableRank`). Multi-vehicle setups get distinct
 * colours; single-vehicle / legacy callers fall back to amber.
 *
 * Same 46dp footprint as [FreeSpotMarker]/[ZoneMarker] — one coherent family.
 *
 * @param selected when true the border switches to white to signal selection.
 * @param isActive when false the badge is greyed out (monitoring stopped).
 * @param stableRank index into [io.apptolast.paparcar.ui.theme.VehicleAccentPalette];
 *   null → amber fallback.
 */
@Composable
fun VehicleBadgeMarker(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    sizeCategory: VehicleSize? = null,
    carbodyType: io.apptolast.paparcar.domain.model.CarbodyType? = null,
    isActive: Boolean = true,
    stableRank: Int? = null,
    isBluetoothPaired: Boolean = false,
) {
    // A vehicle marker is always a parked car → green, unless BT (blue) or monitoring stopped (dim).
    // Same semantic [VehicleBadge] as the Home chip / peek so the car reads identically everywhere.
    val tone = when {
        !isActive -> VehicleBadgeTone.Inactive
        isBluetoothPaired -> VehicleBadgeTone.Bluetooth
        else -> VehicleBadgeTone.Parked
    }
    val shadowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = GROUND_SHADOW_ALPHA)

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

        VehicleBadge(
            carbody = carbodyType,
            size = sizeCategory,
            tone = tone,
            diameter = BADGE_DIAM,
            selected = selected,
            ringWidth = if (selected) BADGE_SEL_STROKE else BADGE_STROKE,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

private val BADGE_DIAM       = 46.dp
private val BADGE_STROKE     = 2.dp
private val BADGE_SEL_STROKE = 3.dp
private val BADGE_SHADOW_W   = 22.dp
private val BADGE_SHADOW_H   = 5.dp
private val BADGE_GROUND_GAP = 4.dp

// ─── Marker 1b — License plate marker (LicensePlateMarker) ───────────────────

/**
 * Amber rectangular badge with a downward-pointing tip — used as the parked-vehicle
 * marker on the Home map so users can identify their car by plate number.
 *
 * Viewport 80×50: rect body (4,4)-(76,32) + triangle (34,32)-(46,32)-(40,42).
 *
 * @param plateText optional license plate — shows up to 7 chars uppercase. Falls back
 *   to "—" when null or blank so the marker is never empty.
 * @param selected when true, a two-pass halo rings the body for selection state.
 */
@Composable
fun LicensePlateMarker(
    plateText: String? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val outfit = rememberOutfitFontFamily()
    val measurer = rememberTextMeasurer()
    val textStyle = remember(outfit) {
        TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            letterSpacing = 1.5.sp,
        )
    }
    val display = plateText?.trim()?.take(7)?.uppercase()?.ifBlank { "—" } ?: "—"

    Canvas(modifier = modifier.size(PLATE_W, PLATE_H)) {
        val s = size.width / PLATE_VIEWPORT_W

        // Selection halo — two-pass (outer dark shadow, inner white ring) on body rect
        if (selected) {
            drawRoundRect(
                color = MarkerColors.SelectionShadow.copy(alpha = HALO_OUTER_ALPHA),
                topLeft = Offset((4f - HALO_OUTER_EXPAND) * s, (4f - HALO_OUTER_EXPAND) * s),
                size = Size((72f + HALO_OUTER_EXPAND * 2f) * s, (28f + HALO_OUTER_EXPAND * 2f) * s),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * s),
                style = Stroke(width = HALO_OUTER_STROKE * s),
            )
            drawRoundRect(
                color = MarkerColors.SelectionRing.copy(alpha = HALO_INNER_ALPHA),
                topLeft = Offset((4f - HALO_INNER_EXPAND) * s, (4f - HALO_INNER_EXPAND) * s),
                size = Size((72f + HALO_INNER_EXPAND * 2f) * s, (28f + HALO_INNER_EXPAND * 2f) * s),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * s),
                style = Stroke(width = HALO_INNER_STROKE * s),
            )
        }

        // Ground shadow
        drawOval(
            color = Color.Black.copy(alpha = GROUND_SHADOW_ALPHA),
            topLeft = Offset(30f * s, 43f * s),
            size = Size(20f * s, 3f * s),
        )

        // Body + tip path
        val plate = plateBodyPath(s)
        drawPath(plate, color = MarkerColors.PlateAmber)
        drawPath(plate, color = MarkerColors.PlateAmberDk, style = Stroke(width = PLATE_STROKE * s))

        // Plate text
        val result = measurer.measure(text = AnnotatedString(display), style = textStyle)
        drawText(
            result,
            color = MarkerColors.PlateOnAmber,
            topLeft = Offset(
                x = PLATE_BODY_CX * s - result.size.width / 2f,
                y = PLATE_BODY_CY * s - result.size.height / 2f,
            ),
        )
    }
}

private fun plateBodyPath(s: Float): Path = Path().apply {
    val cornerR = 4f * s
    val l = 4f * s; val t = 4f * s; val r = 76f * s; val b = 32f * s
    moveTo(l + cornerR, t)
    lineTo(r - cornerR, t)
    cubicTo(r, t, r, t, r, t + cornerR)
    lineTo(r, b)
    lineTo(46f * s, b)
    lineTo(40f * s, 42f * s)
    lineTo(34f * s, b)
    lineTo(l, b)
    lineTo(l, t + cornerR)
    cubicTo(l, t, l, t, l + cornerR, t)
    close()
}

private val PLATE_W              = 80.dp
private val PLATE_H              = 50.dp
private const val PLATE_VIEWPORT_W = 80f
private const val PLATE_BODY_CX    = 40f
private const val PLATE_BODY_CY    = 18f
private const val PLATE_STROKE     = 1.5f

// ─── MyVehicle marker — legacy fallback (ParkingLocationScreen) ──────────────

/**
 * Fallback teardrop marker used by screens that have a parking location but no
 * [io.apptolast.paparcar.domain.model.ParkedVehicleSummary] context (e.g.
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
 * Free-spot marker. Circle with a parking "P" icon — color encodes the spot's
 * reliability tier so it stays in sync with the peek modal badge. [MAP-MARKERS-RELIABILITY-001]
 *
 * @param reliability tier that drives bg/on palette. Defaults to [SpotReliabilityUiState.HIGH]
 *   (green) for legacy callers that don't have a [Spot] in hand.
 * @param selected when true the border turns white to indicate selection.
 */
@Composable
fun FreeSpotMarker(
    modifier: Modifier = Modifier,
    reliability: SpotReliabilityUiState = SpotReliabilityUiState.HIGH,
    selected: Boolean = false,
) {
    val shadowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = GROUND_SHADOW_ALPHA)
    val palette = reliability.markerPalette()

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
                .background(color = palette.bg, shape = CircleShape)
                .border(
                    width = if (selected) FREE_SPOT_SEL_STROKE else FREE_SPOT_MARKER_STROKE,
                    color = if (selected) Color.White else palette.on.copy(alpha = 0.3f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            Icon(
                imageVector = Icons.Outlined.LocalParking,
                contentDescription = null,
                tint = if (isDark) palette.on else Color.White,
                modifier = Modifier.size(FREE_SPOT_MARKER_ICON),
            )
        }
    }
}

private data class SpotMarkerPalette(val bg: Color, val on: Color)

private fun SpotReliabilityUiState.markerPalette(): SpotMarkerPalette = when (this) {
    SpotReliabilityUiState.HIGH   -> SpotMarkerPalette(MarkerColors.SpotGreen, MarkerColors.SpotOnGreen)
    SpotReliabilityUiState.MEDIUM -> SpotMarkerPalette(MarkerColors.SpotAmber, MarkerColors.SpotOnAmber)
    SpotReliabilityUiState.LOW    -> SpotMarkerPalette(MarkerColors.SpotRed,   MarkerColors.SpotOnRed)
    SpotReliabilityUiState.MANUAL -> SpotMarkerPalette(MarkerColors.SpotBlue,  MarkerColors.SpotOnBlue)
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
 * On-map marker for a saved [io.apptolast.paparcar.domain.model.Zone].
 * Blue flat-top hexagon with the first 3 chars of the zone name — shape and
 * colour distinguish it from spot markers (green circle) and vehicle markers
 * (amber rectangle). [MAP-MARKERS-REDESIGN-001]
 *
 * @param zoneCode 1–3 char label — usually `zone.name.take(3).uppercase()`.
 * @param isPrivate when true a white-outlined lock badge appears at the bottom-end.
 */
@Composable
fun ZoneMarker(
    zoneCode: String,
    modifier: Modifier = Modifier,
    isPrivate: Boolean = false,
    isOccupied: Boolean = false,
) {
    val outfit = rememberOutfitFontFamily()
    val measurer = rememberTextMeasurer()
    val textStyle = remember(outfit) {
        TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = MarkerColors.ZoneOnBlue,
        )
    }
    val code = zoneCode.take(3).uppercase()

    Box(
        modifier = modifier.size(
            width  = ZONE_MARKER_DIAM,
            height = ZONE_MARKER_DIAM + ZONE_MARKER_GROUND_GAP,
        ),
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(ZONE_MARKER_DIAM),
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val R = size.minDimension * 0.42f

            // Flat-top hexagon (vertices at 30°, 90°, 150°, 210°, 270°, 330°)
            val hex = Path().apply {
                for (i in 0 until 6) {
                    val angle = Math.PI / 6.0 + Math.PI / 3.0 * i
                    val x = (cx + R * cos(angle)).toFloat()
                    val y = (cy + R * sin(angle)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(hex, color = MarkerColors.ZoneBlue)
            drawPath(hex, color = MarkerColors.ZoneOnBlue, style = Stroke(width = ZONE_HEX_STROKE))

            // Zone code text centred in hexagon
            val result = measurer.measure(text = AnnotatedString(code), style = textStyle)
            drawText(
                result,
                color = MarkerColors.ZoneOnBlue,
                topLeft = Offset(cx - result.size.width / 2f, cy - result.size.height / 2f),
            )
        }

        if (isPrivate && !isOccupied) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(ZONE_LOCK_BADGE_DP)
                    .background(color = MarkerColors.ZoneBlue, shape = CircleShape)
                    .border(width = 1.dp, color = Color.White, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(ZONE_LOCK_ICON_DP),
                )
            }
        }
    }
}

private val ZONE_MARKER_DIAM       = 42.dp
private val ZONE_MARKER_GROUND_GAP = 4.dp
private val ZONE_LOCK_BADGE_DP     = 16.dp
private val ZONE_LOCK_ICON_DP      = 12.dp
private const val ZONE_HEX_STROKE  = 1.5f

// ─── Centre-pin family ───────────────────────────────────────────────────────

/**
 * Animated circle scaffold shared by [ReportCenterPin] and [ParkingCenterPin].
 * Renders a border-only circle (no fill) with a ground shadow at the BOTTOM.
 * Lifts and scales slightly when [cameraMoving] to signal the pin is floating.
 *
 * Geometry mirrors [VehicleBadgeMarker] / [FreeSpotMarker]: a `DIAM x (DIAM + GROUND_GAP)`
 * box with the circle aligned TopCenter and the shadow at BottomCenter. This guarantees
 * the **bottom-centre** of the scaffold == the visual ground point.
 *
 * Position with [Modifier.mapCenterPinAnchor] inside a `Box(contentAlignment = Center)`
 * so the bottom-centre lands on the parent centre — which is the geographic camera
 * target. That matches the `(0.5, 1.0)` anchor used by placed markers, so the pin
 * the user is positioning visually coincides with the marker that will replace it.
 */
@Composable
private fun RoundCenterPinScaffold(
    cameraMoving: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val offsetY = remember { Animatable(0f) }
    val pinScale = remember { Animatable(1f) }
    LaunchedEffect(cameraMoving) {
        val (target, scaleTarget) = if (cameraMoving) ROUND_PIN_LIFT_DP to ROUND_PIN_LIFT_SCALE
                                    else              ROUND_PIN_REST_DP  to ROUND_PIN_REST_SCALE
        launch { offsetY.animateTo(target, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        launch { pinScale.animateTo(scaleTarget, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    }

    val ink = MaterialTheme.colorScheme.onSurface
    val shadowColor = ink.copy(alpha = ROUND_PIN_SHADOW_ALPHA)

    Box(
        modifier = modifier.size(width = ROUND_PIN_TOTAL_W, height = ROUND_PIN_TOTAL_H),
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = ROUND_PIN_SHADOW_W, height = ROUND_PIN_SHADOW_H),
        ) { drawOval(color = shadowColor) }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = offsetY.value.dp)
                .scale(pinScale.value)
                .size(ROUND_PIN_DIAM)
                .border(width = ROUND_PIN_BORDER, color = ink, shape = CircleShape),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/**
 * Map-centre anchor for centre-pin composables.
 *
 * `Modifier.align(Alignment.Center)` aligns the child's **geometric centre** with the
 * parent centre — but the map's geographic camera target also sits at the parent
 * centre, and placed markers are rendered with anchor `(0.5, 1.0)`, i.e. the
 * **bottom-centre** of the bitmap is what pins to the latlon. The two anchors don't
 * agree, so dropping the pin makes the placed marker visually jump UP relative to
 * where the centre-pin circle was floating.
 *
 * This modifier shifts the composable up by half its measured height after layout,
 * so its bottom-centre — not its geometric centre — coincides with the parent's
 * centre alignment point. Result: the centre pin's shadow sits exactly where the
 * placed marker's anchor will pin, eliminating the visual jump.
 *
 * Apply inside a `Box(contentAlignment = Alignment.Center)` after [Modifier.align].
 */
fun Modifier.mapCenterPinAnchor(): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, -placeable.height / 2)
    }
}

@Composable
fun ReportCenterPin(
    cameraMoving: Boolean,
    modifier: Modifier = Modifier,
) {
    RoundCenterPinScaffold(cameraMoving, modifier) {
        Icon(
            imageVector = Icons.Outlined.LocalParking,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(ROUND_PIN_ICON_SIZE),
        )
    }
}

@Composable
fun ParkingCenterPin(
    cameraMoving: Boolean,
    modifier: Modifier = Modifier,
) {
    RoundCenterPinScaffold(cameraMoving, modifier) {
        Icon(
            imageVector = PaparcarIcons.VehicleCar,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(ROUND_PIN_ICON_SIZE),
        )
    }
}

@Composable
fun ZoneCenterPin(
    icon: ImageVector,
    @Suppress("UNUSED_PARAMETER") cameraMoving: Boolean,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(ZONE_AREA_ICON_SIZE),
    )
}

private val ROUND_PIN_DIAM        = 48.dp
private val ROUND_PIN_ICON_SIZE   = 26.dp
private val ROUND_PIN_GROUND_GAP  = 4.dp  // mirrors BADGE_GROUND_GAP / FREE_SPOT_MARKER_GROUND_GAP
private val ROUND_PIN_TOTAL_W     = ROUND_PIN_DIAM
private val ROUND_PIN_TOTAL_H     = ROUND_PIN_DIAM + ROUND_PIN_GROUND_GAP
private val ROUND_PIN_SHADOW_W    = 22.dp
private val ROUND_PIN_SHADOW_H    = 5.dp
private val ROUND_PIN_BORDER      = 2.5.dp
private const val ROUND_PIN_SHADOW_ALPHA = 0.32f
private const val ROUND_PIN_REST_DP      = 0f
private const val ROUND_PIN_LIFT_DP      = -10f
private const val ROUND_PIN_REST_SCALE   = 1.0f
private const val ROUND_PIN_LIFT_SCALE   = 1.04f
private val ZONE_AREA_ICON_SIZE = 22.dp

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
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val textColor = if (isDark) MarkerColors.SpotOnGreen else Color.White
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
            drawText(result, color = textColor)
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

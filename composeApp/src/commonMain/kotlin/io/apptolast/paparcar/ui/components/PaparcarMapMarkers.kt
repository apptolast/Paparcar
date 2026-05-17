package io.apptolast.paparcar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
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
 * Paparcar map markers — Composable implementation from the Design System bundle.
 *
 * These composables are designed to be rendered to a bitmap and passed to Google
 * Maps as a BitmapDescriptor. The kmpmaps library used by [PaparcarMapView]
 * does this conversion automatically when a composable is registered via
 * `customMarkerContent`.
 *
 * Logical dimensions are in dp; the BitmapDescriptor handles density. The marker
 * anchor in Maps must be `(0.5f, 1f)` so the tip of the teardrop pins the
 * geographic coordinate.
 *
 * Introduced for [MARKERS-001]. Currently NOT wired into [PaparcarMapView] — see
 * `VehicleMarkersPreviews` for visual verification. The swap will land in a
 * follow-up commit once the new design is validated against the existing
 * `MyCarMarkerContent` / `SpotMarkerContent`.
 */

// ─── Palette ─────────────────────────────────────────────────────────────────
// Matches the design tokens in the Design System (colors_and_type.css).
private object MarkerColors {
    val Green           = Color(0xFF25F48C)
    val Amber           = Color(0xFFF4A825)
    val Red             = Color(0xFFFF5252)
    val Blue            = Color(0xFF5B9EFF)
    val Forest          = Color(0xFF0D1C14)
    val OnAmber         = Color(0xFF3D2A10)
    val OnRed           = Color(0xFF3D1010)
    val OnBlue          = Color(0xFF061021)
    // Selection halo is drawn in two passes so it reads on any map tile
    // background: the dark outer stroke contrasts light tiles, the bright
    // inner stroke contrasts dark tiles. Both are theme-agnostic — a single
    // bitmap cache entry serves light and dark themes alike.
    val SelectionRing   = Color(0xFFE8F5EC)
    val SelectionShadow = Color(0xFF000000)
}

// Two-pass halo geometry — shared by every selected-state marker so they
// read with the same visual weight regardless of pin family.
private const val HALO_OUTER_EXPAND = 6f
private const val HALO_INNER_EXPAND = 4f
private const val HALO_OUTER_STROKE = 4.5f
private const val HALO_INNER_STROKE = 2.5f
private const val HALO_OUTER_ALPHA  = 0.55f
private const val HALO_INNER_ALPHA  = 0.95f

// Ground shadow alpha — applied to `onSurface`, so the resulting shadow
// flips between black-ish (light theme) and white-ish (dark theme).
private const val GROUND_SHADOW_ALPHA = 0.35f

// ─── MyVehicle marker — active parking session ───────────────────────────────

/**
 * Marker for the user's currently parked car (one per session).
 *
 * @param selected when true, draws a white selection halo around the pin.
 */
@Composable
fun MyVehicleMarker(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    // onSurface is dark in light themes / light in dark themes, so the ground
    // shadow flips polarity automatically: black-with-alpha on light map tiles,
    // white-with-alpha on dark map tiles. kmpmaps re-rasterises the marker when
    // the captured color changes, so the cache stays correct across theme flips.
    val shadowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = GROUND_SHADOW_ALPHA)
    Canvas(modifier = modifier.size(MY_VEHICLE_W, MY_VEHICLE_H)) {
        drawMyVehicle(selected = selected, shadowColor = shadowColor)
    }
}

private fun DrawScope.drawMyVehicle(selected: Boolean, shadowColor: Color) {
    // The teardrop path's bounding box matches its viewport (0..64 wide, 0..80
    // tall), but the 2px stroke extends ½px beyond on every side. Without
    // padding, that ½px gets clipped on bitmap render and the rounded balloon
    // corners read as flat. Scale to a viewport that's wider than the path so
    // the stroke + halo fall safely inside the canvas, then translate the
    // drawing in by the same amount on each side.
    val w = size.width
    val pad = MY_VEHICLE_VIEWPORT_PAD
    val scale = w / (64f + pad * 2f)
    val padPx = pad * scale

    translate(left = padPx, top = padPx) {
        if (selected) {
            // Two-pass halo so the selection ring reads against both light
            // and dark map tiles: outer dark stroke creates a thin shadow
            // (visible on light backgrounds), inner bright stroke pops
            // against dark backgrounds. Same "text on photo" trick used in
            // map labels.
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

        // Ground shadow ellipse under the pin tip. Theme-aware (see composable).
        drawOval(
            color = shadowColor,
            topLeft = Offset(20f * scale, 73.5f * scale),
            size = Size(24f * scale, 5f * scale),
        )

        val pin = teardropPath(cx = 32f, w = 64f, h = 80f, expand = 0f, scale = scale)
        drawPath(pin, color = MarkerColors.Green)
        drawPath(pin, color = MarkerColors.Forest.copy(alpha = 0.35f), style = Stroke(width = 2f * scale))

        // Inner dark disc to host the car glyph.
        drawCircle(
            color = MarkerColors.Forest,
            radius = 20f * scale,
            center = Offset(32f * scale, 32f * scale),
        )

        // Car glyph (Material Filled "directions_car", simplified) centred on the disc.
        translate(left = 20f * scale, top = 20f * scale) {
            drawCarIcon(scale = scale, color = MarkerColors.Green)
        }
    }
}

// Viewport units of breathing room added on every side of the teardrop path.
// Sized to fit the two-pass selection halo (outer dark stroke at expand=6 +
// stroke width) without clipping. The canvas dp sizes are inflated by the
// same ratio so the perceived pin remains identical to the previous design.
private const val MY_VEHICLE_VIEWPORT_PAD = 7f

// ─── FreeSpot marker — community-reported plaza libre ────────────────────────

/**
 * Free-spot marker. Variants per [SpotReliabilityLevel]:
 *  - HIGH   → green
 *  - MEDIUM → amber
 *  - LOW    → red
 *  - MANUAL → blue + "!" badge in the corner
 *
 * @param ttlProgress `1.0` = freshly published, `0.0` = about to expire. `null`
 *   skips the ring (use for stale-data-tolerant callers).
 * @param selected when true, draws a white halo around the pin.
 */
@Composable
fun FreeSpotMarker(
    reliability: SpotReliabilityLevel,
    modifier: Modifier = Modifier,
    ttlProgress: Float? = null,
    selected: Boolean = false,
) {
    val outfit = rememberOutfitFontFamily()
    val measurer = rememberTextMeasurer()
    val pStyle = remember(outfit) {
        TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            textAlign = TextAlign.Center,
        )
    }
    // Theme-aware ground shadow (see MyVehicleMarker for the rationale).
    val shadowColor = MaterialTheme.colorScheme.onSurface.copy(alpha = GROUND_SHADOW_ALPHA)
    Canvas(modifier = modifier.size(FREE_SPOT_W, FREE_SPOT_H)) {
        drawFreeSpot(
            reliability = reliability,
            ttlProgress = ttlProgress,
            selected = selected,
            shadowColor = shadowColor,
            measurer = measurer,
            pStyle = pStyle,
        )
    }
}

private fun DrawScope.drawFreeSpot(
    reliability: SpotReliabilityLevel,
    ttlProgress: Float?,
    selected: Boolean,
    shadowColor: Color,
    measurer: TextMeasurer,
    pStyle: TextStyle,
) {
    // Same viewport-pad trick as drawMyVehicle: scale to a viewport wider
    // than the path so the selection halo + outer dark stroke fall inside
    // the canvas instead of clipping at the edges. Without this, the
    // expand=6 outer halo at the right edge (x=74) was being cut off at
    // canvas width 46dp (scale 0.676 → x_px=50 vs canvas 46) — the user
    // saw the selected ring "as if it didn't fit".
    val w = size.width
    val pad = FREE_SPOT_VIEWPORT_PAD
    val scale = w / (68f + pad * 2f)
    val padPx = pad * scale

    val (fill, onFill) = when (reliability) {
        SpotReliabilityLevel.HIGH   -> MarkerColors.Green to MarkerColors.Forest
        SpotReliabilityLevel.MEDIUM -> MarkerColors.Amber to MarkerColors.OnAmber
        SpotReliabilityLevel.LOW    -> MarkerColors.Red   to MarkerColors.OnRed
        SpotReliabilityLevel.MANUAL -> MarkerColors.Blue  to MarkerColors.OnBlue
    }

    translate(left = padPx, top = padPx) {
        if (selected) {
            // Two-pass halo so the selection ring reads against both light
            // and dark map tiles: outer dark stroke creates a thin shadow
            // (visible on light backgrounds), inner bright stroke pops
            // against dark backgrounds.
            val haloOuter = teardropPath(
                cx = 34f, w = 68f, h = 84f, expand = HALO_OUTER_EXPAND,
                scale = scale, top = 4f, bottom = 78f,
            )
            drawPath(
                haloOuter,
                color = MarkerColors.SelectionShadow.copy(alpha = HALO_OUTER_ALPHA),
                style = Stroke(width = HALO_OUTER_STROKE * scale),
            )
            val haloInner = teardropPath(
                cx = 34f, w = 68f, h = 84f, expand = HALO_INNER_EXPAND,
                scale = scale, top = 4f, bottom = 78f,
            )
            drawPath(
                haloInner,
                color = MarkerColors.SelectionRing.copy(alpha = HALO_INNER_ALPHA),
                style = Stroke(width = HALO_INNER_STROKE * scale),
            )
        }

        drawOval(
            color = shadowColor,
            topLeft = Offset(21f * scale, 77.2f * scale),
            size = Size(26f * scale, 5.6f * scale),
        )

        val pin = teardropPath(cx = 34f, w = 68f, h = 84f, expand = 0f, scale = scale, top = 4f, bottom = 78f)
        drawPath(pin, color = fill)
        drawPath(pin, color = onFill.copy(alpha = 0.35f), style = Stroke(width = 2f * scale))

        if (ttlProgress != null) {
            val ringRadius = 22f * scale
            val center = Offset(34f * scale, 34f * scale)
            drawCircle(
                color = onFill.copy(alpha = 0.25f),
                radius = ringRadius,
                center = center,
                style = Stroke(width = 3f * scale),
            )
            val sweep = 360f * ttlProgress.coerceIn(0f, 1f)
            drawArc(
                color = onFill,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                size = Size(ringRadius * 2, ringRadius * 2),
                style = Stroke(width = 3f * scale, cap = StrokeCap.Round),
            )
        }

        val result = measurer.measure(text = AnnotatedString("P"), style = pStyle)
        val tx = 34f * scale - result.size.width / 2f
        val ty = 34f * scale - result.size.height / 2f
        translate(left = tx, top = ty) {
            drawText(result, color = onFill)
        }

        if (reliability == SpotReliabilityLevel.MANUAL) {
            drawCircle(MarkerColors.Forest, radius = 9f * scale, center = Offset(52f * scale, 14f * scale))
            drawCircle(
                MarkerColors.Blue,
                radius = 9f * scale,
                center = Offset(52f * scale, 14f * scale),
                style = Stroke(width = 2f * scale),
            )
            drawRect(
                MarkerColors.Blue,
                topLeft = Offset(50.8f * scale, 10f * scale),
                size = Size(2.4f * scale, 6f * scale),
            )
            drawRect(
                MarkerColors.Blue,
                topLeft = Offset(50.8f * scale, 17f * scale),
                size = Size(2.4f * scale, 2f * scale),
            )
        }
    }
}

// ─── Report centre pin — outlined twin of FreeSpotMarker ─────────────────────

/**
 * Centre indicator shown while Home is in Reporting mode. Same teardrop
 * silhouette as [FreeSpotMarker] but stroke-only and using the theme's
 * onSurface ink, so it reads as "a marker you are about to drop" and stays
 * visible on both light and dark map themes (contrasts with the surface
 * background by definition).
 *
 * Layout: an outer Box twice as tall as the pin so the pin's tip can sit
 * at the Box's geometric centre. That centre is where the map composable
 * anchors this indicator, i.e. the geographic camera target — making the
 * shadow ellipse (drawn at the Box centre) the visual "where the pin will
 * land" marker. The pin bounces above the shadow while the camera is
 * moving and settles back onto it when the user releases the gesture.
 */
@Composable
fun ReportCenterPin(
    cameraMoving: Boolean,
    modifier: Modifier = Modifier,
) {
    val offsetY = remember { Animatable(0f) }
    val pinScale = remember { Animatable(1f) }
    LaunchedEffect(cameraMoving) {
        val (target, scaleTarget) = if (cameraMoving) {
            REPORT_PIN_LIFT_DP to REPORT_PIN_LIFT_SCALE
        } else {
            REPORT_PIN_REST_DP to REPORT_PIN_REST_SCALE
        }
        launch {
            offsetY.animateTo(target, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        launch {
            pinScale.animateTo(scaleTarget, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    val ink = MaterialTheme.colorScheme.onSurface
    val shadowColor = ink.copy(alpha = REPORT_SHADOW_ALPHA)
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

    // Outer Box is 2× the pin height so the pin's TIP coincides with the Box's
    // geometric centre — that's the map anchor. Empty space below the centre
    // is intentional and stays transparent.
    Box(
        modifier = modifier.size(
            width = REPORT_PIN_W,
            height = REPORT_PIN_H * 2,
        ),
    ) {
        // Ground shadow — anchored at Box centre so it stays fixed at the
        // geographic camera target while the pin lifts above it.
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = REPORT_SHADOW_W, height = REPORT_SHADOW_H),
        ) {
            drawOval(color = shadowColor)
        }

        // The pin itself — drawn in the upper half so its tip aligns with the
        // Box centre. offset/scale apply the bounce only to the pin, leaving
        // the shadow steady.
        Canvas(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = offsetY.value.dp)
                .scale(pinScale.value)
                .size(width = REPORT_PIN_W, height = REPORT_PIN_H),
        ) {
            drawReportPin(ink = ink, measurer = measurer, pStyle = pStyle)
        }
    }
}

private fun DrawScope.drawReportPin(
    ink: Color,
    measurer: TextMeasurer,
    pStyle: TextStyle,
) {
    val w = size.width
    val scale = w / 68f
    val strokeWidth = REPORT_STROKE_WIDTH * scale

    // Outlined teardrop — same path math as FreeSpotMarker so the silhouette
    // matches and users intuitively recognise the centre pin as "the report
    // counterpart of the FreeSpot markers" they see across the map.
    val pin = teardropPath(
        cx = 34f, w = 68f, h = 84f, expand = 0f, scale = scale,
        top = 4f, bottom = 78f,
    )
    drawPath(pin, color = ink, style = Stroke(width = strokeWidth))

    // "P" centred on the pin disc — same letter and font as FreeSpotMarker.
    val result = measurer.measure(text = AnnotatedString("P"), style = pStyle)
    val tx = 34f * scale - result.size.width / 2f
    val ty = 32f * scale - result.size.height / 2f
    translate(left = tx, top = ty) {
        drawText(result, color = ink)
    }
}

// Same dp footprint as FreeSpotMarker so the report pin reads as the
// outlined twin of the spot markers seen around it on the map.
private val REPORT_PIN_W = 46.dp
private val REPORT_PIN_H = 57.dp
private val REPORT_SHADOW_W = 22.dp
private val REPORT_SHADOW_H = 5.dp
private const val REPORT_PIN_REST_DP = 0f
private const val REPORT_PIN_LIFT_DP = -10f
private const val REPORT_PIN_REST_SCALE = 1.0f
private const val REPORT_PIN_LIFT_SCALE = 1.04f
private const val REPORT_STROKE_WIDTH = 3.5f
private const val REPORT_SHADOW_ALPHA = 0.32f

/**
 * Centre indicator shown while Home is in AddingZone mode. Circular surface
 * with an onSurface ink border and the user's currently-selected zone icon
 * inside, so the picker selection is echoed at three places at once (text
 * field leading icon, chip row, this pin). Same bounce-on-camera-settle and
 * shadow molde as [ReportCenterPin] but a distinct silhouette (circle vs
 * teardrop) so it reads as "saving a place" rather than "reporting a spot".
 *
 * Layout uses the same 2× tall outer Box as ReportCenterPin: the circle is
 * positioned in the upper half so its bottom edge rests at the Box centre
 * (= geographic anchor = ground); the shadow ellipse stays anchored at the
 * Box centre while the circle lifts during drag.
 */
@Composable
fun ZoneCenterPin(
    icon: ImageVector,
    cameraMoving: Boolean,
    modifier: Modifier = Modifier,
) {
    val offsetY = remember { Animatable(0f) }
    val pinScale = remember { Animatable(1f) }
    LaunchedEffect(cameraMoving) {
        val (target, scaleTarget) = if (cameraMoving) {
            ZONE_PIN_LIFT_DP to ZONE_PIN_LIFT_SCALE
        } else {
            ZONE_PIN_REST_DP to ZONE_PIN_REST_SCALE
        }
        launch {
            offsetY.animateTo(target, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        launch {
            pinScale.animateTo(scaleTarget, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    val ink = MaterialTheme.colorScheme.onSurface
    val fill = MaterialTheme.colorScheme.surfaceContainer
    val accent = MaterialTheme.colorScheme.primary
    val shadowColor = ink.copy(alpha = REPORT_SHADOW_ALPHA)

    Box(
        modifier = modifier.size(
            width = ZONE_PIN_DIAM,
            height = ZONE_PIN_DIAM * 2,
        ),
    ) {
        // Ground shadow — anchored at Box centre so it stays fixed at the
        // geographic camera target while the circle lifts above it.
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = ZONE_SHADOW_W, height = ZONE_SHADOW_H),
        ) {
            drawOval(color = shadowColor)
        }

        // The pin itself — a filled circle with an onSurface ink border and
        // the selected zone icon centred inside. Placed at TopCenter so its
        // bottom edge sits exactly at the Box centre (ground line); the
        // bounce only translates the circle, leaving the shadow steady.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = offsetY.value.dp)
                .scale(pinScale.value)
                .size(ZONE_PIN_DIAM)
                .background(color = fill, shape = CircleShape)
                .border(width = ZONE_STROKE_WIDTH, color = ink, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(ZONE_ICON_SIZE),
            )
        }
    }
}

private val ZONE_PIN_DIAM = 46.dp
private val ZONE_ICON_SIZE = 22.dp
private val ZONE_STROKE_WIDTH = 3.dp
private val ZONE_SHADOW_W = 22.dp
private val ZONE_SHADOW_H = 5.dp
private const val ZONE_PIN_REST_DP = 0f
private const val ZONE_PIN_LIFT_DP = -10f
private const val ZONE_PIN_REST_SCALE = 1.0f
private const val ZONE_PIN_LIFT_SCALE = 1.04f

// ─── Zone marker — saved habitual place (Casa, Trabajo…) ─────────────────────

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
        modifier = modifier.size(width = ZONE_MARKER_DIAM, height = ZONE_MARKER_DIAM + ZONE_MARKER_GROUND_GAP),
    ) {
        // Ground shadow ellipse at the bottom — sits at the marker's anchor
        // line (bitmap bottom corresponds to the geographic point).
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = ZONE_MARKER_SHADOW_W, height = ZONE_MARKER_SHADOW_H),
        ) {
            drawOval(color = shadowColor)
        }

        // The circle pin itself — surfaceContainer fill so the icon stays
        // readable over any map tile, onSurface stroke for depth, primary
        // tint on the icon to mirror the chip picker's selected colouring.
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

// ─── Cluster marker — several spots grouped at low zoom ──────────────────────

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
        drawCircle(MarkerColors.Green, radius = 30f * s, center = Offset(34f * s, 34f * s))
        drawCircle(
            MarkerColors.Forest,
            radius = 30f * s,
            center = Offset(34f * s, 34f * s),
            style = Stroke(width = 3f * s),
        )
        val result = measurer.measure(text = AnnotatedString(count.toString()), style = style)
        translate(34f * s - result.size.width / 2f, 34f * s - result.size.height / 2f) {
            drawText(result, color = MarkerColors.Forest)
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

/**
 * Teardrop / pin path. Re-builds the canonical SVG path with the given scale
 * and optional `expand` (used by the selection halo to draw a slightly larger
 * outline). The geometry is fixed and mirrors the bundled `ic_marker_*.xml`
 * drawables so the Composable and the static drawables render identically.
 */
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

/** Car glyph (24×24 viewport) — simplified Material `directions_car` path. */
private fun DrawScope.drawCarIcon(scale: Float, color: Color) {
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
    drawPath(ws, color = MarkerColors.Forest)
}

// ─── Marker sizes (logical, dp) ──────────────────────────────────────────────
// ~33% smaller than the original Design System spec (64×80 / 68×84 / 58) — the
// initial sizes read too prominent on real screens, especially at default zoom
// where multiple markers cluster nearby. Aspect ratios are preserved so the
// teardrop path (calibrated to 64×80 and 68×84 viewports inside the Canvas)
// scales proportionally without clipping.
//
// Canvas sizes are deliberately INFLATED beyond the pin's bounding box: each
// marker's draw fn divides width by `(viewport + pad*2)`, so the perceived
// pin renders at the original size but a margin of transparent space sits
// around it. That margin is what hosts the selection halo (expand=6 outer
// stroke) without clipping. The map anchor (0.5f, 1f) still aligns the pin
// TIP with the geographic coordinate because the bottom margin is part of
// the symmetric pad — the bitmap grows around the pin, not below it.
// MyVehicle: viewport 64+14=78 wide × 80+14=94 tall. Canvas 46dp wide →
// scale = 46/78 = 0.590; height = 94*0.590 ≈ 55dp so path bottom aligns with
// canvas bottom (tip touches the bitmap anchor). Reduced from 53×64 to give
// less prominence next to FreeSpot markers and the (now-larger) ZoneMarker.
private val MY_VEHICLE_W = 46.dp
private val MY_VEHICLE_H = 55.dp
// FreeSpot: viewport 68+14=82 wide × 84+14=98 tall. Canvas 48dp wide →
// scale = 48/82 = 0.585; height = 98*0.585 ≈ 57dp so the same anchor math
// holds. Reduced from 55×66 for the same visual-balance reason.
private val FREE_SPOT_W  = 48.dp
private val FREE_SPOT_H  = 57.dp
private val CLUSTER_SIZE = 39.dp

private const val FREE_SPOT_VIEWPORT_PAD = 7f

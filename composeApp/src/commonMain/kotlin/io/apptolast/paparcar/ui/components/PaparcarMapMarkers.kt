package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    val Green         = Color(0xFF25F48C)
    val Amber         = Color(0xFFF4A825)
    val Red           = Color(0xFFFF5252)
    val Blue          = Color(0xFF5B9EFF)
    val Forest        = Color(0xFF0D1C14)
    val OnAmber       = Color(0xFF3D2A10)
    val OnRed         = Color(0xFF3D1010)
    val OnBlue        = Color(0xFF061021)
    val SelectionRing = Color(0xFFE8F5EC)
}

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
    Canvas(modifier = modifier.size(MY_VEHICLE_W, MY_VEHICLE_H)) {
        drawMyVehicle(selected = selected)
    }
}

private fun DrawScope.drawMyVehicle(selected: Boolean) {
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
            val haloPath = teardropPath(cx = 32f, w = 64f, h = 80f, expand = 5f, scale = scale)
            drawPath(
                haloPath,
                color = MarkerColors.SelectionRing.copy(alpha = 0.7f),
                style = Stroke(width = 2.5f * scale),
            )
        }

        // Ground shadow ellipse under the pin tip.
        drawOval(
            color = Color.Black.copy(alpha = 0.35f),
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
// Picked to comfortably contain a 2px stroke + selected-state halo without
// changing the perceived marker size.
private const val MY_VEHICLE_VIEWPORT_PAD = 4f

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
    Canvas(modifier = modifier.size(FREE_SPOT_W, FREE_SPOT_H)) {
        drawFreeSpot(
            reliability = reliability,
            ttlProgress = ttlProgress,
            selected = selected,
            measurer = measurer,
            pStyle = pStyle,
        )
    }
}

private fun DrawScope.drawFreeSpot(
    reliability: SpotReliabilityLevel,
    ttlProgress: Float?,
    selected: Boolean,
    measurer: TextMeasurer,
    pStyle: TextStyle,
) {
    val w = size.width
    val scale = w / 68f
    val (fill, onFill) = when (reliability) {
        SpotReliabilityLevel.HIGH   -> MarkerColors.Green to MarkerColors.Forest
        SpotReliabilityLevel.MEDIUM -> MarkerColors.Amber to MarkerColors.OnAmber
        SpotReliabilityLevel.LOW    -> MarkerColors.Red   to MarkerColors.OnRed
        SpotReliabilityLevel.MANUAL -> MarkerColors.Blue  to MarkerColors.OnBlue
    }

    if (selected) {
        val haloPath = teardropPath(cx = 34f, w = 68f, h = 84f, expand = 5f, scale = scale)
        drawPath(
            haloPath,
            color = MarkerColors.SelectionRing.copy(alpha = 0.7f),
            style = Stroke(width = 2.5f * scale),
        )
    }

    drawOval(
        color = Color.Black.copy(alpha = 0.35f),
        topLeft = Offset(21f * scale, 77.2f * scale),
        size = Size(26f * scale, 5.6f * scale),
    )

    val pin = teardropPath(cx = 34f, w = 68f, h = 84f, expand = 0f, scale = scale, top = 4f, bottom = 78f)
    drawPath(pin, color = fill)
    drawPath(pin, color = onFill.copy(alpha = 0.35f), style = Stroke(width = 2f * scale))

    if (ttlProgress != null) {
        val ringRadius = 22f * scale
        val center = Offset(34f * scale, 34f * scale)
        // Background ring (always full circle, low alpha)
        drawCircle(
            color = onFill.copy(alpha = 0.25f),
            radius = ringRadius,
            center = center,
            style = Stroke(width = 3f * scale),
        )
        // Active arc: 360° when freshly published, shrinks as TTL drains.
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

    // The "P" centred on the pin disc — Outfit Bold via TextMeasurer.
    val result = measurer.measure(text = AnnotatedString("P"), style = pStyle)
    val tx = 34f * scale - result.size.width / 2f
    val ty = 34f * scale - result.size.height / 2f
    translate(left = tx, top = ty) {
        drawText(result, color = onFill)
    }

    // Manual reports get a distinct "!" badge in the top-right corner.
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
// MY_VEHICLE_* keeps a proportional breathing-room margin over the visible
// pin: drawMyVehicle()'s scale formula divides by 72f so the teardrop's 2px
// stroke + rounded corners always fall inside the canvas, leaving room for
// antialiasing to feather the edges.
private val MY_VEHICLE_W = 48.dp
private val MY_VEHICLE_H = 60.dp
private val FREE_SPOT_W  = 46.dp
private val FREE_SPOT_H  = 57.dp
private val CLUSTER_SIZE = 39.dp

package io.apptolast.paparcar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale as drawScale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.presentation.util.SpotReliabilityUiState
import io.apptolast.paparcar.ui.icons.PaparcarIcons
import io.apptolast.paparcar.ui.icons.icon
import io.apptolast.paparcar.ui.theme.PapBlueLight
import io.apptolast.paparcar.ui.theme.PapDriveBlue
import io.apptolast.paparcar.ui.theme.PapGreenLight
import io.apptolast.paparcar.ui.theme.PapInk
import io.apptolast.paparcar.ui.theme.PapOutlineVariantLight
import io.apptolast.paparcar.ui.theme.rememberOutfitFontFamily
import kotlinx.coroutines.launch

/**
 * Paparcar map markers — three-marker family with distinct shapes per role.
 *
 * | Marker             | Shape             | Colour | Content              |
 * |--------------------|-------------------|--------|----------------------|
 * | Parked vehicle     | Amber rect + tip  | Amber  | License plate text   |
 * | Free spot          | Circle            | Green  | "P" parking icon     |
 * | Zone (saved place) | Pill label        | Theme  | Icon + zone name     |
 *
 * Rendered as bitmaps by the kmpmaps library via `customMarkerContent` in [PaparcarMapView].
 * Marker anchor = (0.5f, 1f): the bottom-centre of each bitmap pins the geographic coordinate.
 */

// ─── Shared palette ──────────────────────────────────────────────────────────

private object MarkerColors {
    // Free-spot tones now live in [SpotPalette] (Bolt-green teardrop pins). [BOLT-MARKERS-001]

    // License-plate marker — amber rectangle
    val PlateAmber    = Color(0xFFF59E0B)
    val PlateAmberDk  = Color(0xFFD97706)
    val PlateOnAmber  = Color(0xFF1C0900)

    // Zone marker is now a theme-tinted centre label (primary / tertiary), so it
    // carries no hardcoded palette here. [ZONE-AREA-001]

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
 * Marker for the user's parked vehicle: the Bolt-green design's **square "tag"** — a rounded
 * surface card holding the full-colour isometric carbody — sitting over a small position dome.
 * Instantly reads "mine, parked" and never confuses with the round green free-spot pucks around it.
 * [MAP-ICONS-V2]
 *
 * Rendered entirely in Canvas (the SVG uses nested `<svg>`/filters that an Android VectorDrawable
 * can't express) plus the carbody [VehicleIcon] overlaid in the tag interior. Coordinate system is
 * the design viewBox `104 × 88` scaled into [TAG_MARKER_W]; the bitmap is anchored bottom-centre so
 * the dome base pins the coordinate.
 *
 * **State lives in the tag border**, not by recolouring or dimming the car: green = parked/active,
 * blue = Bluetooth, grey = inactive (monitoring stopped). The carbody stays fully opaque/full-colour
 * in every state — it's identity, not status — so an inactive car never reads as "fading away".
 * **Selection** flips the border to the theme's max-contrast `onSurface` (white on dark, black on
 * light) — unified across every marker — and thickens it. The default border is the state colour
 * (no white-by-default). [MAP-ICONS-V2]
 *
 * @param selected when true the border becomes onSurface + thicker to signal selection.
 * @param isActive when false the tag border + position dot grey out (monitoring stopped); the car
 *   itself stays opaque.
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
    color: io.apptolast.paparcar.domain.model.VehicleColor? = null,
    contentAlpha: Float = 1f,
    originDot: Boolean = false,
) {
    // Bluetooth supersedes monitoring state (matches Vehicle.monitoringStatus): a BT car always reads
    // blue; a non-BT car is green when its monitoring is active, grey when inactive. [MAP-ICONS-V2]
    val tone = when {
        isBluetoothPaired -> VehicleBadgeTone.Bluetooth
        !isActive -> VehicleBadgeTone.Inactive
        else -> VehicleBadgeTone.Parked
    }
    // State colour = the tag border by default; identity colours stay brand-fixed in both themes.
    val stateColor = when (tone) {
        VehicleBadgeTone.Bluetooth -> PapBlueLight
        VehicleBadgeTone.Inactive  -> PapOutlineVariantLight
        else                       -> PapGreenLight
    }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val borderColor = if (selected) onSurface else stateColor
    // Theme-aware tag fill: white in light, near-black ink in dark — the original look. The full-colour
    // car pops on it in both themes. Detect dark by theme luminance (not isSystemInDarkTheme, which can
    // disagree with the resolved app theme). [MAP-ICONS-V2]
    val tagFill = if (MaterialTheme.colorScheme.surface.luminance() < TAG_DARK_LUMINANCE) PapInk else Color.White
    val shadowColor = onSurface.copy(alpha = TAG_SHADOW_ALPHA)
    val borderUnits = if (selected) TAG_SEL_BORDER_U else TAG_BORDER_U

    // dp-per-viewBox-unit, so the overlaid carbody lands exactly inside the drawn tag interior.
    val u = TAG_MARKER_W / TAG_VB_W

    Box(
        modifier = modifier.size(
            width  = TAG_MARKER_W,
            height = TAG_MARKER_W * (TAG_VB_H / TAG_VB_W),
        ),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val s = size.width / TAG_VB_W // px per unit
            // 1 · contact shadow under the dome
            drawOval(
                color = shadowColor,
                topLeft = Offset((52f - 5f) * s, (82.5f - 1.6f) * s),
                size = Size(10f * s, 3.2f * s),
            )
            // 2 · position dot — the exact ground point. For the departure marker ([originDot]) it's a
            // blue dot with a white halo: the clear point the trip breadcrumb line starts from. Otherwise
            // the state-coloured dot, kept opaque even when [contentAlpha] fades the rest. [TRIP-TRAIL-001]
            val dotCenter = Offset(52f * s, 80.5f * s)
            if (originDot) {
                drawCircle(color = Color.White, radius = TAG_DOT_R * s * ORIGIN_DOT_HALO_SCALE, center = dotCenter)
                drawCircle(color = PapDriveBlue, radius = TAG_DOT_R * s * ORIGIN_DOT_SCALE, center = dotCenter)
            } else {
                drawCircle(color = borderColor, radius = TAG_DOT_R * s, center = dotCenter)
            }
            // 3 · the tag — rounded surface card + state/selection border (faded by contentAlpha)
            val tagTopLeft = Offset(10f * s, 6f * s)
            val tagSize = Size(84f * s, 66f * s)
            val corner = CornerRadius(20f * s, 20f * s)
            drawRoundRect(color = tagFill.copy(alpha = tagFill.alpha * contentAlpha), topLeft = tagTopLeft, size = tagSize, cornerRadius = corner)
            drawRoundRect(
                color = borderColor.copy(alpha = borderColor.alpha * contentAlpha),
                topLeft = tagTopLeft,
                size = tagSize,
                cornerRadius = corner,
                style = Stroke(width = borderUnits * s),
            )
        }
        // 4 · the isometric carbody, centred in the tag interior (ContentScale.Fit); faded with the tag.
        VehicleIcon(
            carbody = carbodyType,
            size = sizeCategory,
            tint = Color.Unspecified,
            color = color,
            modifier = Modifier
                .offset(x = u * 16f, y = u * 13f)
                .size(width = u * 72f, height = u * 52f)
                .alpha(contentAlpha),
        )
    }
}

// Car tag — design viewBox 104×88 (the original 104×92 trimmed to the dome base) scaled into a
// 62dp-wide footprint (design spec 58–66dp). [MAP-ICONS-V2]
private val TAG_MARKER_W   = 62.dp
private const val TAG_VB_W = 104f
private const val TAG_VB_H = 88f
private const val TAG_BORDER_U      = 3.6f  // default state-colour border — matches the selected weight, a touch thicker (viewBox units)
private const val TAG_SEL_BORDER_U  = 4f    // selected onSurface border
private const val TAG_DOT_R         = 4.5f  // position-dot radius (viewBox units)
private const val TAG_SHADOW_ALPHA  = 0.18f
private const val TAG_DARK_LUMINANCE = 0.5f // theme surface below this ⇒ dark ⇒ ink fill, else white
private const val ORIGIN_DOT_SCALE      = 1.4f // blue trip-origin dot, larger than the normal dot [TRIP-TRAIL-001]
private const val ORIGIN_DOT_HALO_SCALE = 1.9f // white halo behind the origin dot

// ─── Marker 1c — Location-active driving puck (LocationActiveMarker) ─────────

/**
 * The user's own car as a **top-down driving puck** — shown in place of the native location dot
 * while detection is actively monitoring a trip. A translucent blue precision halo (static) with the
 * top-down carbody rotated to the GPS [headingDegrees] on top. Anchored centre on the map so it
 * pivots around the coordinate. [MAP-ICONS-V2]
 *
 * Heading is baked per bitmap (kmpmaps has no marker rotation): the caller buckets the bearing and
 * passes the bucket angle here, so the cached bitmap matches its contentId.
 */
@Composable
fun LocationActiveMarker(
    carbody: io.apptolast.paparcar.domain.model.CarbodyType?,
    size: VehicleSize?,
    headingDegrees: Float,
    modifier: Modifier = Modifier,
    color: io.apptolast.paparcar.domain.model.VehicleColor? = null,
) {
    Box(modifier.size(LOC_ACTIVE_DIAM), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(color = LOC_HALO_BLUE, alpha = LOC_HALO_ALPHA, radius = this.size.minDimension / 2f)
        }
        VehicleTopdownIcon(
            carbody = carbody,
            size = size,
            color = color,
            modifier = Modifier
                .size(LOC_ACTIVE_CAR)
                .rotate(headingDegrees),
        )
    }
}

private val LOC_ACTIVE_DIAM = 54.dp
private val LOC_ACTIVE_CAR  = 38.dp
private val LOC_HALO_BLUE   = Color(0xFF2F6BFF) // matches the design's location halo / en-route blue
private const val LOC_HALO_ALPHA = 0.16f

// ─── Marker 1d — Trip origin dot (DepartureDotMarker) ────────────────────────

/**
 * The trip's origin: a small blue dot with a white halo, marking exactly where the departing vehicle
 * left from — the point the breadcrumb trail starts at. Center-anchored on the map so the dot sits on
 * the coordinate. Replaces the old faded-car departure badge: the origin is now just the clear dot.
 * [DEPART-CONSISTENCY-001] [TRIP-TRAIL-001]
 */
@Composable
fun DepartureDotMarker(modifier: Modifier = Modifier) {
    Box(modifier.size(DEPARTURE_DOT_BOX), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension / 2f
            drawCircle(color = Color.White, radius = r)
            drawCircle(color = PapDriveBlue, radius = r * DEPARTURE_DOT_INNER_SCALE)
        }
    }
}

private val DEPARTURE_DOT_BOX = 16.dp
private const val DEPARTURE_DOT_INNER_SCALE = 0.6f

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

// ─── Marker 2 — Free spot (FreeSpotMarker) — Bolt-green teardrop pin ─────────
// Bolt-green design: brand-filled teardrop + white 3px halo, a "P" drawn as a
// rounded stroke, a freshness ring whose arc length encodes the reliability
// tier, and a tier-coloured badge (clock for cooling/expiring, person for
// manual). When people are en route the pin flips to the blue "reserved" state
// with a dashed orbit + a people pill carrying the count. [BOLT-MARKERS-001]
//
// viewBox 72×92, anchor = pin tip ≈ (36, 90) → bottom-centre. All geometry is
// drawn in viewBox units scaled by `s = width / 72`.

/** Bolt-green spot palette — fixed brand tones (independent of [MaterialTheme]). */
// Brand tier colours — kept fixed across themes (neutrals invert via `paper`/`ink`). [BOLT-MARKERS-001]
private object SpotPalette {
    val Green       = Color(0xFF009F5E) // libre · fresca
    val Amber       = Color(0xFFE08200) // libre · enfriándose
    val Red         = Color(0xFFE0322F) // libre · caduca ya
    val ManualBlue  = Color(0xFF0057CA) // reporte manual
    val EnRouteBlue = Color(0xFF2F6BFF) // reservada · en ruta
    val Paper       = Color(0xFFFFFFFF) // fixed white — ring / "P" / TTL ring / badge discs
    val Ink         = Color(0xFF0E1A2E) // fixed dark — contact shadow / en-route pill
}

/** Per-tier visual recipe: pin colour, freshness-ring fraction, which badge. */
private data class SpotTierVisual(
    val color: Color,
    /** 0..1 arc length of the freshness ring; null = no ring (manual). */
    val ringFraction: Float?,
    val clockBadge: Boolean,
    val personBadge: Boolean,
)

// Ring length is static per tier (no live TTL timestamp wired to the marker
// layer yet — animated countdown deferred). Fractions mirror the reference
// SVGs (full / ~½ / minimal). [BOLT-MARKERS-001]
private fun SpotReliabilityUiState.tierVisual(): SpotTierVisual = when (this) {
    SpotReliabilityUiState.HIGH   -> SpotTierVisual(SpotPalette.Green,      ringFraction = 1f,    clockBadge = false, personBadge = false)
    SpotReliabilityUiState.MEDIUM -> SpotTierVisual(SpotPalette.Amber,      ringFraction = 0.5f,  clockBadge = true,  personBadge = false)
    SpotReliabilityUiState.LOW    -> SpotTierVisual(SpotPalette.Red,        ringFraction = 0.16f, clockBadge = true,  personBadge = false)
    SpotReliabilityUiState.MANUAL -> SpotTierVisual(SpotPalette.ManualBlue, ringFraction = null,  clockBadge = false, personBadge = true)
}

/**
 * Free-spot marker — Bolt-green teardrop pin. Colour + freshness ring + badge
 * encode the reliability tier so the on-map marker matches the peek modal.
 * [BOLT-MARKERS-001]
 *
 * @param reliability tier driving colour/ring/badge. Defaults to [SpotReliabilityUiState.HIGH].
 * @param selected when true an extra white outer outline marks the selection
 *   (the live pulse ring is drawn separately by [PaparcarMapView]).
 * @param enRouteCount when > 0 the pin renders the blue "reserved · en route"
 *   state with a people pill carrying the count, overriding the tier colour.
 */
@Composable
fun FreeSpotMarker(
    modifier: Modifier = Modifier,
    reliability: SpotReliabilityUiState = SpotReliabilityUiState.HIGH,
    selected: Boolean = false,
    enRouteCount: Int = 0,
) {
    val outfit = rememberOutfitFontFamily()
    val measurer = rememberTextMeasurer()
    val w = if (selected) SPOT_PIN_SEL_W else SPOT_PIN_W
    val h = w * (SPOT_VIEWBOX_H / SPOT_VIEWBOX_W)
    // sp tracks the marker width so the pill count scales with the pin (the
    // canvas is a fixed Dp, so a width-relative sp keeps the same px ratio as
    // the `s`-scaled geometry). 15/72 = the reference SVG font-size ratio.
    val countStyle = remember(outfit, w) {
        TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = (w.value * 15f / SPOT_VIEWBOX_W).sp,
            textAlign = TextAlign.Center,
        )
    }

    // Spot pucks float above the map tiles, so their neutrals stay FIXED (not theme-inverted): the
    // white ring / "P" / TTL ring / badge discs are always white, the contact shadow + en-route pill
    // always dark. Only the brand tier colour (green/amber/red/blue) carries meaning. [MAP-ICONS-V2]
    val paper = SpotPalette.Paper
    val ink = SpotPalette.Ink
    val pPath = remember { PathParser().parsePathString(SPOT_P_PATH).toPath() }

    Canvas(modifier.size(width = w, height = h)) {
        val s = size.width / SPOT_VIEWBOX_W
        if (enRouteCount > 0) {
            drawEnRoutePin(s, enRouteCount, selected, measurer, countStyle, pPath, paper, ink)
        } else {
            drawTierPin(s, reliability.tierVisual(), selected, pPath, paper, ink)
        }
    }
}

/**
 * Tail-less spot puck for use inside list containers (only the round "P" puck, no pointing tip). The
 * same puck drawing as [FreeSpotMarker], so the list badge and the map marker read identically —
 * colour + TTL ring + badge encode the reliability tier. [HOME-PUCK-001]
 *
 * @param reliability tier driving colour/ring/badge.
 * @param enRouteCount when > 0 the puck turns the blue "reserved · en route" colour to mirror the
 *   map marker (ring/badge dropped; the row shows a separate en-route indicator).
 */
@Composable
fun SpotPuckIcon(
    reliability: SpotReliabilityUiState,
    modifier: Modifier = Modifier,
    enRouteCount: Int = 0,
    selected: Boolean = false,
) {
    val paper = SpotPalette.Paper
    val ink = SpotPalette.Ink
    val pPath = remember { PathParser().parsePathString(SPOT_P_PATH).toPath() }
    val visual = if (enRouteCount > 0) {
        SpotTierVisual(SpotPalette.EnRouteBlue, ringFraction = null, clockBadge = false, personBadge = false)
    } else {
        reliability.tierVisual()
    }
    Canvas(modifier) {
        val s = size.minDimension / SPOT_VIEWBOX_W
        // Centre the puck circle (viewBox centre y = 34) vertically in the square slot — the marker
        // viewBox reserves the lower rows for the tail we're omitting here.
        translate(top = size.height / 2f - SPOT_PUCK_CENTER_Y * s) {
            drawSpotPuck(s, visual, selected, pPath, paper, ink)
        }
    }
}

// ── Puck geometry (viewBox 72×82 units) ──────────────────────────────────────
// Spot marker = round "puck" (centre 36,34 r29) carrying the Fredoka "P", a per-tier TTL ring and an
// optional badge, over a separated position dot (the exact ground point). Anchor = bottom-centre.
// [MAP-ICONS-V2]

// Fredoka "P" outline (stem + bowl + counter; nonzero winding gives the hole), positioned for the
// 72×82 viewBox. Parsed once into a unit Path and drawn scaled by `s`. [MAP-ICONS-V2]
private const val SPOT_P_PATH =
    "M27.42 24.66 a4.16 4.16 0 0 1 8.32 0 L35.74 42.34 a4.16 4.16 0 0 1 -8.32 0 Z " +
        "M27.42 28.82 a9.62 8.32 0 1 1 19.24 0 a9.62 8.32 0 1 1 -19.24 0 Z " +
        "M32.81 28.82 a4.23 3.66 0 1 0 8.47 0 a4.23 3.66 0 1 0 -8.47 0 Z"

private fun DrawScope.drawSpotShadow(s: Float, ink: Color) {
    drawOval(
        color = ink.copy(alpha = 0.12f),
        topLeft = Offset(31.5f * s, 71.4f * s),
        size = Size(9f * s, 3.2f * s),
    )
}

/** Separated position dot — the exact ground point, in the marker's own colour (no border). [MAP-ICONS-V2] */
private fun DrawScope.drawSpotDot(s: Float, color: Color) {
    drawCircle(color, radius = SPOT_DOT_R * s, center = Offset(36f * s, 71f * s))
}

/** The round puck body + paper stroke; selected adds an outer onSurface ring (max contrast). */
private fun DrawScope.drawPuckBody(
    s: Float,
    color: Color,
    selected: Boolean,
    paper: Color,
    ink: Color,
    radius: Float = 29f,
) {
    val c = Offset(36f * s, 34f * s)
    if (selected) drawCircle(ink, radius = (radius + 3.4f) * s, center = c, style = Stroke(width = 3.2f * s))
    drawCircle(color, radius = radius * s, center = c)
    drawCircle(paper, radius = radius * s, center = c, style = Stroke(width = 3.2f * s))
}

/** TTL ring: faint full base (r22) + a bright arc whose length = [fraction]. Static per tier. */
private fun DrawScope.drawTtlRing(s: Float, fraction: Float, paper: Color) {
    val topLeft = Offset(14f * s, 12f * s) // centre (36,34) r22
    val sz = Size(44f * s, 44f * s)
    drawArc(paper.copy(alpha = 0.30f), -90f, 360f, false, topLeft, sz, style = Stroke(3.4f * s))
    if (fraction > 0f) {
        drawArc(
            paper, -90f, 360f * fraction.coerceIn(0f, 1f), false, topLeft, sz,
            style = Stroke(3.4f * s, cap = StrokeCap.Round),
        )
    }
}

/** The Fredoka "P" — filled, drawn from [pPath] (unit coords) scaled into the viewBox. */
private fun DrawScope.drawPFredoka(s: Float, pPath: Path, color: Color) {
    drawScale(s, s, pivot = Offset.Zero) { drawPath(pPath, color) }
}

private fun DrawScope.drawBadgeDisc(s: Float, tier: Color, discColor: Color) {
    drawCircle(discColor, 13f * s, Offset(56f * s, 15f * s))
    drawCircle(tier, 13f * s, Offset(56f * s, 15f * s), style = Stroke(2f * s))
}

private fun DrawScope.drawClockBadge(s: Float, tier: Color, discColor: Color) {
    drawBadgeDisc(s, tier, discColor)
    val c = Offset(55.92f * s, 14.92f * s)
    drawCircle(tier, 5.61f * s, c, style = Stroke(1.58f * s))
    val hands = Path().apply {
        moveTo(55.92f * s, 12.02f * s)
        lineTo(55.92f * s, 14.92f * s)
        lineTo(57.9f * s, 16.11f * s)
    }
    drawPath(hands, tier, style = Stroke(1.58f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawPersonBadge(s: Float, tier: Color, discColor: Color) {
    drawBadgeDisc(s, tier, discColor)
    // head + dome shoulders (reads as a person at badge scale)
    drawCircle(tier, 2.38f * s, Offset(55.92f * s, 12.6f * s))
    drawArc(
        tier, 180f, 180f, true,
        topLeft = Offset(51.5f * s, 16.5f * s), size = Size(9f * s, 5f * s),
    )
}

/**
 * The round puck WITHOUT the tail — body + TTL ring + "P" + optional badge. Shared by the on-map
 * [FreeSpotMarker] (which adds the shadow + separated position dot around it) and the tail-less
 * [SpotPuckIcon] used inside list containers. [HOME-PUCK-001]
 */
private fun DrawScope.drawSpotPuck(s: Float, v: SpotTierVisual, selected: Boolean, pPath: Path, paper: Color, ink: Color) {
    drawPuckBody(s, v.color, selected, paper, ink)
    v.ringFraction?.let { drawTtlRing(s, it, paper) }
    drawPFredoka(s, pPath, paper)
    if (v.clockBadge) drawClockBadge(s, v.color, paper)
    if (v.personBadge) drawPersonBadge(s, v.color, paper)
}

private fun DrawScope.drawTierPin(s: Float, v: SpotTierVisual, selected: Boolean, pPath: Path, paper: Color, ink: Color) {
    drawSpotShadow(s, ink)
    drawSpotDot(s, v.color)
    drawSpotPuck(s, v, selected, pPath, paper, ink)
}

// ── En-route ("reserved") state ──────────────────────────────────────────────

/** Two little heads — reads as "people" inside the pill. */
private fun DrawScope.drawPeopleGlyph(s: Float, left: Float, top: Float, paper: Color) {
    drawCircle(paper, 1.7f * s, Offset((left + 2.0f) * s, (top + 2.0f) * s))
    drawCircle(paper, 1.45f * s, Offset((left + 6.1f) * s, (top + 2.6f) * s))
    drawArc(paper, 180f, 180f, true, Offset((left - 0.4f) * s, (top + 3.4f) * s), Size(5.6f * s, 3.4f * s))
    drawArc(paper, 180f, 180f, true, Offset((left + 4.2f) * s, (top + 4.2f) * s), Size(5.0f * s, 3.0f * s))
}

private fun DrawScope.drawEnRoutePin(
    s: Float,
    count: Int,
    selected: Boolean,
    measurer: TextMeasurer,
    countStyle: TextStyle,
    pPath: Path,
    paper: Color,
    ink: Color,
) {
    drawSpotShadow(s, ink)
    drawSpotDot(s, SpotPalette.EnRouteBlue)
    // Dashed outer orbit (centre 36,34 r29)
    drawCircle(
        SpotPalette.EnRouteBlue,
        radius = 29f * s,
        center = Offset(36f * s, 34f * s),
        style = Stroke(2.4f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * s, 5f * s))),
    )
    // Inner blue puck (r25) inside the dashed orbit
    drawPuckBody(s, SpotPalette.EnRouteBlue, selected, paper, ink, radius = 25f)
    drawPFredoka(s, pPath, paper)

    // People pill — right-anchored at x=68 so it widens leftward for "9+". The pill uses `ink`
    // (dark in light theme / light in dark theme) with `paper` people + count on top.
    val wide = count > 9
    val pillW = if (wide) 30f else 26f
    val pillX = 68f - pillW
    drawRoundRect(
        ink,
        topLeft = Offset(pillX * s, 2f * s),
        size = Size(pillW * s, 22f * s),
        cornerRadius = CornerRadius(11f * s, 11f * s),
    )
    drawPeopleGlyph(s, left = pillX + 3f, top = 5.5f, paper = paper)

    val txt = if (wide) "9+" else count.toString()
    val res = measurer.measure(AnnotatedString(txt), countStyle)
    // Count sits in the right portion of the pill (text centre ≈ x 60, y 13).
    drawText(
        res, color = paper,
        topLeft = Offset(60f * s - res.size.width / 2f, 13f * s - res.size.height / 2f),
    )
}

private val SPOT_PIN_W     = 46.dp
private val SPOT_PIN_SEL_W  = 56.dp
private const val SPOT_VIEWBOX_W = 72f
private const val SPOT_VIEWBOX_H = 82f
private const val SPOT_DOT_R = 4.5f // position-dot radius (viewBox units)
private const val SPOT_PUCK_CENTER_Y = 34f // puck circle centre Y (viewBox units) — used to centre the tail-less icon

// ─── Marker 3 — Zone (ZoneMarker) ────────────────────────────────────────────

/**
 * On-map centre label for a saved [io.apptolast.paparcar.domain.model.Zone].
 *
 * Area-first design [ZONE-AREA-001]: the translucent radius ring (drawn as a
 * native map circle in [PaparcarMapView]) is the hero that communicates the
 * zone's area. This marker is just a small, sober chip that tags the centre
 * with the zone's icon + name. It deliberately recedes — soft surface fill,
 * thin tinted outline — so the bright spot/vehicle markers always read above it.
 *
 * @param name zone display name; shown next to the icon, ellipsised if long.
 * @param icon the zone's chosen preset icon (resolved from `zone.iconKey`).
 * @param isPrivate when true the chip tints to `tertiary` and shows a small lock.
 */
@Composable
fun ZoneMarker(
    name: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isPrivate: Boolean = false,
) {
    val zoneColor = if (isPrivate) MaterialTheme.colorScheme.tertiary
                    else           MaterialTheme.colorScheme.primary
    val paper = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface
    val outfit = rememberOutfitFontFamily()
    val label = name.trim()

    // Centre-anchored marker (0.5, 0.5 in PaparcarMapView), so the pill is centred
    // in its own bitmap and lands dead-centre on the circle centre — no ground gap.
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(ZONE_LABEL_CORNER))
                .background(paper.copy(alpha = ZONE_LABEL_BG_ALPHA))
                .border(
                    width = ZONE_LABEL_BORDER,
                    color = zoneColor.copy(alpha = ZONE_LABEL_BORDER_ALPHA),
                    shape = RoundedCornerShape(ZONE_LABEL_CORNER),
                )
                .padding(horizontal = ZONE_LABEL_PAD_H, vertical = ZONE_LABEL_PAD_V),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = zoneColor,
                modifier = Modifier.size(ZONE_LABEL_ICON),
            )
            if (label.isNotEmpty()) {
                Spacer(Modifier.width(ZONE_LABEL_GAP))
                Text(
                    text = label,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontFamily = outfit,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = ZONE_LABEL_TEXT_SP,
                    ),
                    modifier = Modifier.widthIn(max = ZONE_LABEL_MAX_TEXT_W),
                )
            }
            if (isPrivate) {
                Spacer(Modifier.width(ZONE_LABEL_GAP))
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = zoneColor,
                    modifier = Modifier.size(ZONE_LABEL_LOCK),
                )
            }
        }
    }
}

private val ZONE_LABEL_CORNER     = 999.dp
private val ZONE_LABEL_BORDER     = 1.dp
private val ZONE_LABEL_PAD_H      = 8.dp
private val ZONE_LABEL_PAD_V      = 4.dp
private val ZONE_LABEL_ICON       = 14.dp
private val ZONE_LABEL_LOCK       = 11.dp
private val ZONE_LABEL_GAP        = 4.dp
private val ZONE_LABEL_MAX_TEXT_W = 96.dp
private val ZONE_LABEL_TEXT_SP    = 11.sp
private const val ZONE_LABEL_BG_ALPHA     = 0.92f
private const val ZONE_LABEL_BORDER_ALPHA = 0.55f

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

/**
 * Lift wrapper for the centre-positioning pins: applies the float/bounce on [cameraMoving] to the
 * REAL marker composable inside, so the pin the user is placing is pixel-identical to the marker
 * that will replace it (no separate "placement" style). The marker brings its own shape/shadow/dot;
 * this only animates the lift. Bottom-anchored so the marker's ground point stays put. [MAP-ICONS-V2]
 */
@Composable
private fun LiftedCenterPin(
    cameraMoving: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val offsetY = remember { Animatable(0f) }
    val pinScale = remember { Animatable(1f) }
    // One-shot drop-in from above when the placement mode opens: the pin falls into
    // place and fades up to its ghost alpha, so it reads as "being placed". [MOTION-POLISH-001]
    val entry = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entry.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        )
    }
    LaunchedEffect(cameraMoving) {
        val (target, scaleTarget) = if (cameraMoving) ROUND_PIN_LIFT_DP to ROUND_PIN_LIFT_SCALE
                                    else              ROUND_PIN_REST_DP to ROUND_PIN_REST_SCALE
        launch { offsetY.animateTo(target, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        launch { pinScale.animateTo(scaleTarget, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    }
    Box(
        modifier = modifier
            // Entry drop (negative = from above) added to the camera-move lift.
            .offset(y = (offsetY.value + (1f - entry.value) * PLACING_ENTRY_DROP_DP).dp)
            // Translucent "ghost" so the placing pin never looks like a real, placed marker.
            .alpha(entry.value * PLACING_GHOST_ALPHA)
            .scale(pinScale.value),
        contentAlignment = Alignment.BottomCenter,
    ) { content() }
}

/** Manual-spot placement pin — the real spot puck (manual blue). [MAP-ICONS-V2] */
@Composable
fun ReportCenterPin(
    cameraMoving: Boolean,
    modifier: Modifier = Modifier,
) {
    LiftedCenterPin(cameraMoving, modifier) {
        FreeSpotMarker(reliability = SpotReliabilityUiState.MANUAL)
    }
}

/** Parked-car placement pin — the real square vehicle tag. Falls back to a sedan body when the
 *  positioning vehicle has no carbody so the tag always shows an isometric car. [MAP-ICONS-V2] */
@Composable
fun ParkingCenterPin(
    cameraMoving: Boolean,
    modifier: Modifier = Modifier,
    carbodyType: io.apptolast.paparcar.domain.model.CarbodyType? = null,
    sizeCategory: VehicleSize? = null,
    color: io.apptolast.paparcar.domain.model.VehicleColor? = null,
    isActive: Boolean = true,
    isBluetoothPaired: Boolean = false,
) {
    LiftedCenterPin(cameraMoving, modifier) {
        // Same marker the vehicle will get when placed — icon, paint colour AND state border
        // (grey inactive / blue BT / green active) — so the preview matches the result. [MOTION-POLISH-001]
        VehicleBadgeMarker(
            carbodyType = carbodyType ?: io.apptolast.paparcar.domain.model.CarbodyType.SEDAN,
            sizeCategory = sizeCategory,
            color = color,
            isActive = isActive,
            isBluetoothPaired = isBluetoothPaired,
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
        // Geometric-centre alignment puts the bounding box centre on the circle
        // centre, but a teardrop glyph (the default location pin) carries its mass
        // up top and a tip below, so it reads as floating high. A small downward
        // nudge sits the visual centre on the circle centre. [ZONE-AREA-001]
        modifier = modifier
            .size(ZONE_AREA_ICON_SIZE)
            .offset(y = ZONE_AREA_ICON_VNUDGE),
    )
}

// Lift/bounce applied to the centre-positioning pins ([LiftedCenterPin]) while the camera moves.
private const val ROUND_PIN_REST_DP      = 0f
private const val ROUND_PIN_LIFT_DP      = -10f
private const val ROUND_PIN_REST_SCALE   = 1.0f
private const val ROUND_PIN_LIFT_SCALE   = 1.04f
// Placement-pin treatment: starts this far above (drops in) and settles to a translucent ghost so
// the pin the user is positioning is clearly distinct from real, placed markers. [MOTION-POLISH-001]
private const val PLACING_ENTRY_DROP_DP  = -34f
private const val PLACING_GHOST_ALPHA    = 0.72f
private val ZONE_AREA_ICON_SIZE = 22.dp
private val ZONE_AREA_ICON_VNUDGE = 2.dp

// ─── Cluster marker ───────────────────────────────────────────────────────────

@Composable
fun FreeSpotClusterMarker(count: Int, modifier: Modifier = Modifier) {
    val outfit = rememberOutfitFontFamily()
    val measurer = rememberTextMeasurer()
    // Bolt-green cluster: brand-green disc + white outline + white count, on a
    // soft contact shadow. viewBox 72×72 (centre 36,34 r29). [BOLT-MARKERS-001]
    val style = remember(outfit) {
        TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = (CLUSTER_SIZE.value * 26f / 72f).sp,
            textAlign = TextAlign.Center,
        )
    }
    // Fixed neutrals like the spot pucks (floats over the map). [MAP-ICONS-V2]
    val paper = SpotPalette.Paper
    val ink = SpotPalette.Ink
    Canvas(modifier.size(width = CLUSTER_SIZE, height = CLUSTER_SIZE * 82f / 72f)) {
        val s = size.width / 72f
        drawSpotShadow(s, ink)
        drawSpotDot(s, SpotPalette.Green)
        drawCircle(SpotPalette.Green, radius = 29f * s, center = Offset(36f * s, 34f * s))
        drawCircle(paper, radius = 29f * s, center = Offset(36f * s, 34f * s), style = Stroke(width = 3.4f * s))
        val result = measurer.measure(text = AnnotatedString(count.toString()), style = style)
        drawText(
            result, color = paper,
            topLeft = Offset(36f * s - result.size.width / 2f, 34f * s - result.size.height / 2f),
        )
    }
}

private val CLUSTER_SIZE = 44.dp

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

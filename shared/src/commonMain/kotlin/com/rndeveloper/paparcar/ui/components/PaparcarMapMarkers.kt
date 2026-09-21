package com.rndeveloper.paparcar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.model.SpotFreshness
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import com.rndeveloper.paparcar.ui.icons.icon
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.PapCarBlueLight
import com.rndeveloper.paparcar.ui.theme.PapGreen
import com.rndeveloper.paparcar.ui.theme.PapSpotCoolingLight
import com.rndeveloper.paparcar.ui.theme.PapSpotExpiringLight
import com.rndeveloper.paparcar.ui.theme.PapSpotFreshPuck
import com.rndeveloper.paparcar.ui.theme.PapWatchGreenLight
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PapInk
import com.rndeveloper.paparcar.ui.theme.PapOutlineVariantLight
import com.rndeveloper.paparcar.ui.theme.zoneIdentityColor
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Paparcar map markers — three-marker family with distinct shapes per role.
 *
 * | Marker             | Shape             | Colour | Content              |
 * |--------------------|-------------------|--------|----------------------|
 * | Parked vehicle     | Amber rect + tip  | Amber  | License plate text   |
 * | Free spot          | Circle            | Green  | Given-spot hand + heart + announcement badge |
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

    // Zone marker is now a theme-tinted centre label (primary / neutral+lock for private), so it
    // carries no hardcoded palette here. [ZONE-AREA-001]

    // MyVehicle fallback teardrop. `MyVehicleMarker` is reachable ONLY from previews and the Dev
    // Catalog gallery — no production call site — so this pin is kept verbatim rather than
    // restyled. `LegacyGreen` was a second literal holding PapGreen's exact value; it is now the
    // token itself, so it cannot drift away from the brand it is copying.
    // [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]
    val LegacyGreen   = PapGreen
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
 * What kind of DOUBT the parked-vehicle tag is wearing — the vocabulary of its top-right corner.
 *
 * The two doubts are not the same thing and must not be drawn the same way:
 *
 * | state           | what happened to the user                                  | resolves when…            |
 * |-----------------|------------------------------------------------------------|---------------------------|
 * | [NONE]          | the car is HERE                                            | —                         |
 * | [ASKING]        | we don't know whether you parked — a question is OPEN      | you answer / it times out |
 * | [APPROXIMATE]   | you DID park and it is saved; we don't know exactly where  | only if you edit it       |
 *
 * Modelled as one value rather than two booleans because both dresses compete for the SAME corner
 * and cannot co-exist: a question has no saved session yet, so it can carry no radius. With two
 * flags that is a comment somebody has to read; here it is a state nobody can write.
 * [UI-A-SAVED-ZONE-WEARS-ITS-DOUBT-TOO-001]
 *
 * ⛔ In all three the carbody stays fully opaque and full-colour. Doubt about the PLACE may not be
 * expressed by doubting WHICH CAR — a dimmed carbody reads as "this vehicle is going away", the
 * failure mode the inactive state was explicitly designed around. Identity is not status.
 */
/**
 * The zone a parked car sits inside, worn by its own marker.
 * [MAP-A-PARKED-CAR-INSIDE-A-ZONE-WEARS-THE-ZONE-001]
 *
 * When a car is parked inside a saved zone, the zone stops labelling itself — its glyph and name
 * would sit on top of the car that is the subject — and the car carries the zone's glyph instead.
 * The ring stays: the ring is the AREA and the area is still true. What moves is the LABEL.
 *
 * @param icon the zone's own preset glyph, resolved from `zone.iconKey` by the caller.
 * @param isPrivate a private zone reads neutral and carries the padlock, exactly as
 *   [ZoneMarker] does — one rule said on both surfaces.
 *   [UI-A-PRIVATE-ZONE-IS-PRIVATE-ON-EVERY-SURFACE-001]
 */
data class VehicleZoneMark(
    val icon: ImageVector,
    val isPrivate: Boolean,
)

enum class VehicleMarkerDoubt {
    /** A fact with a point behind it: solid frame, opaque tag, empty corner. */
    NONE,

    /**
     * [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] The park is NOT a fact yet — this is the place
     * an open "did you park?" question is about. Same tag, same silhouette, so when the answer (or
     * the timeout) confirms it, the marker RESOLVES instead of one marker vanishing and another
     * appearing. Said three ways, none of which touch the car: the border goes DASHED, a `?` disc
     * sits on the tag's top corner, and the tag's own fill drops to [UNCONFIRMED_TAG_ALPHA].
     */
    ASKING,

    /**
     * [UI-A-SAVED-ZONE-WEARS-ITS-DOUBT-TOO-001] A saved session that is an AREA, not a point
     * (`zoneRadiusMeters != null`). The frame stays SOLID and the tag stays OPAQUE on purpose —
     * this is a FACT, and borrowing the ask's provisional dress would send the user hunting for a
     * notification that no longer exists. What changes is the corner: a target ⊙, the Canvas twin
     * of the `Icons.Rounded.Adjust` the peek's own approximate row already uses.
     *
     * It does not repeat what the doubt ring says. The ring says HOW BIG the doubt is; the target
     * says THIS PIN IS A CENTRE, NOT A POINT — and it is the only one of the two that survives
     * street zoom (where the ring's edge is off-screen), the dim pass, and a screen corner.
     */
    APPROXIMATE,
}

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
 * **State lives in the tag border**, not by recolouring or dimming the car: the frame wears the
 * vehicle's identity colour — its watch METHOD (green = active detection, blue = Bluetooth,
 * grey = unwatched) — matching the name/chip/ficha everywhere else. The carbody stays fully
 * opaque/full-colour in every state — it's identity, not status — so an inactive car never reads
 * as "fading away". [MAP-ICONS-V2] [UI-COLOR-DOCTRINE-001]
 *
 * @param selected when true the border becomes onSurface + thicker to signal selection.
 * @param isActive when false the tag border + position dot grey out (monitoring stopped); the car
 *   itself stays opaque.
 * @param isBluetoothPaired Bluetooth-watched vehicle — the frame reads blue instead of green.
 */
@Composable
fun VehicleBadgeMarker(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    sizeCategory: VehicleSize? = null,
    carbodyType: com.rndeveloper.paparcar.domain.model.CarbodyType? = null,
    isActive: Boolean = true,
    stableRank: Int? = null,
    isBluetoothPaired: Boolean = false,
    color: com.rndeveloper.paparcar.domain.model.VehicleColor? = null,
    contentAlpha: Float = 1f,
    /**
     * Which doubt this tag wears — see [VehicleMarkerDoubt] for the three dresses and the rule none
     * of them may break (the carbody never fades).
     */
    doubt: VehicleMarkerDoubt = VehicleMarkerDoubt.NONE,
    /** Non-null when this car is parked inside a saved zone. See [VehicleZoneMark]. */
    zoneMark: VehicleZoneMark? = null,
) {
    // Method-coloured frame, fixed light-theme tones (markers float over map tiles, not surfaces):
    // BT blue wins (matches Vehicle.monitoringStatus), then active green, grey when unwatched.
    // Selection flips to max-contrast onSurface + thicker. [UI-COLOR-DOCTRINE-001]
    val onSurface = MaterialTheme.colorScheme.onSurface
    val borderColor = when {
        selected -> onSurface
        isBluetoothPaired -> PapCarBlueLight
        isActive -> PapWatchGreenLight
        else -> PapOutlineVariantLight
    }
    // Theme-aware tag fill: white in light, near-black ink in dark — the original look. The full-colour
    // car pops on it in both themes. Detect dark by theme luminance (not isSystemInDarkTheme, which can
    // disagree with the resolved app theme). [MAP-ICONS-V2]
    val tagFill = if (MaterialTheme.colorScheme.surface.luminance() < TAG_DARK_LUMINANCE) PapInk else Color.White
    val shadowColor = onSurface.copy(alpha = TAG_SHADOW_ALPHA)
    val borderUnits = if (selected) TAG_SEL_BORDER_U else TAG_BORDER_U

    // dp-per-viewBox-unit, so the overlaid carbody lands exactly inside the drawn tag interior.
    val u = TAG_MARKER_W / TAG_VB_W

    // Where the zone disc lands, and it is a rule rather than a constant: the right corner belongs
    // to the doubt when there is one — whether the pin is real outranks where it is — so the zone
    // mirrors to the left instead of stacking. With no doubt it takes the right corner, which is
    // where the owner asked for it. [MAP-A-PARKED-CAR-INSIDE-A-ZONE-WEARS-THE-ZONE-001]
    val zoneBadgeCx = if (doubt == VehicleMarkerDoubt.NONE) BADGE_CX else BADGE_ZONE_CX_LEFT
    // The zone's own colour, not the vehicle's: this disc says WHICH PLACE, and privacy is a
    // category the glyph carries — the same resolver the zone's label uses on the map.
    val zoneDiscColor = zoneMark?.let { zoneIdentityColor(it.isPrivate) } ?: Color.Transparent

    // [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] The `?` is measured here, not drawn as a
    // hand-built path: a glyph at ~9 dp lives or dies on its hinting, and the app's own family
    // already draws every other label on this map. Map-marker `TextMeasurer` is the standing
    // exception in the typography guardrail, for exactly this. [UI-TYPE-ONE-VOICE-REACHES-MATERIAL-001]
    val markerFamily = PaparcarType.current.cardTitle.fontFamily
    val questionMeasurer = rememberTextMeasurer()
    val questionGlyph = if (doubt == VehicleMarkerDoubt.ASKING) {
        val style = remember(markerFamily) {
            TextStyle(
                fontFamily = markerFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (TAG_MARKER_W.value * BADGE_GLYPH_U / TAG_VB_W).sp,
                textAlign = TextAlign.Center,
            )
        }
        remember(questionMeasurer, style) {
            questionMeasurer.measure(text = AnnotatedString("?"), style = style)
        }
    } else {
        null
    }

    // The corner badge (`?` / ⊙) half-rides OUTSIDE the 104×88 design viewBox (centre (90,12),
    // radius 16+ring → x up to 107.5, y down to −5.5), and Canvas clips to its bounds — on device
    // the disc rendered square-cut. The badge belongs on the corner; what was wrong was the
    // canvas, so the canvas grows breathing room (symmetric sides + top, none at the bottom) and
    // the drawing translates into it. `u` (px per design unit) is untouched: the tag renders the
    // same size, the anchor's bottom-centre ground point stays put, and [TAG_PIN_GROUND_INSET]
    // still measures from an unpadded bottom. [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001]
    Box(
        modifier = modifier.size(
            width  = TAG_MARKER_W * (TAG_CANVAS_VB_W / TAG_VB_W),
            height = TAG_MARKER_W * (TAG_CANVAS_VB_H / TAG_VB_W),
        ),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val s = size.width / TAG_CANVAS_VB_W // px per unit
            translate(left = TAG_PAD_X * s, top = TAG_PAD_TOP * s) {
            // 1 · contact shadow under the dome
            drawOval(
                color = shadowColor,
                topLeft = Offset((52f - 5f) * s, (82.5f - 1.6f) * s),
                size = Size(10f * s, 3.2f * s),
            )
            // 2 · position dot — the exact ground point, in the state colour and kept opaque even
            // when [contentAlpha] fades the rest. The trip's ORIGIN is not drawn here: it has been
            // its own centre-anchored marker ([DepartureDotMarker]) since [DEPART-CONSISTENCY-001],
            // which is why this tag's blue variant had no caller left. [TRIP-TRAIL-001]
            val dotCenter = Offset(52f * s, TAG_DOT_CY * s)
            drawCircle(color = borderColor, radius = TAG_DOT_R * s, center = dotCenter)
            // 3 · the tag — rounded surface card + state/selection border (faded by contentAlpha)
            val tagTopLeft = Offset(10f * s, 6f * s)
            val tagSize = Size(84f * s, 66f * s)
            val corner = CornerRadius(20f * s, 20f * s)
            // Only the OPEN QUESTION goes provisional (translucent fill + dashed frame). A saved
            // approximate zone is a FACT — it keeps the solid frame and the opaque tag, and says
            // its doubt in the corner instead. [UI-A-SAVED-ZONE-WEARS-ITS-DOUBT-TOO-001]
            val asking = doubt == VehicleMarkerDoubt.ASKING
            val tagAlpha = if (asking) UNCONFIRMED_TAG_ALPHA else 1f
            drawRoundRect(
                color = tagFill.copy(alpha = tagFill.alpha * contentAlpha * tagAlpha),
                topLeft = tagTopLeft,
                size = tagSize,
                cornerRadius = corner,
            )
            drawRoundRect(
                color = borderColor.copy(alpha = borderColor.alpha * contentAlpha),
                topLeft = tagTopLeft,
                size = tagSize,
                cornerRadius = corner,
                // A dashed frame is the one "provisional" idiom that survives at marker scale, and
                // Canvas can draw it — the VectorDrawable ban on `stroke-dasharray` does not reach
                // here, which is the whole reason this marker family was never an SVG.
                style = Stroke(
                    width = borderUnits * s,
                    pathEffect = if (asking) {
                        PathEffect.dashPathEffect(
                            floatArrayOf(UNCONFIRMED_DASH_ON * s, UNCONFIRMED_DASH_OFF * s),
                        )
                    } else {
                        null
                    },
                ),
            )
            // The doubt badge rides the tag's TOP-RIGHT corner: the tip of the dome is the ground
            // point and the tag interior is the car, so the corner is the only real estate that
            // is neither. One slot, one word — which is why [VehicleMarkerDoubt] is an enum.
            //
            // FILLED, with the glyph knocked out of it: a solid identity-coloured disc carrying the
            // mark in the tag's own fill. The first pass had it the other way round — pale disc,
            // coloured glyph — and on device it read as one more pale blob among the map's
            // labels. The filled version is the same language the free-spot pucks already speak
            // (coloured disc, paper ring, paper glyph), so it reads as a marker badge at a
            // glance instead of as a sticker.
            //
            // The ring is the tag fill too, which is what keeps the disc off the map tiles —
            // never grey, which in this app already means "unwatched vehicle" and would vanish
            // against asphalt anyway.
            if (doubt != VehicleMarkerDoubt.NONE) {
                val badgeCenter = Offset(BADGE_CX * s, BADGE_CY * s)
                drawCircle(color = borderColor, radius = BADGE_R * s, center = badgeCenter)
                drawCircle(
                    color = tagFill,
                    radius = BADGE_R * s,
                    center = badgeCenter,
                    style = Stroke(width = BADGE_BORDER_U * s),
                )
                when (doubt) {
                    VehicleMarkerDoubt.NONE -> Unit
                    VehicleMarkerDoubt.ASKING -> questionGlyph?.let { glyph ->
                        drawText(
                            glyph,
                            color = tagFill,
                            topLeft = Offset(
                                badgeCenter.x - glyph.size.width / 2f,
                                badgeCenter.y - glyph.size.height / 2f,
                            ),
                        )
                    }
                    // The target ⊙ — a ring with its centre marked. Drawn, not measured: unlike the
                    // `?`, it is two circles and owes nothing to a font's hinting at 9 dp. It is the
                    // Canvas twin of the `Icons.Rounded.Adjust` the peek's approximate row already
                    // carries, so both surfaces say the same word about the same session.
                    // [UI-A-SAVED-ZONE-WEARS-ITS-DOUBT-TOO-001]
                    VehicleMarkerDoubt.APPROXIMATE -> {
                        drawCircle(
                            color = tagFill,
                            radius = BADGE_TARGET_RING_R * s,
                            center = badgeCenter,
                            style = Stroke(width = BADGE_TARGET_RING_U * s),
                        )
                        drawCircle(color = tagFill, radius = BADGE_TARGET_DOT_R * s, center = badgeCenter)
                    }
                }
            }
            // The zone disc — same grammar as the doubt above (filled disc, tag-fill ring), in the
            // ZONE's identity colour so it says WHICH zone family this is, neutral when private.
            //
            // ⛔ It does not share the corner with a doubt. The corner was modelled as exclusive on
            // purpose ([VehicleMarkerDoubt]) and that reasoning still holds: two discs on the same
            // 16-unit corner is one blob at map size. So the doubt keeps the right corner — it says
            // whether the pin is even real — and the zone takes the mirrored LEFT one when both are
            // present. Neither piece of information is ever dropped.
            // [MAP-A-PARKED-CAR-INSIDE-A-ZONE-WEARS-THE-ZONE-001]
            if (zoneMark != null) {
                val zoneCenter = Offset(zoneBadgeCx * s, BADGE_CY * s)
                drawCircle(color = zoneDiscColor, radius = BADGE_R * s, center = zoneCenter)
                drawCircle(
                    color = tagFill,
                    radius = BADGE_R * s,
                    center = zoneCenter,
                    style = Stroke(width = BADGE_BORDER_U * s),
                )
                if (zoneMark.isPrivate) {
                    // The padlock rides the zone disc's own bottom corner — a badge on a badge, the
                    // only place left that is neither the car nor the ground point.
                    val lockCenter = Offset(
                        (zoneBadgeCx + BADGE_LOCK_DX) * s,
                        (BADGE_CY + BADGE_LOCK_DY) * s,
                    )
                    drawCircle(color = zoneDiscColor, radius = BADGE_LOCK_R * s, center = lockCenter)
                    drawCircle(
                        color = tagFill,
                        radius = BADGE_LOCK_R * s,
                        center = lockCenter,
                        style = Stroke(width = BADGE_BORDER_U * 0.7f * s),
                    )
                }
            }
            } // translate — the badge's breathing room
        }
        // 4b · the zone glyph (and its padlock) knocked out of the discs the Canvas just drew. An
        // ImageVector cannot be drawn into a Canvas, so they ride as overlaid Icons the same way
        // the carbody does. [MAP-A-PARKED-CAR-INSIDE-A-ZONE-WEARS-THE-ZONE-001]
        if (zoneMark != null) {
            Icon(
                imageVector = zoneMark.icon,
                contentDescription = null,
                tint = tagFill,
                modifier = Modifier
                    .offset(
                        x = u * (zoneBadgeCx - BADGE_ZONE_GLYPH_U / 2f + TAG_PAD_X),
                        y = u * (BADGE_CY - BADGE_ZONE_GLYPH_U / 2f + TAG_PAD_TOP),
                    )
                    .size(u * BADGE_ZONE_GLYPH_U),
            )
            if (zoneMark.isPrivate) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = tagFill,
                    modifier = Modifier
                        .offset(
                            x = u * (zoneBadgeCx + BADGE_LOCK_DX - BADGE_LOCK_GLYPH_U / 2f + TAG_PAD_X),
                            y = u * (BADGE_CY + BADGE_LOCK_DY - BADGE_LOCK_GLYPH_U / 2f + TAG_PAD_TOP),
                        )
                        .size(u * BADGE_LOCK_GLYPH_U),
                )
            }
        }
        // 4 · the isometric carbody, centred in the tag interior (ContentScale.Fit); faded with the
        // tag. Offsets are design-viewBox coords, so they shift by the same pads the Canvas does.
        VehicleIcon(
            carbody = carbodyType,
            size = sizeCategory,
            tint = Color.Unspecified,
            color = color,
            modifier = Modifier
                .offset(x = u * (16f + TAG_PAD_X), y = u * (13f + TAG_PAD_TOP))
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
// Breathing room around the design viewBox so the corner badge's disc (centre (90,12), r 16 + its
// ring) is never clipped by the Canvas bounds: it needs 3.5u past the right edge and 5.5u above the
// top. Symmetric sides keep the bottom-centre anchor centred; no bottom pad keeps the dome base ON
// the bitmap's bottom edge (the ground point). [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001]
private const val TAG_PAD_X   = 5f
private const val TAG_PAD_TOP = 7f
private const val TAG_CANVAS_VB_W = TAG_VB_W + 2f * TAG_PAD_X
private const val TAG_CANVAS_VB_H = TAG_VB_H + TAG_PAD_TOP
private const val TAG_BORDER_U      = 3.6f  // default state-colour border — matches the selected weight, a touch thicker (viewBox units)
private const val TAG_SEL_BORDER_U  = 4f    // selected onSurface border
private const val TAG_DOT_R         = 4.5f  // position-dot radius (viewBox units)
private const val TAG_DOT_CY        = 80.5f // position-dot centre Y (viewBox units) — the visual ground point
private const val TAG_SHADOW_ALPHA  = 0.18f
private const val TAG_DARK_LUMINANCE = 0.5f // theme surface below this ⇒ dark ⇒ ink fill, else white

// [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] The unconfirmed dress (viewBox units).
private const val UNCONFIRMED_TAG_ALPHA = 0.72f // the TAG's fill only — never the carbody
private const val UNCONFIRMED_DASH_ON   = 7f
private const val UNCONFIRMED_DASH_OFF  = 5f
private const val BADGE_CX          = 90f  // tag spans x 10..94 — the disc rides the top-right corner
private const val BADGE_CY          = 12f  // tag spans y 6..72
private const val BADGE_R           = 16f
private const val BADGE_BORDER_U    = 3f
private const val BADGE_GLYPH_U     = 22f  // `?` cap size, in the same viewBox units

// The zone disc mirrors the doubt's corner across the tag (x 10..94), so both sit on the top edge
// at the same height. [MAP-A-PARKED-CAR-INSIDE-A-ZONE-WEARS-THE-ZONE-001]
private const val BADGE_ZONE_CX_LEFT  = 14f
private const val BADGE_ZONE_GLYPH_U  = 18f   // a preset glyph reads smaller than a `?` cap
private const val BADGE_LOCK_R        = 9f
private const val BADGE_LOCK_DX       = 11f
private const val BADGE_LOCK_DY       = 12f
private const val BADGE_LOCK_GLYPH_U  = 10f

// [UI-A-SAVED-ZONE-WEARS-ITS-DOUBT-TOO-001] The target ⊙ of a saved approximate zone, knocked out
// of the same BADGE_R disc the `?` uses (viewBox units) — ring + centre, like Icons.Rounded.Adjust.
private const val BADGE_TARGET_RING_R = 8.5f
private const val BADGE_TARGET_RING_U = 2.6f
private const val BADGE_TARGET_DOT_R  = 3.2f

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
    carbody: com.rndeveloper.paparcar.domain.model.CarbodyType?,
    size: VehicleSize?,
    headingDegrees: Float,
    modifier: Modifier = Modifier,
    color: com.rndeveloper.paparcar.domain.model.VehicleColor? = null,
    /** The halo says HOW this car is being watched — green = assisted, blue = Bluetooth. Defaults to
     *  the movement blue for callers with no watch to declare (and for an unwatched car, whose grey
     *  would vanish over map tiles). [UI-A-TRACKED-TRIP-WEARS-ITS-WATCH-001] */
    haloColor: Color = PapColor.live,
) {
    Box(modifier.size(LOC_ACTIVE_DIAM), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(color = haloColor, alpha = LOC_HALO_ALPHA, radius = this.size.minDimension / 2f)
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

// [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001] Both grew together (was 54/38): the car
// reads too small against the road at speed, but bumping only the glyph would leave the halo a rim.
// The ratio is what makes it read as a puck and not as a sticker — keep it near 0.73.
private val LOC_ACTIVE_DIAM = 60.dp
private val LOC_ACTIVE_CAR  = 44.dp
private const val LOC_HALO_ALPHA = 0.16f

// ─── Marker 1d — Trip origin dot (DepartureDotMarker) ────────────────────────

/**
 * The trip's origin: a small blue dot with a white halo, marking exactly where the departing vehicle
 * left from — the point the breadcrumb trail starts at. Center-anchored on the map so the dot sits on
 * the coordinate. Replaces the old faded-car departure badge: the origin is now just the clear dot.
 * Also caps a STORED route's final vertex at the parked car — both ends of the line deliberately
 * share this one visual language. [DEPART-CONSISTENCY-001] [TRIP-TRAIL-001] [ROUTE-END-AT-CAR-001]
 */
@Composable
fun DepartureDotMarker(
    modifier: Modifier = Modifier,
    /** Where the trip started belongs to the trip, so it wears the same watch colour as its trail.
     *  [UI-A-TRACKED-TRIP-WEARS-ITS-WATCH-001] */
    color: Color = PapColor.live,
) {
    Box(modifier.size(DEPARTURE_DOT_BOX), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension / 2f
            drawCircle(color = Color.White, radius = r)
            drawCircle(color = color, radius = r * DEPARTURE_DOT_INNER_SCALE)
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
    // La familia de MARCA sale del tema, no de una llamada suelta: si la app cambia de
    // tipografía, las etiquetas del mapa cambian con ella. [UI-TYPE-ONE-VOICE-REACHES-MATERIAL-001]
    val outfit = PaparcarType.current.cardTitle.fontFamily
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

// ─── MyVehicle marker — legacy fallback (ParkingHistoryDetailScreen) ──────────────

/**
 * Fallback teardrop marker used by screens that have a parking location but no
 * [com.rndeveloper.paparcar.domain.model.ParkedVehicleSummary] context (e.g.
 * ParkingHistoryDetailScreen). Home screen uses [VehicleBadgeMarker] instead.
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
// Bolt-green design: brand-filled teardrop + white 3px halo, the given-spot glyph (hand + heart),
// a freshness ring whose arc length encodes the reliability tier, and a tier-coloured
// announcement badge. When people are en route the pin flips to the blue "reserved"
// state with a dashed orbit + a people pill carrying the count. [BOLT-MARKERS-001]
//
// viewBox 72×92, anchor = pin tip ≈ (36, 90) → bottom-centre. All geometry is
// drawn in viewBox units scaled by `s = width / 72`.

/** Bolt-green spot palette — fixed brand tones (independent of [MaterialTheme]). */
// Brand tier colours — kept fixed across themes (neutrals invert via `paper`/`ink`). [BOLT-MARKERS-001]
// The en-route tone is NOT here: it reads the shared [PapColor.live], which is now what that blue
// means — movement that is NOT yours. It used to be justified as "the same semantic as the driving
// car and the trip trail"; that equivalence is gone. Your own trip wears its car's watch colour,
// and this pin stays blue precisely because the person driving toward this spot is a stranger.
// [UI-COLOR-DOCTRINE-001] [UI-A-TRACKED-TRIP-WEARS-ITS-WATCH-001]
private object SpotPalette {
    // `Green` was #009F5E — the EXACT value the active-vehicle marker used two hundred lines up.
    // A stranger's free spot and your own watched car were the same pixel on the same layer,
    // simultaneously, always. [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]
    val Green       = PapSpotFreshPuck // libre · fresca
    // One ramp, one value: the light-theme ramp legs ARE the map tones by design ("una sola rampa,
    // los mismos tres tonos en el sheet y sobre las teselas"). These two used to duplicate the hex
    // as literals, which the one-hex-one-story rule cannot see — it only audits declared tokens.
    // [UI-SEVEN-STRAYS-FROM-THE-CANON-001]
    val Amber       = PapSpotCoolingLight // libre · enfriándose
    val Red         = PapSpotExpiringLight // libre · caduca ya
    val Paper       = Color(0xFFFFFFFF) // fixed white — ring / community glyph / badge discs
    val Ink         = Color(0xFF0E1A2E) // fixed dark — contact shadow / en-route pill
}

/** Per-tier visual recipe: pin colour and freshness-ring fraction. */
private data class SpotTierVisual(
    val color: Color,
    /** 0..1 arc length of the freshness ring; null = no ring (manual). */
    val ringFraction: Float?,
)

// Ring length is static per tier (no live TTL timestamp wired to the marker
// layer yet — animated countdown deferred). Fractions mirror the reference
// SVGs (full / ~½ / minimal). [BOLT-MARKERS-001]
//
private fun SpotFreshness.tierVisual(isManual: Boolean = false): SpotTierVisual = when (this) {
    SpotFreshness.FRESH   -> SpotTierVisual(SpotPalette.Green, ringFraction = 1f)
    SpotFreshness.RECENT -> SpotTierVisual(SpotPalette.Amber, ringFraction = 0.5f)
    SpotFreshness.STALE  -> SpotTierVisual(SpotPalette.Red, ringFraction = 0.16f)
}

/**
 * Free-spot marker — Bolt-green teardrop pin. Colour + freshness ring + badge
 * encode the reliability tier so the on-map marker matches the peek modal.
 * [BOLT-MARKERS-001]
 *
 * @param reliability freshness tier driving colour/ring. Defaults to [SpotFreshness.FRESH].
 * @param selected when true an extra white outer outline marks the selection
 *   (the live pulse ring is drawn separately by [PaparcarMapView]).
 * @param enRouteCount when > 0 the pin renders the blue "reserved · en route"
 *   state with a people pill carrying the count, overriding the tier colour.
 * @param isManual eyewitness report. Kept for the bitmap cache key; the pin itself no longer draws
 *   provenance (the old person badge is gone — [tierVisual] ignores it). [UI-COLOR-DOCTRINE-001 F5]
 */
@Composable
fun FreeSpotMarker(
    modifier: Modifier = Modifier,
    reliability: SpotFreshness = SpotFreshness.FRESH,
    selected: Boolean = false,
    enRouteCount: Int = 0,
    isManual: Boolean = false,
    /** [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] Area spot: the freeing session only knew its rest
     *  to a radius, so the ground point is a CENTRE, not a point — the solid dot becomes the
     *  target ⊙, the same word the saved approximate parking's corner badge already speaks
     *  [UI-A-SAVED-ZONE-WEARS-ITS-DOUBT-TOO-001]. */
    approximate: Boolean = false,
) {
    // La familia de MARCA sale del tema, no de una llamada suelta: si la app cambia de
    // tipografía, las etiquetas del mapa cambian con ella. [UI-TYPE-ONE-VOICE-REACHES-MATERIAL-001]
    val outfit = PaparcarType.current.cardTitle.fontFamily
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
    // white ring / community glyph / TTL ring / badge discs are always white, the contact shadow + en-route pill
    // always dark. Only the freshness tier colour (green/amber/red) carries meaning. [MAP-ICONS-V2]
    val paper = SpotPalette.Paper
    val ink = SpotPalette.Ink

    Box(modifier.size(width = w, height = h)) {
        Canvas(Modifier.matchParentSize()) {
            val s = size.width / SPOT_VIEWBOX_W
            if (enRouteCount > 0) {
                drawEnRoutePin(s, enRouteCount, selected, measurer, countStyle, paper, ink)
            } else {
                drawTierPin(s, reliability.tierVisual(isManual), selected, paper, ink)
            }
            if (approximate) {
                val targetColor = if (enRouteCount > 0) PapColor.live else reliability.tierVisual(isManual).color
                drawApproximateSpotDot(s, targetColor, paper)
            }
        }
        CommunitySpotOverlay(
            showAnnouncement = enRouteCount == 0,
            badgeColor = if (enRouteCount > 0) PapColor.live else reliability.tierVisual(isManual).color,
        )
    }
}

/**
 * [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] The ground point of an AREA spot: the exact dot is
 * knocked out and replaced by the target ⊙ (ring + centre) — "this pin is a centre, not a point".
 * Same two-circle language as the saved approximate parking's corner badge, so both layers of the
 * map say the same word about the same kind of doubt. [UI-A-SAVED-ZONE-WEARS-ITS-DOUBT-TOO-001]
 */
private fun DrawScope.drawApproximateSpotDot(s: Float, color: Color, paper: Color) {
    val center = Offset(36f * s, SPOT_DOT_CY * s)
    drawCircle(paper, radius = SPOT_TARGET_R * s, center = center)
    drawCircle(color, radius = SPOT_TARGET_R * s, center = center, style = Stroke(width = SPOT_TARGET_STROKE_U * s))
    drawCircle(color, radius = SPOT_TARGET_CENTER_R * s, center = center)
}

/**
 * Tail-less community spot puck for use inside list containers (no pointing tip). The
 * same puck drawing as [FreeSpotMarker], so the list badge and the map marker read identically —
 * colour + TTL ring + badge encode the reliability tier. [HOME-PUCK-001]
 *
 * @param reliability tier driving colour/ring/badge.
 * @param enRouteCount when > 0 the puck turns the blue "reserved · en route" colour to mirror the
 *   map marker (ring/badge dropped; the row shows a separate en-route indicator).
 */
@Composable
fun SpotPuckIcon(
    reliability: SpotFreshness,
    modifier: Modifier = Modifier,
    enRouteCount: Int = 0,
    selected: Boolean = false,
    isManual: Boolean = false,
) {
    val paper = SpotPalette.Paper
    val ink = SpotPalette.Ink
    val visual = if (enRouteCount > 0) {
        SpotTierVisual(PapColor.live, ringFraction = null)
    } else {
        reliability.tierVisual(isManual)
    }
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val s = size.minDimension / SPOT_VIEWBOX_W
            // Centre the puck circle (viewBox centre y = 34) vertically in the square slot — the marker
            // viewBox reserves the lower rows for the tail we're omitting here.
            translate(top = size.height / 2f - SPOT_PUCK_CENTER_Y * s) {
                drawSpotPuck(s, visual, selected, paper, ink)
            }
        }
        CommunitySpotOverlay(
            showAnnouncement = enRouteCount == 0,
            badgeColor = if (enRouteCount > 0) PapColor.live else visual.color,
        )
    }
}

// ── Puck geometry (viewBox 72×82 units) ──────────────────────────────────────
// Spot marker = round "puck" (centre 36,34 r29) carrying the given-spot glyph, a per-tier
// TTL ring and an announcement badge, over a separated position dot (the exact ground point).
// Anchor = bottom-centre.
// [MAP-ICONS-V2]

/** La sombra es la del PUNTO, así que viaja con él: su tapa cae [SHADOW_TOP_UNDER_DOT] por encima
 *  del centro del punto, esté ese punto donde esté. Antes era el literal `71,4`, que sólo valía
 *  para el único [SPOT_DOT_CY] que había. [MAP-THE-FAMILY-LINE-IS-THE-ONE-YOU-CAN-SEE-001] */
private fun DrawScope.drawSpotShadow(s: Float, ink: Color, centerX: Float = 36f, dotCy: Float = SPOT_DOT_CY) {
    drawOval(
        color = ink.copy(alpha = 0.12f),
        topLeft = Offset((centerX - 4.5f) * s, (dotCy + SHADOW_TOP_UNDER_DOT) * s),
        size = Size(9f * s, 3.2f * s),
    )
}

/** Separated position dot — the exact ground point, in the marker's own colour (no border). [MAP-ICONS-V2] */
private fun DrawScope.drawSpotDot(s: Float, color: Color, centerX: Float = 36f, dotCy: Float = SPOT_DOT_CY) {
    drawCircle(color, radius = SPOT_DOT_R * s, center = Offset(centerX * s, dotCy * s))
}

/** Desfase de la tapa de la sombra respecto al centro del punto — el que tenía por construcción
 *  (71,4 − 72,5), conservado para que la plaza no se mueva. */
private const val SHADOW_TOP_UNDER_DOT = -1.1f

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

private fun DrawScope.drawBadgeDisc(s: Float, tier: Color, discColor: Color) {
    drawCircle(discColor, 13f * s, Offset(56f * s, 15f * s))
    drawCircle(tier, 13f * s, Offset(56f * s, 15f * s), style = Stroke(2f * s))
}

/**
 * The round puck WITHOUT the tail — body + TTL ring + announcement badge. Shared by the on-map
 * [FreeSpotMarker] (which adds the shadow + separated position dot around it) and the tail-less
 * [SpotPuckIcon] used inside list containers. [HOME-PUCK-001]
 */
private fun DrawScope.drawSpotPuck(s: Float, v: SpotTierVisual, selected: Boolean, paper: Color, ink: Color) {
    drawPuckBody(s, v.color, selected, paper, ink)
    v.ringFraction?.let { drawTtlRing(s, it, paper) }
    drawBadgeDisc(s, v.color, paper)
}

@Composable
private fun CommunitySpotOverlay(
    showAnnouncement: Boolean,
    badgeColor: Color,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = maxWidth.value / SPOT_VIEWBOX_W
        // [SPOT-ICON-IS-THE-GIVEN-SPOT-HAND-HEART-001] The hand + heart fills more of its box than
        // the old person did, so it goes one size down (26 vs 28) — still centred on the puck
        // (36,34), clear of the announcement badge (centre 56,15 r13) and inside the TTL ring.
        Icon(
            imageVector = PaparcarIcons.CommunitySpot,
            contentDescription = null,
            tint = SpotPalette.Paper,
            modifier = Modifier
                .offset(x = (COMMUNITY_GLYPH_X * scale).dp, y = (COMMUNITY_GLYPH_Y * scale).dp)
                .size((COMMUNITY_GLYPH_SIZE * scale).dp),
        )
        if (showAnnouncement) {
            Icon(
                imageVector = Icons.Rounded.Campaign,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier
                    .offset(x = (47f * scale).dp, y = (6f * scale).dp)
                    .size((18f * scale).dp),
            )
        }
    }
}

// Given-spot glyph box inside the puck, in viewBox units. [SPOT-ICON-IS-THE-GIVEN-SPOT-HAND-HEART-001]
private const val COMMUNITY_GLYPH_X = 23f
private const val COMMUNITY_GLYPH_Y = 21f
private const val COMMUNITY_GLYPH_SIZE = 26f

private fun DrawScope.drawTierPin(s: Float, v: SpotTierVisual, selected: Boolean, paper: Color, ink: Color) {
    drawSpotShadow(s, ink)
    drawSpotDot(s, v.color)
    drawSpotPuck(s, v, selected, paper, ink)
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
    paper: Color,
    ink: Color,
) {
    drawSpotShadow(s, ink)
    drawSpotDot(s, PapColor.live)
    // Dashed outer orbit (centre 36,34 r29)
    drawCircle(
        PapColor.live,
        radius = 29f * s,
        center = Offset(36f * s, 34f * s),
        style = Stroke(2.4f * s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * s, 5f * s))),
    )
    // Inner blue puck (r25) inside the dashed orbit
    drawPuckBody(s, PapColor.live, selected, paper, ink, radius = 25f)
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
/**
 * **La línea sobre la que se apoya el CUERPO de cualquier marcador de la familia del puck** — el
 * borde inferior del disco (centro 34 + radio 29). El punto de posición se coloca debajo, separado
 * por [MARKER_DOT_GAP]. [MAP-EVERY-MARKER-SITS-THE-SAME-HEIGHT-OVER-ITS-DOT-001]
 *
 * Esa distancia es un RASGO de la familia, no un número de cada marcador: el puck de plaza y el de
 * parking la tenían por construcción (los dos dibujan el mismo disco) y el tag del coche la tiene
 * equivalente en su propio viewBox (4 de 104 de ancho, frente a 3,5 de 72). La cápsula de la
 * gasolinera era la única que colgaba del borde SUPERIOR del marcador: con la altura mínima de 48
 * unidades quedaba a ~18 del punto, cinco veces la separación de sus hermanos, y por eso se leía
 * como un globo suelto encima de un punto en vez de como un marcador.
 */
internal const val MARKER_BODY_BOTTOM_Y = 63f

internal const val SPOT_DOT_R = 4.5f // position-dot radius (viewBox units)

/** Grosor del contorno de papel del puck de plaza, en unidades del viewBox. Vivía como literal
 *  `3.2f` dentro de `drawPuckBody`, que es donde dejó de poder participar en la geometría de la
 *  familia. [MAP-THE-FAMILY-LINE-IS-THE-ONE-YOU-CAN-SEE-001] */
internal const val PUCK_PAPER_STROKE_U = 3.2f

/**
 * **Cuánto sobresale la silueta de un cuerpo por DEBAJO de [MARKER_BODY_BOTTOM_Y]**, por marcador.
 * [MAP-THE-FAMILY-LINE-IS-THE-ONE-YOU-CAN-SEE-001]
 *
 * El puck de plaza lleva su contorno de papel CENTRADO en el radio 29, así que media pluma —1,6 u—
 * cae fuera del relleno: su borde inferior visible no está en 63, está en 64,6. La cápsula y el
 * disco no tienen contorno en reposo, así que el suyo sí está en 63.
 *
 * ⛔ Esto no es un ajuste por marcador: es que la línea de la familia se había escrito sobre el
 * borde del RELLENO, que nadie ve. Quien añada un cuerpo nuevo declara aquí lo que sobresale, y la
 * separación visible le sale sola.
 */
internal const val SPOT_BODY_OVERSHOOT_U = PUCK_PAPER_STROKE_U / 2f

/** El disco de parking y la cápsula de gasolinera no sobresalen: sin contorno en reposo, su borde
 *  visible ES la línea de la familia. Existe con nombre para que se lea como una DECISIÓN —el
 *  cuerpo es el color, no hace falta anillo— y no como un cero olvidado.
 *  [MAP-A-DISC-THAT-CARRIES-NO-DATA-NEEDS-NO-RING-001] */
internal const val NEARBY_BODY_OVERSHOOT_U = 0f

/**
 * **El AIRE VISIBLE entre el cuerpo de un marcador y su punto de posición**, en unidades del
 * viewBox de la familia del puck (72 × 82).
 * [MAP-EVERY-MARKER-SITS-THE-SAME-HEIGHT-OVER-ITS-DOT-001]
 * [MAP-THE-FAMILY-LINE-IS-THE-ONE-YOU-CAN-SEE-001]
 *
 * Era 3,5 por construcción —nadie lo eligió: salía de restar el borde del disco (63) al borde
 * superior del punto (66,5)— y a ese valor el punto se lee **pegado** al puck, sobre todo en la
 * plaza, que es el marcador que más se mira. Subió a 5 y el marcador de plaza quedó como el user
 * lo aprobó.
 *
 * ⛔ **Pero ese 5 se medía contra el borde del RELLENO, y el puck de plaza sobresale 1,6 u por
 * debajo con su contorno de papel** ([SPOT_BODY_OVERSHOOT_U]). O sea: la plaza enseñaba 3,4 u de
 * aire y el disco de parking, que no lleva contorno, enseñaba los 5 enteros —un **47 % más**— con
 * la misma constante. El user lo vio en mano («el parking parece llevar algo más de aire que el
 * resto») y tenía razón: el rasgo de familia estaba escrito sobre una línea que nadie ve.
 *
 * Así que esta constante pasa a ser lo que siempre dijo ser —**el aire que se VE**— y vale 3,4: lo
 * que la plaza ya enseñaba y el user aprobó. El puck de plaza **no se mueve ni un píxel**; el disco
 * y la cápsula suben su punto 1,6 u (≈ 1,0 dp a 46 dp de ancho) hasta enseñar el mismo aire.
 *
 * ⛔ **El que se mueve es el PUNTO, no el cuerpo.** El cuerpo se apoya en [MARKER_BODY_BOTTOM_Y] y
 * ahí se queda; bajar el punto lo acerca además al ancla real del marcador —(0.5, 1.0), el borde
 * inferior del viewBox—, así que la separación se gana sin alejar el punto de la coordenada que
 * marca. Al revés habría sido subir el cuerpo y dejar el marcador más lejos de su sitio.
 *
 * ⛔ **El tag del coche NO entra**, y no es un olvido: tiene otro viewBox (104 × 88) y es otra
 * familia (tarjeta + cúpula, no puck + punto). Su separación de 4 unidades sobre 104 de ancho ya es
 * proporcionalmente MENOR que la de sus primos, y el user no se quejó de él. Tocarlo aquí sería
 * cambiar un marcador que nadie ha dicho que esté mal.
 */
internal const val MARKER_DOT_GAP = 3.4f

/** Centro Y del punto de posición, **derivado** del cuerpo, de lo que su silueta sobresale y del
 *  aire: nadie vuelve a escribir un literal aquí, que es como los 3,5 de antes acabaron sin autor.
 *  [MAP-EVERY-MARKER-SITS-THE-SAME-HEIGHT-OVER-ITS-DOT-001] */
internal const val SPOT_DOT_CY =
    MARKER_BODY_BOTTOM_Y + SPOT_BODY_OVERSHOOT_U + MARKER_DOT_GAP + SPOT_DOT_R

/** Centro Y del punto de la cápsula y del disco: el MISMO aire visible, sobre un cuerpo que no
 *  sobresale. [MAP-THE-FAMILY-LINE-IS-THE-ONE-YOU-CAN-SEE-001] */
internal const val NEARBY_DOT_CY =
    MARKER_BODY_BOTTOM_Y + NEARBY_BODY_OVERSHOOT_U + MARKER_DOT_GAP + SPOT_DOT_R
// [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] Target ⊙ of an area spot's ground point (viewBox units):
// outer ring sized to knock out the solid dot with clear air, centre dot well under the ring.
private const val SPOT_TARGET_R = 6.5f
private const val SPOT_TARGET_STROKE_U = 2.2f
private const val SPOT_TARGET_CENTER_R = 2f
private const val SPOT_PUCK_CENTER_Y = 34f // puck circle centre Y (viewBox units) — used to centre the tail-less icon

// ─── Marker 3 — Zone (ZoneMarker) ────────────────────────────────────────────

/**
 * Rótulo de ÁREA de una [com.rndeveloper.paparcar.domain.model.Zone] guardada.
 *
 * ### Por qué no es un marcador
 * El mapa tiene dos clases de objeto y cada una tiene un vestido [UI-A-ZONE-IS-AN-AREA-NOT-A-PIN-001]:
 *
 * | clase | qué es | ancla | suelo | caja |
 * |---|---|---|---|---|
 * | **puntual** | ocupa una coordenada | `(0.5, 1.0)` | punto + sombra | sí |
 * | **ámbito** | califica una región | `(0.5, 0.5)` | ninguno | **ninguna** |
 *
 * La zona es lo segundo, y hasta este ticket vestía de lo primero: una cápsula opaca con borde,
 * igual que la gasolinera, pero sin punto de suelo ni sombra. La misma forma decía dos cosas —*un
 * sitio en este punto* y *el centro de un área*— en la misma capa y a la vez, y la zona flotaba
 * sobre una coordenada que no ocupaba.
 *
 * Ahora es lo que la cartografía hace con los barrios y los parques: glifo + nombre **sin caja**,
 * legibles por un halo de papel. Sin fondo y sin borde no puede competir con un puck porque no es
 * del mismo mundo. El sujeto pasa a ser el anillo de radio que dibuja [PaparcarMapView] como
 * círculo nativo, que es quien lleva el color y quien ya decía el área [ZONE-AREA-001].
 *
 * ### El halo, y por qué se pinta trece veces
 * Un texto puede llevar contorno (`TextStyle.drawStyle`), pero un [Icon] no: un `ImageVector` no se
 * puede trazar. Dibujar el rótulo ENTERO en color papel bajo sus vecinos y el de verdad encima le
 * da a glifo y nombre el MISMO casing, que es lo que hace que se lean como un solo objeto dibujado
 * por el mapa y no como dos cosas pegadas. Son trece composiciones de una fila diminuta dentro de
 * un bitmap que kmpmaps cachea por `contentId`: se pagan una vez, no por frame.
 *
 * Los vecinos van repartidos por una CIRCUNFERENCIA ([ZONE_HALO_OFFSETS]), no por las esquinas de
 * un cuadrado: un casing sólo mide lo mismo en todas las direcciones si el vecindario es isótropo,
 * y con las diagonales a √2 el glifo perdía la punta. [UI-A-HALO-MUST-FOLLOW-THE-GLYPH-NOT-ITS-BOX-001]
 *
 * @param name nombre de la zona. Se pinta sólo si [showName]; vacío ⇒ sólo el glifo.
 * @param icon icono preset de la zona (resuelto desde `zone.iconKey`).
 * @param isPrivate zona privada ⇒ tinte NEUTRO + candado pegado al glifo. Privado es una categoría,
 *   y una categoría es trabajo del glifo (tertiary está retirado). [UI-COLOR-DOCTRINE-001 F6]
 *   Sin valor por defecto: el `= false` que llevaba antes es cómo dos superficies se quedaron en
 *   verde de marca durante meses sin que nadie escribiera una línea equivocada.
 *   [UI-A-PRIVATE-ZONE-IS-PRIVATE-ON-EVERY-SURFACE-001]
 * @param showName `false` cuando el anillo es demasiado pequeño en pantalla para que su nombre
 *   titule algo — lo decide `zoneLabelShowsName`, medible y con tests, no el composable.
 */
@Composable
fun ZoneMarker(
    name: String,
    icon: ImageVector,
    isPrivate: Boolean,
    showName: Boolean,
    modifier: Modifier = Modifier,
) {
    val zoneColor = zoneIdentityColor(isPrivate)
    val paper = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface
    // La familia de MARCA sale del tema, no de una llamada suelta: si la app cambia de
    // tipografía, las etiquetas del mapa cambian con ella. [UI-TYPE-ONE-VOICE-REACHES-MATERIAL-001]
    val outfit = PaparcarType.current.cardTitle.fontFamily
    val label = if (showName) name.trim() else ""

    // Marcador anclado al centro (0.5, 0.5 en PaparcarMapView): el rótulo se centra en su propio
    // bitmap y cae en el centro del círculo. El padding es el hueco que necesita el halo para no
    // recortarse contra el borde del bitmap — Canvas y Box clipan igual de rápido.
    Box(modifier = modifier.padding(ZONE_LABEL_HALO)) {
        ZONE_HALO_OFFSETS.forEach { (dx, dy) ->
            ZoneLabelRow(
                label = label,
                icon = icon,
                isPrivate = isPrivate,
                glyphColor = paper,
                textColor = paper,
                family = outfit,
                modifier = Modifier.offset(x = ZONE_LABEL_HALO * dx, y = ZONE_LABEL_HALO * dy),
            )
        }
        ZoneLabelRow(
            label = label,
            icon = icon,
            isPrivate = isPrivate,
            glyphColor = zoneColor,
            // El NOMBRE no se tiñe: el glifo lleva la identidad y el nombre se escribe.
            // [UI-COLOR-DOCTRINE-001]
            textColor = ink,
            family = outfit,
        )
    }
}

/** Una pasada del rótulo — la del halo o la de verdad. Misma geometría en las nueve. */
@Composable
private fun ZoneLabelRow(
    label: String,
    icon: ImageVector,
    isPrivate: Boolean,
    glyphColor: Color,
    textColor: Color,
    family: androidx.compose.ui.text.font.FontFamily?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = glyphColor,
            modifier = Modifier.size(ZONE_LABEL_ICON),
        )
        // El candado va PEGADO al glifo, no al final de una fila: sin cápsula no hay final donde
        // colgarlo, y privado califica a la zona, no al nombre.
        // [UI-A-PRIVATE-ZONE-IS-PRIVATE-ON-EVERY-SURFACE-001]
        if (isPrivate) {
            Spacer(Modifier.width(ZONE_LABEL_LOCK_GAP))
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = glyphColor,
                modifier = Modifier.size(ZONE_LABEL_LOCK),
            )
        }
        if (label.isNotEmpty()) {
            Spacer(Modifier.width(ZONE_LABEL_GAP))
            Text(
                text = label,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontFamily = family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = ZONE_LABEL_TEXT_SP,
                ),
                modifier = Modifier.widthIn(max = ZONE_LABEL_MAX_TEXT_W),
            )
        }
    }
}

private val ZONE_LABEL_ICON       = 16.dp
private val ZONE_LABEL_LOCK       = 11.dp
private val ZONE_LABEL_GAP        = 5.dp
private val ZONE_LABEL_LOCK_GAP   = 2.dp
private val ZONE_LABEL_MAX_TEXT_W = 96.dp
private val ZONE_LABEL_TEXT_SP    = 12.sp

// Grosor del halo de papel. Más de cuatro direcciones: en diagonal el hueco entre dos pasadas
// ortogonales deja pasar la tesela, y eso se ve como un borde sucio a tamaño de marcador.
private val ZONE_LABEL_HALO = 1.5.dp

// ⛔ Las direcciones van sobre una CIRCUNFERENCIA de radio 1, no sobre las esquinas de un cuadrado.
// Con `(±1,±1)` las cuatro diagonales caían a √2 (un 41% más lejos que las ortogonales), así que la
// unión de las copias no era el glifo engordado sino el glifo DILATADO POR UN CUADRADO — y dilatar
// una punta con un cuadrado la convierte en una barra plana. En el nombre no se veía (el texto no
// tiene puntas); en el glifo de la zona el pin perdía el pico y se leía como un marcador recortado.
// Radio constante = mismo casing en todas las direcciones, que es lo que este halo prometía, y
// ninguna copia se sale del `padding(ZONE_LABEL_HALO)` que le reserva el hueco.
// Doce y no ocho: sobre la circunferencia, ocho dejan huecos de 45° y el casing se ve poligonal a
// tamaño de marcador. Son doce composiciones de una fila diminuta dentro de un bitmap que kmpmaps
// cachea por `contentId`: se pagan una vez, no por frame.
// [UI-A-HALO-MUST-FOLLOW-THE-GLYPH-NOT-ITS-BOX-001]
private const val ZONE_HALO_DIRECTIONS = 12
private val ZONE_HALO_OFFSETS: List<Pair<Float, Float>> = List(ZONE_HALO_DIRECTIONS) { i ->
    val angle = 2.0 * PI * i / ZONE_HALO_DIRECTIONS
    cos(angle).toFloat() to sin(angle).toFloat()
}

// ─── Fuel-station layer ──────────────────────────────────────────────────────

/**
 * A fuel station on the map: capsule with our own pump glyph and, when the active car's fuel has a
 * price here, the price WRITTEN beside it. [FUEL-STATIONS-MAP-AND-PEEK-001]
 *
 * ⛔ The price is never coloured cheap/expensive — the green/amber/red ramp belongs exclusively to
 * spot freshness [UI-COLOR-DOCTRINE-001] — and an unpriced station shows the glyph alone, never a
 * zero. The shape itself is [NearbyCapsuleMarker], shared with the car park: what changes between
 * the two is the accent and what they have to say, never the grammar.
 */
@Composable
fun FuelStationMarker(
    priceText: String?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    /** WHICH fuel the price is — a figure with no subject makes the reader remember what their car
     *  burns. Muted beside the price; drawn only when there IS a price to qualify. */
    fuelLabel: String? = null,
) = NearbyCapsuleMarker(
    glyph = PaparcarIcons.Fuel,
    accent = nearbyMarkerGreen(),
    selected = selected,
    modifier = modifier,
    primaryText = priceText,
    secondaryText = fuelLabel?.replaceFirstChar { it.uppercase() },
)

/**
 * **La cápsula de un sitio CERCANO: glifo + lo que ese sitio tenga que decir.** Gasolinera y parking
 * público son el mismo marcador con distinto acento y distinta frase.
 * [MAP-A-CAR-PARK-MARKER-SAYS-WHAT-IT-COSTS-001] [FUEL-STATIONS-MAP-AND-PEEK-001]
 *
 * Existe porque la issue #46 pidió literalmente que el parking siguiera «la misma estructura de
 * diseño y contenido que gasolineras». Copiar la cápsula habría dado dos definiciones de la misma
 * forma, y la forma tiene seis números (corner, dos grosores de borde, dos alphas, tamaño del
 * glifo): dos copias es el día que una se ajusta y la otra no.
 *
 * ⛔ **El cuerpo se APOYA en [MARKER_BODY_BOTTOM_Y]**, no cuelga del borde de arriba. Colgando, la
 * distancia al punto de posición la decide el alto de la cápsula — cambia entre una línea y dos —
 * y quedaba a ~18 unidades del punto cuando el puck de plaza y el de parking dejan 3,5.
 * [MAP-EVERY-MARKER-SITS-THE-SAME-HEIGHT-OVER-ITS-DOT-001]
 */
@Composable
private fun NearbyCapsuleMarker(
    glyph: ImageVector,
    accent: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    /** El glifo va dentro de un DISCO relleno de [accent], en [onAccent], en vez de suelto y
     *  teñido. Lo lleva el parking, cuya identidad en la calle es la P sobre azul; la gasolinera no,
     *  porque su glifo suelto ya es el surtidor. [MAP-A-CAR-PARK-MARKER-SAYS-WHAT-IT-COSTS-001] */
    glyphOnDisc: Boolean = false,
    /** Lo que se dibuja SOBRE [accent] cuando [glyphOnDisc]. */
    onAccent: Color = Color.White,
    /** La línea que manda: el precio, la tarifa. Null = sólo el glifo, nunca un cero ni un hueco. */
    primaryText: String? = null,
    /** La que matiza a la de arriba (qué combustible). Sólo se dibuja si hay primera. */
    secondaryText: String? = null,
) {
    val paper = MaterialTheme.colorScheme.surface
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val labelFamily = PaparcarType.current.cardTitle.fontFamily
    val density = LocalDensity.current

    // Misma escala que el marcador de plaza (s = alto / 82) para que el punto y la sombra pesen
    // igual que los de sus vecinos. [FUEL-STATION-CLIPPING-FIX]
    val s = with(density) { SPOT_PIN_W.toPx() / SPOT_VIEWBOX_W }
    val markerH = with(density) { (SPOT_VIEWBOX_H * s).toDp() }
    // ⛔ El CUERPO se apoya en [MARKER_BODY_BOTTOM_Y], no cuelga de arriba.
    // [MAP-EVERY-MARKER-SITS-THE-SAME-HEIGHT-OVER-ITS-DOT-001]
    val bodyBottomPad = with(density) { ((SPOT_VIEWBOX_H - MARKER_BODY_BOTTOM_Y) * s).toDp() }

    // ⛔ `IntrinsicSize.Max`, NO `Min` (review de #59: «De pago no cabe en el marker»). El ancho
    // mínimo intrínseco de un texto es el de su palabra MÁS LARGA, así que con `maxLines = 1` la
    // cápsula se quedaba en «De» y tiraba «pago» fuera. No era que el marcador fuese pequeño: era
    // que se le pedía el ancho de una palabra para pintar una frase. `Max` es el ancho de la línea
    // entera, que es lo que una cápsula de una línea necesita.
    // [MAP-A-CAR-PARK-MARKER-SAYS-WHAT-IT-COSTS-001]
    Box(
        modifier = modifier
            .height(markerH)
            .width(IntrinsicSize.Max),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val canvasS = size.height / SPOT_VIEWBOX_H
            val centerX = (size.width / 2f) / canvasS
            // El punto de la cápsula/disco: mismo aire VISIBLE que la plaza sobre un cuerpo que
            // no sobresale. [MAP-THE-FAMILY-LINE-IS-THE-ONE-YOU-CAN-SEE-001]
            drawSpotShadow(canvasS, ink, centerX = centerX, dotCy = NEARBY_DOT_CY)
            drawSpotDot(canvasS, accent, centerX = centerX, dotCy = NEARBY_DOT_CY)
        }
        Row(
            modifier = Modifier
                // Alineado por ABAJO contra la línea del puck: una cápsula de una línea y otra de
                // dos se apoyan en el mismo sitio, y ninguna de las dos flota.
                // [MAP-EVERY-MARKER-SITS-THE-SAME-HEIGHT-OVER-ITS-DOT-001]
                .padding(start = 4.dp, end = 4.dp, bottom = bodyBottomPad) // + aire para que el borde no se recorte
                .clip(RoundedCornerShape(FUEL_LABEL_CORNER))
                .background(paper.copy(alpha = FUEL_LABEL_BG_ALPHA))
                .border(
                    width = if (selected) FUEL_LABEL_BORDER_SEL else FUEL_LABEL_BORDER,
                    color = accent.copy(alpha = if (selected) 1f else FUEL_LABEL_BORDER_ALPHA),
                    shape = RoundedCornerShape(FUEL_LABEL_CORNER),
                )
                .padding(horizontal = 10.dp, vertical = 3.dp)
                .heightIn(min = with(density) { (CAPSULE_BODY_MIN_U * s).toDp() }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyphOnDisc) {
                // El mismo disco azul con la P blanca que llevan la FILA y el peek, aquí dentro de
                // la cápsula (user, review de #59). Es la identidad de la calle —P sobre azul— y
                // ahora se dibuja igual en las tres superficies; lo que cambia entre ellas es el
                // tamaño, no el objeto. [MAP-A-CAR-PARK-MARKER-WEARS-ITS-OWN-BLUE-001]
                Box(
                    modifier = Modifier.size(CAPSULE_DISC_SIZE).clip(CircleShape).background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = glyph,
                        contentDescription = null,
                        tint = onAccent,
                        modifier = Modifier.size(CAPSULE_DISC_GLYPH_SIZE),
                    )
                }
            } else {
                Icon(
                    imageVector = glyph,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(FUEL_GLYPH_SIZE),
                )
            }
            if (primaryText != null) {
                Spacer(Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = primaryText,
                        color = ink,
                        maxLines = 1,
                        style = TextStyle(
                            fontFamily = labelFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            lineHeight = 12.sp,
                        ),
                    )
                    if (secondaryText != null) {
                        Text(
                            text = secondaryText,
                            color = muted,
                            maxLines = 1,
                            style = TextStyle(
                                fontFamily = labelFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.sp,
                                lineHeight = 10.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

// ─── Public-parkings layer ───────────────────────────────────────────────────

/**
 * The brand green the car park and fuel markers wear — glyph, outline and ground dot.
 * [MAP-NEARBY-MARKERS-GREEN-CONTRASTS-ON-LIGHT-MAP-001]
 *
 * It used to be `PapGreen` (#25F48C) in BOTH themes: the dark theme's neon, which reads well on dark
 * tiles and washes out on the light map, where a thin 55 % outline and a green glyph on paper nearly
 * vanished. [PapColor.actionText] is the SAME brand green resolved per theme — the neon on a dark
 * surface, the text leg `PapGreenTextLight` (#237A46, 5.3:1 on white) on a light one — and it is
 * exactly the tint the car park and fuel ROWS already use, so a pin and its row stay one colour.
 *
 * Safe for the marker bitmap cache: both content ids already carry the theme key, so a theme change
 * re-renders them instead of reusing the other theme's green.
 */
@Composable
private fun nearbyMarkerGreen(): Color = PapColor.actionText

/**
 * A public car park on the map — **the nearby capsule, wearing the car park's blue and saying what
 * it costs**. [MAP-A-CAR-PARK-MARKER-SAYS-WHAT-IT-COSTS-001] [PARKINGS-ON-THE-MAP-001]
 *
 * La misma [NearbyCapsuleMarker] que la gasolinera, y a propósito: las dos son SITIOS a los que se
 * conduce —contexto, no producto—, así que se dibujan con la misma gramática y lo que las separa es
 * el acento y lo que tienen que decir. Lo que decía antes era sólo «P»: un disco relleno no puede
 * llevar una frase, y la tarifa es justo lo que decide entre dos parkings a la misma distancia.
 *
 * ⛔ **El azul es el acento, no el relleno.** La P-sobre-azul de la calle sigue siendo la identidad
 * [MAP-A-CAR-PARK-MARKER-WEARS-ITS-OWN-BLUE-001] y sobrevive entera en la FILA y en el peek, que es
 * donde hay sitio para un tile. Aquí el azul pasa al glifo, al borde y al punto de posición, igual
 * que el verde de la gasolinera: dos cuerpos macizos de colores distintos uno al lado del otro
 * compiten con el producto (las plazas), y ninguno de los dos es el producto.
 *
 * ⛔ **Y nunca una tarifa inventada**: `FeeInfo.UNKNOWN` —el 71 % de los parkings que nadie ha
 * tarifado— dibuja el glifo solo. Un conductor al que le dijimos «gratuito» y se encontró una
 * barrera fue engañado por nosotros, no por OpenStreetMap.
 */
@Composable
fun PublicParkingMarker(
    selected: Boolean,
    modifier: Modifier = Modifier,
    /** «De pago» / «Gratuito», ya traducido por quien sí puede leer recursos. Null cuando la tarifa
     *  es desconocida — y entonces no hay cápsula que dibujar, sólo el disco. */
    feeLabel: String? = null,
) = if (feeLabel != null) {
    NearbyCapsuleMarker(
        glyph = PaparcarIcons.CarParkMark,
        accent = PapColor.carPark,
        selected = selected,
        modifier = modifier,
        glyphOnDisc = true,
        onAccent = PapColor.onCarPark,
        primaryText = feeLabel,
    )
} else {
    // ⛔ **Sin tarifa NO hay cápsula** (user, review de #59). Una cápsula es un papel con contorno
    // que existe para SOSTENER una frase; sin tarifa no hay frase, así que lo que quedaba era un
    // disco azul metido dentro de una caja blanca vacía — dos cuerpos, dos bordes y dos colores
    // para decir una sola letra. El marcador ES el disco: azul macizo, P blanca dentro, sin papel y
    // sin contorno. Y es el 71 % de los parkings, o sea el caso NORMAL, no el raro.
    // [MAP-A-CAR-PARK-MARKER-SAYS-WHAT-IT-COSTS-001] [MAP-A-CAR-PARK-MARKER-WEARS-ITS-OWN-BLUE-001]
    NearbyDiscMarker(
        glyph = PaparcarIcons.CarParkMark,
        accent = PapColor.carPark,
        onAccent = PapColor.onCarPark,
        selected = selected,
        modifier = modifier,
    )
}

/**
 * El marcador de un sitio que **no tiene nada que decir además de qué es**: un disco macizo del
 * acento con su glifo dentro, sobre el mismo punto y la misma sombra que el resto de la familia.
 * [MAP-A-CAR-PARK-MARKER-SAYS-WHAT-IT-COSTS-001]
 *
 * Es la hermana callada de [NearbyCapsuleMarker] y comparte con ella **toda** la geometría: la misma
 * escala `s`, el mismo `markerH`, el mismo apoyo en [MARKER_BODY_BOTTOM_Y] y el mismo alto de cuerpo
 * ([CAPSULE_BODY_MIN_U]). Por eso el disco mide **exactamente lo que mide una cápsula que sólo
 * lleva glifo** —la gasolinera sin precio— y las dos se leen como el mismo objeto a distinta carga,
 * que es lo que pidió la review.
 *
 * ⛔ **En reposo no lleva borde, y el anillo NO vuelve**
 * [MAP-A-DISC-THAT-CARRIES-NO-DATA-NEEDS-NO-RING-001]. Hasta `19243d2a0` el parking dibujaba un
 * contorno de `surface` alrededor del relleno —negro en tema oscuro, blanco en claro— defendido con
 * que «un puck azul oscuro no se funda con las teselas oscuras». **No es el mismo caso que la
 * plaza**, y la diferencia es cuál de los dos lleva el DATO:
 *
 * - En la plaza el **relleno ES el dato** (la rampa de frescura, de verde a rojo). Un dato que
 *   cambia de color necesita un anillo neutro que lo separe del mapa, porque su contraste con la
 *   tesela depende de qué esté diciendo ese día.
 * - En el parking el relleno es **identidad fija** ([PapColor.carPark]), elegida ya para contrastar
 *   con las dos teselas. Un anillo ahí no separa nada: sólo añade un segundo cuerpo alrededor del
 *   primero — y de paso hundía la silueta 1,6 u por debajo de la línea de la familia, que es lo que
 *   hacía que el marcador se leyera con más aire que sus hermanos.
 *
 * Regla: **el anillo neutro es de los cuerpos cuyo color es un dato, no de los que son identidad.**
 *
 * La selección sí dibuja un anillo de papel,
 * porque sin ninguna diferencia un parking tocado se vería idéntico a uno sin tocar y toda la
 * familia marca la selección de alguna forma. El anillo va FUERA del disco (`border` sobre el `clip`
 * circular), así que no le come tamaño al glifo.
 */
@Composable
private fun NearbyDiscMarker(
    glyph: ImageVector,
    accent: Color,
    onAccent: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current

    val s = with(density) { SPOT_PIN_W.toPx() / SPOT_VIEWBOX_W }
    val markerH = with(density) { (SPOT_VIEWBOX_H * s).toDp() }
    val bodyBottomPad = with(density) { ((SPOT_VIEWBOX_H - MARKER_BODY_BOTTOM_Y) * s).toDp() }
    val discSize = with(density) { (CAPSULE_BODY_MIN_U * s).toDp() }

    Box(
        modifier = modifier.height(markerH).width(IntrinsicSize.Max),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val canvasS = size.height / SPOT_VIEWBOX_H
            val centerX = (size.width / 2f) / canvasS
            // El punto de la cápsula/disco: mismo aire VISIBLE que la plaza sobre un cuerpo que
            // no sobresale. [MAP-THE-FAMILY-LINE-IS-THE-ONE-YOU-CAN-SEE-001]
            drawSpotShadow(canvasS, ink, centerX = centerX, dotCy = NEARBY_DOT_CY)
            drawSpotDot(canvasS, accent, centerX = centerX, dotCy = NEARBY_DOT_CY)
        }
        Box(
            modifier = Modifier
                .padding(bottom = bodyBottomPad)
                .size(discSize)
                .clip(CircleShape)
                .background(accent)
                .then(
                    if (selected) {
                        Modifier.border(FUEL_LABEL_BORDER_SEL, paper, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = glyph,
                contentDescription = null,
                tint = onAccent,
                // ⛔ La MISMA proporción glifo/disco que dentro de la cápsula (13 de 20), no los
                // 13 dp literales: aquí el disco es mayor, y un glifo fijo se vería perdido en el
                // centro. Lo que se comparte entre superficies es el objeto, no la cifra.
                modifier = Modifier.size(discSize * (CAPSULE_DISC_GLYPH_SIZE / CAPSULE_DISC_SIZE)),
            )
        }
    }
}


private val PARKING_LABEL_PAD_H = 9.dp

// La capsula ya no se comparte con la zona: es de la gasolinera y solo de ella. Estos tres valores
// vivian en el bloque de la zona y el surtidor los tomaba prestados -- por eso la misma forma decia
// dos cosas a la vez. La zona se fue a rotulo de area, asi que la capsula (fondo, borde y su alpha)
// se queda aqui, con la unica cosa puntual que lleva una cifra. [UI-A-ZONE-IS-AN-AREA-NOT-A-PIN-001]
private val FUEL_LABEL_CORNER = 999.dp
private val FUEL_LABEL_BORDER = 1.dp
private val FUEL_LABEL_BORDER_SEL = 2.dp
private val FUEL_GLYPH_SIZE = 16.dp

/** El disco de la cápsula y la P que lleva dentro. Proporción de la fila (tile 40 / glifo 20), a la
 *  escala del marcador. [MAP-A-CAR-PARK-MARKER-SAYS-WHAT-IT-COSTS-001] */
/** Alto mínimo del CUERPO de la cápsula, en unidades del viewBox — y por tanto el diámetro del
 *  disco de [NearbyDiscMarker], para que un sitio sin nada que decir mida lo mismo que una cápsula
 *  que sólo lleva glifo. [MAP-A-CAR-PARK-MARKER-SAYS-WHAT-IT-COSTS-001] */
private const val CAPSULE_BODY_MIN_U = 48f

private val CAPSULE_DISC_SIZE = 20.dp
private val CAPSULE_DISC_GLYPH_SIZE = 13.dp
private const val FUEL_LABEL_BG_ALPHA = 0.92f
private const val FUEL_LABEL_BORDER_ALPHA = 0.55f

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
 * this only animates the lift. Bottom-anchored so the marker's ground point stays put.
 * A stationary drop shadow stays glued to that ground point — it does NOT lift with the pin, so
 * while the camera moves it marks exactly where the marker will land on release. [MAP-ICONS-V2]
 */
@Composable
private fun LiftedCenterPin(
    cameraMoving: Boolean,
    groundInset: Dp,
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
    // 0 = pin resting, 1 = pin lifted. The shadow opens up (wider + stronger) while lifted so the
    // landing point reads clearly under the floating pin, and tucks back under it on rest.
    val shadowLift = animateFloatAsState(
        targetValue = if (cameraMoving) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    ).value
    // Dark map tiles swallow a black shadow, so the drop marker inverts to a white glow there.
    // Detect dark by theme luminance (not isSystemInDarkTheme) like the tag fill above. [MAP-ICONS-V2]
    val shadowBase = if (MaterialTheme.colorScheme.surface.luminance() < TAG_DARK_LUMINANCE) {
        Color.White
    } else {
        Color.Black
    }
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(DROP_SHADOW_WIDTH, DROP_SHADOW_HEIGHT)
                // Centre the ellipse ON the marker's VISUAL ground point — its position dot,
                // which sits [groundInset] above the canvas bottom — so the pin tip rests on
                // the shadow instead of floating above it.
                .offset(y = DROP_SHADOW_HEIGHT / 2 - groundInset)
                .alpha(entry.value),
        ) {
            val alpha = DROP_SHADOW_REST_ALPHA +
                (DROP_SHADOW_LIFT_ALPHA - DROP_SHADOW_REST_ALPHA) * shadowLift
            val spread = DROP_SHADOW_REST_SCALE +
                (DROP_SHADOW_LIFT_SCALE - DROP_SHADOW_REST_SCALE) * shadowLift
            val radius = (size.width / 2f) * spread
            val brush = Brush.radialGradient(
                colors = listOf(shadowBase.copy(alpha = alpha), Color.Transparent),
                center = center,
                radius = radius,
            )
            // Squash the circular gradient into a ground ellipse.
            drawScale(scaleX = 1f, scaleY = size.height / size.width, pivot = center) {
                drawCircle(brush = brush, radius = radius, center = center)
            }
        }
        Box(
            modifier = Modifier
                // Entry drop (negative = from above) added to the camera-move lift.
                .offset(y = (offsetY.value + (1f - entry.value) * PLACING_ENTRY_DROP_DP).dp)
                // Translucent "ghost" so the placing pin never looks like a real, placed marker.
                .alpha(entry.value * PLACING_GHOST_ALPHA)
                .scale(pinScale.value),
            contentAlignment = Alignment.BottomCenter,
        ) { content() }
    }
}

/** Manual-spot placement pin — the real spot puck (manual blue). [MAP-ICONS-V2] */
@Composable
fun ReportCenterPin(
    cameraMoving: Boolean,
    modifier: Modifier = Modifier,
) {
    LiftedCenterPin(cameraMoving, groundInset = SPOT_PIN_GROUND_INSET, modifier = modifier) {
        FreeSpotMarker(isManual = true)
    }
}

/** Parked-car placement pin — the real square vehicle tag. Falls back to a sedan body when the
 *  positioning vehicle has no carbody so the tag always shows an isometric car. [MAP-ICONS-V2] */
@Composable
fun ParkingCenterPin(
    cameraMoving: Boolean,
    modifier: Modifier = Modifier,
    carbodyType: com.rndeveloper.paparcar.domain.model.CarbodyType? = null,
    sizeCategory: VehicleSize? = null,
    color: com.rndeveloper.paparcar.domain.model.VehicleColor? = null,
    isActive: Boolean = true,
    isBluetoothPaired: Boolean = false,
) {
    LiftedCenterPin(cameraMoving, groundInset = TAG_PIN_GROUND_INSET, modifier = modifier) {
        // Same marker the vehicle will get when placed — icon, paint colour AND state border
        // (grey inactive / blue BT / green active) — so the preview matches the result. [MOTION-POLISH-001]
        VehicleBadgeMarker(
            carbodyType = carbodyType ?: com.rndeveloper.paparcar.domain.model.CarbodyType.SEDAN,
            sizeCategory = sizeCategory,
            color = color,
            isActive = isActive,
            isBluetoothPaired = isBluetoothPaired,
        )
    }
}

/**
 * El pin de colocación de una zona es **el mismo rótulo que quedará**, sin nombre todavía: se
 * coloca el centro de un área, y lo que se está colocando ya se ve como se verá.
 * [UI-A-ZONE-IS-AN-AREA-NOT-A-PIN-001]
 *
 * Antes era un glifo suelto sin halo ni candado, así que no se parecía ni a lo que había en el mapa
 * ni a lo que iba a quedar — la única familia de pines de colocación que incumplía su propia regla
 * («el pin que colocas ES el marcador que quedará»). No se eleva ni proyecta sombra a propósito:
 * eso es de los objetos que pisan un punto, y una zona no pisa ninguno.
 *
 * @param isPrivate el pin que arrastras lleva la identidad que la zona TENDRÁ al guardarse. El
 *   anillo de debajo ya se volvía neutro con ese mismo flag, así que un pin `primary` pintaba una
 *   zona de dos colores en un solo fotograma. Ahora además lleva su candado, como el rótulo.
 *   [UI-A-PRIVATE-ZONE-IS-PRIVATE-ON-EVERY-SURFACE-001]
 * @param name nombre ya escrito, si lo hay: en el alta todavía no existe y el rótulo es sólo glifo.
 */
@Composable
fun ZoneCenterPin(
    icon: ImageVector,
    @Suppress("UNUSED_PARAMETER") cameraMoving: Boolean,
    isPrivate: Boolean,
    modifier: Modifier = Modifier,
    name: String = "",
) {
    ZoneMarker(
        name = name,
        icon = icon,
        isPrivate = isPrivate,
        showName = name.isNotBlank(),
        modifier = modifier,
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
// Stationary drop shadow under the placement pins ([LiftedCenterPin]): a ground ellipse pinned to
// the drop point that does NOT lift with the pin. Rest = tucked under the marker; lift = opens up
// (wider + darker) so the landing point stays obvious while the pin floats.
private val DROP_SHADOW_WIDTH  = 22.dp
private val DROP_SHADOW_HEIGHT = 8.dp
// How far each marker's visual tip — the BOTTOM edge of its position dot — sits above its canvas
// bottom. The drop shadow rises by this inset so the tip rests ON the shadow (ellipse centred at
// the dot's bottom tangent; centring on the dot itself reads too high, a halo around the dot).
private val SPOT_PIN_GROUND_INSET = SPOT_PIN_W * ((SPOT_VIEWBOX_H - SPOT_DOT_CY - SPOT_DOT_R) / SPOT_VIEWBOX_W)
private val TAG_PIN_GROUND_INSET  = TAG_MARKER_W * ((TAG_VB_H - TAG_DOT_CY - TAG_DOT_R) / TAG_VB_W)
private const val DROP_SHADOW_REST_SCALE = 0.75f
private const val DROP_SHADOW_LIFT_SCALE = 1f
private const val DROP_SHADOW_REST_ALPHA = 0.22f
private const val DROP_SHADOW_LIFT_ALPHA = 0.38f
// El tamaño del pin de zona ya no vive aquí: es el del rótulo ([ZONE_LABEL_ICON]), porque son el
// mismo objeto. Y el empujoncito vertical se fue con la cápsula — corregía que un glifo de lágrima
// leyera alto dentro de una caja que ya no existe. [UI-A-ZONE-IS-AN-AREA-NOT-A-PIN-001]

// ─── Cluster marker ───────────────────────────────────────────────────────────

@Composable
fun FreeSpotClusterMarker(count: Int, modifier: Modifier = Modifier) {
    // La familia de MARCA sale del tema, no de una llamada suelta: si la app cambia de
    // tipografía, las etiquetas del mapa cambian con ella. [UI-TYPE-ONE-VOICE-REACHES-MATERIAL-001]
    val outfit = PaparcarType.current.cardTitle.fontFamily
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

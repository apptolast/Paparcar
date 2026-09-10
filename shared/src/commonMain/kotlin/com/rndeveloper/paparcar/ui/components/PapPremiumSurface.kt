package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import com.rndeveloper.paparcar.ui.theme.PapBorders
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.PremiumSweep
import com.rndeveloper.paparcar.ui.theme.premiumEdge
import com.rndeveloper.paparcar.ui.theme.premiumInk
import com.rndeveloper.paparcar.ui.theme.premiumSweep
import org.jetbrains.compose.resources.painterResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.paparcar_iconmark_white

/**
 * The ONE surface that says "Paparcar Plus". [UI-COLOR-PLUS-WEARS-THE-BRAND-WITH-A-FINISH-001]
 *
 * Every Plus touchpoint — the Settings row, the history footer, the paywall hero — renders on
 * this, so the premium look is a single recipe instead of three call sites each inventing a
 * gradient — and it is why the third revocation was fixed by editing two files rather than three
 * screens. The metallic read comes from a narrow, off-centre highlight crossing a BRUSHED brand
 * bed: a flat tone cannot look like metal, and the doctrine has no hue left for a fourth flat
 * green.
 *
 * The sweep runs corner to corner rather than straight down, because a highlight that tracks
 * the diagonal is what the eye reads as a polished surface; a vertical fade reads as a plain
 * tonal card.
 *
 * ⚠️ This is a FILLED brand surface — brand green carrying dark ink, the same contract as
 * `PapPrimaryButton` — so it PROVIDES `onPrimary` as its content colour and children inherit
 * ink that reads on green. Do not flip that back to light text to "allow a darker bed": the dark
 * ink is precisely what lets the highlight be bright, because with dark content a brighter band
 * can only improve contrast. With white ink, every highlight becomes a legibility risk and the
 * sheen has to be timid — which is how this surface ended up flat the first time.
 *
 * The bed is deliberately NOT `PapGreen`. Filling a card with the brand accent is what made Plus
 * read as garish; area amplifies chroma. See the block in `Color.kt`.
 * [UI-PLUS-A-BRAND-FILL-IS-NOT-A-METALLIC-FINISH-001]
 */
@Composable
fun PapPremiumSurface(
    modifier: Modifier = Modifier,
    shape: Shape = PapShapes.card,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    emphasis: PapPremiumEmphasis = PapPremiumEmphasis.Filled,
    content: @Composable BoxScope.() -> Unit,
) {
    // The SHAPE of the finish. `premiumSweep` supplies the three tones; the layers below are what
    // turn them into brushed metal.
    //
    // 🔑 The thing this went back and forth on four times: **the STRUCTURE is what reads as metal,
    // the AMPLITUDE is what reads as exaggerated, and they are independent knobs.** The version
    // revoked as "too much" had the right structure — several bands plus a brushed grain — over a
    // 25 L* range, and all that light-and-dark became stripes. Flattening it to a single soft
    // highlight fixed the stripes and lost the metal with them. What works is keeping every band
    // and every groove and COMPRESSING the range to 11 L*: the eye still reads a worked surface,
    // it just is not shouted at.
    //
    //  1. `sweep` — several specular bands of differing strength at UNEVEN spacing. Even spacing
    //     reads as a printed pattern rather than as light.
    //  2. `grain` — the micro-grooves, as fine repeating diagonal streaks at very low alpha.
    //     `TileMode.Repeated` over 2 dp, so the texture keeps its physical size on a small card
    //     and on a full-width hero.
    //  3. `bevel` — light falls from above, so the top edge catches it and the bottom sits in
    //     shade. Shaded in the sweep's OWN deep tone rather than neutral black, because metal
    //     shades toward its own colour.
    //
    // A later pass asked for "more metal, still not exaggerated", and the two knobs above are
    // exactly what makes that a sentence with an answer: every band got NARROWER and better
    // separated (a third faint band, a dark stop pressed against the specular one, tighter bevel
    // shoulders, a second lit corner, slightly more grain) and the three tones in `Color.kt` were
    // not touched at all. Structure up, amplitude flat.
    // [UI-PAYWALL-THE-CREST-STANDS-BESIDE-ITS-NAME-001]
    //
    // Corner-to-corner: `Brush.linearGradient` with no explicit start/end runs top-left →
    // bottom-right by default, which is exactly the diagonal a sheen needs.
    val tones = premiumSweep
    val sweep = metallicSweep(tones)
    val grainPeriodPx = with(LocalDensity.current) { GRAIN_PERIOD_DP.dp.toPx() }
    val grain = Brush.linearGradient(
        0.00f to Color.Transparent,
        0.35f to premiumEdge.copy(alpha = GRAIN_LIGHT_ALPHA),
        0.50f to Color.Transparent,
        0.65f to tones.shadow.copy(alpha = GRAIN_DARK_ALPHA),
        1.00f to Color.Transparent,
        start = Offset.Zero,
        end = Offset(grainPeriodPx, grainPeriodPx),
        tileMode = TileMode.Repeated,
    )
    val bevel = Brush.verticalGradient(
        0.00f to premiumEdge.copy(alpha = BEVEL_TOP_ALPHA),
        0.13f to Color.Transparent,
        0.87f to Color.Transparent,
        1.00f to tones.shadow.copy(alpha = BEVEL_BOTTOM_ALPHA),
    )
    val edge = BorderStroke(
        width = PapBorders.thin,
        brush = Brush.linearGradient(
            // Two lit corners, not one fading to nothing: real metal catches the light at the
            // corner facing it AND, faintly, at the one opposite. That second catch is a large part
            // of why an edge reads as an OBJECT's rim rather than as a card's border — and it costs
            // no extra lightness range, which is the axis that must not grow.
            // [UI-PAYWALL-THE-CREST-STANDS-BESIDE-ITS-NAME-001]
            0.00f to premiumEdge.copy(alpha = EDGE_BRIGHT_ALPHA),
            0.55f to Color.Transparent,
            1.00f to premiumEdge.copy(alpha = EDGE_COUNTER_ALPHA),
        ),
    )
    val subdued = emphasis == PapPremiumEmphasis.Subdued
    // The three layers are painted BEHIND the content, so the emphasis alpha fades the metal and
    // never the text on it. `Surface` already clips to `shape`, so the draw needs no shape of its
    // own.
    val metalAlpha = if (subdued) SUBDUED_ALPHA else 1f
    val body: @Composable () -> Unit = {
        Box(
            modifier = Modifier.drawBehind {
                drawRect(brush = sweep, alpha = metalAlpha)
                drawRect(brush = grain, alpha = metalAlpha)
                drawRect(brush = bevel, alpha = metalAlpha)
            },
            content = content,
        )
    }
    // contentColor is the point: everything inside inherits ink that reads on its bed, so a call
    // site cannot pair the metal with the wrong ink by accident. The two weights need DIFFERENT
    // ink: the filled bed is bright green and takes dark ink; the subdued one is a tint over the
    // page and takes the page's own `onSurface`, exactly like any other card.
    val ink = if (subdued) MaterialTheme.colorScheme.onSurface else premiumInk
    val border = if (subdued) {
        BorderStroke(PapBorders.thin, tones.bed.copy(alpha = SUBDUED_EDGE_ALPHA))
    } else {
        edge
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            color = Color.Transparent,
            contentColor = ink,
            border = border,
            content = body,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = Color.Transparent,
            contentColor = ink,
            border = border,
            content = body,
        )
    }
}

/**
 * How loudly a Plus surface speaks. [UI-PLUS-EMPHASIS-MATCHES-THE-MOMENT-001]
 *
 * 🔑 Same material, different amount of it — because the moment is different:
 *
 *  · [Filled] is for a surface whose JOB is to be the product: the paywall's Plus tier, its buy
 *    button, the crest. You came here to decide.
 *  · [Subdued] is for a surface that INTERRUPTS something else — the footer under a history list,
 *    the Settings row, the stats row on a vehicle. There the metal is a tap on the shoulder, and a
 *    full slab of brand green in the middle of a list of parkings reads as an advert, not as part
 *    of the app. Same brushed sweep, drawn as a tint over the page, with the page's own ink.
 *    ⚠️ Same METAL though: the weight changes how opaque it is, never whether the bands and the
 *    grain are there to see. See [SUBDUED_ALPHA] for the time that distinction was lost.
 *
 * This is the axis the finish was missing: every earlier revocation tried to fix "too loud" by
 * changing the COLOUR, when the surface that was too loud was simply the wrong weight for where
 * it stood.
 */
enum class PapPremiumEmphasis { Filled, Subdued }

/**
 * How much of the metal survives in [PapPremiumEmphasis.Subdued]. Enough to read as the same
 * material, little enough to sit inside a list without taking it over.
 *
 * 🔑 **Less opaque is not the same as less metallic**, and 0.20 was quietly conflating them.
 * [UI-PAYWALL-THE-CREST-STANDS-BESIDE-ITS-NAME-001] At a fifth, the bands and the grain — the whole
 * reason the surface reads as worked metal rather than as a green tint — fell under the threshold
 * where the eye picks them out, so the Settings row and the history footer were a flat wash while
 * the paywall was metal. Two materials for one product.
 *
 * At 0.34 the same sweep is legible as a finish and the surface is still plainly a TINT over the
 * page: the ink stays `onSurface`, the rim stays faint, and it still reads as a tap on the
 * shoulder inside a list rather than as a slab of brand green. Judged on device against both the
 * Settings row and the history footer.
 */
private const val SUBDUED_ALPHA = 0.34f

/** The subdued rim: the bed itself, faint. White would be the FILLED surface's lit corner, and on
 *  a tint there is no lit corner to speak of — just an edge. */
private const val SUBDUED_EDGE_ALPHA = 0.45f

/**
 * The brushed sweep — the ONE description of what this metal looks like, shared by the card, the
 * button and the crest so they can never drift apart.
 *
 * 🔑 **Structure and amplitude are separate knobs, and only one of them was ever the problem.**
 * These many bands — unevenly spaced, of differing strength — are what the eye reads as a worked
 * metal surface. What made an earlier cut look exaggerated was not having them; it was spreading
 * them over 25 L*. Compressed to 11 L* around the bed, the same structure reads as a finish rather
 * than as stripes. Deleting the bands in between (a single soft highlight) removed the stripes and
 * the metal together, which is how this got to four passes.
 *
 * Even spacing would read as a printed pattern, so the gaps are deliberately irregular.
 * [UI-PLUS-A-BRAND-FILL-IS-NOT-A-METALLIC-FINISH-001]
 */
private fun metallicSweep(tones: PremiumSweep): Brush = Brush.linearGradient(
    0.00f to tones.bed,
    0.09f to tones.shadow,
    0.15f to tones.sheen.copy(alpha = SECONDARY_BAND_ALPHA).compositeOver(tones.bed),
    0.20f to tones.bed,
    0.29f to tones.shadow,
    0.35f to tones.sheen.copy(alpha = FAINT_BAND_ALPHA).compositeOver(tones.bed),
    0.40f to tones.bed,
    // The specular band, and the two stops that make it read as one. `0.46 bed` is a flat run-up,
    // so the climb into the sheen happens over 0.03 instead of 0.05 — a reflection has a hard
    // leading edge; a gradient does not. `0.58 bed → 0.63 shadow` is the fall on the other side:
    // the eye judges a highlight against what sits immediately beside it, so putting the darkest
    // tone right after the brightest buys contrast that costs no extra lightness range.
    // [UI-PAYWALL-THE-CREST-STANDS-BESIDE-ITS-NAME-001]
    0.46f to tones.bed,
    0.49f to tones.sheen,
    0.53f to tones.sheen,
    0.58f to tones.bed,
    0.63f to tones.shadow,
    0.71f to tones.bed,
    0.78f to tones.sheen.copy(alpha = FAINT_BAND_ALPHA).compositeOver(tones.shadow),
    0.84f to tones.shadow,
    1.00f to tones.bed,
)

/**
 * The Paparcar LOGO cast in metal — the disc, not a bare glyph.
 * [UI-PLUS-WEARS-THE-BRANDS-OWN-MARK-001]
 *
 * Plus is the brand dressed up, so the hero mark should be the BRAND, not a generic medal: a
 * trophy glyph said "achievement", and nothing is unlocked by subscribing.
 *
 * ⚠️ **The logo always keeps its disc.** A first cut used the bare iconmark and it was wrong twice
 * over: it stopped being the logo, and a bare glyph gives a gradient almost no area to happen in,
 * so it rendered as flat green. The disc is what the metal is FOR.
 *
 * The structure mirrors the logo's dark-theme form — coloured disc, ink glyph — with the metal
 * standing in for the green. That also settles which way round it goes: an Ink disc would simply
 * disappear on a dark surface, so the DISC is what carries the finish and the glyph is knocked out
 * of it in the surface's ink, exactly like every other child of a [PapPremiumSurface].
 *
 * Built from `PapPremiumSurface` with a circular shape rather than a private gradient, so the
 * crest and the cards can never drift apart.
 */
@Composable
fun PapPremiumMark(
    modifier: Modifier = Modifier,
    size: Dp = PAPARCAR_LOGO_SIZE,
) {
    PapPremiumSurface(modifier = modifier.size(size), shape = CircleShape) {
        // ⚠️ Do NOT inset the glyph here. `paparcar_iconmark_white` already carries the same 0.8
        // group scale the full logo uses, so its padding is baked into the drawable. A first cut
        // multiplied by another 0.58 on top and the glyph landed at ~0.46 of the disc: two insets
        // stacked, and the crest read as tiny.
        PaparcarMark(
            tint = premiumInk,
            size = size * GLYPH_OVERSCAN,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/**
 * How much larger than the disc the glyph box is drawn.
 *
 * The artwork only spans about 54 % of its own canvas, so at 1.0 the car sits well inside the
 * disc and its 6-unit wheel strokes render thin. Scaling the box up fills the disc AND thickens
 * the strokes in one move — the alternative, a fatter `strokeWidth`, would mean a premium-only
 * copy of the logo diverging from the splash, the auth badge and the launcher.
 *
 * ⛔ There is a hard ceiling around 1.35: the wheels sit low and right, and past that their corner
 * crosses the circle and gets clipped by the disc. Measured from the path extents, not guessed.
 */
private const val GLYPH_OVERSCAN = 1.22f

/**
 * The CTA that BUYS Plus — the brand's metal cast as a button.
 *
 * It IS the metal, because it sits on a plain surface. The paywall's buy button is the single
 * place in the app where the action and the product are the same thing, so it wears the product's
 * material instead of the flat brand fill every other CTA uses.
 *
 * Deliberately NOT a third `PapButtonTone`. A tone is a MEANING — brand, destructive — and
 * "premium" is not a meaning a button can have on any screen; it is this product's surface. Adding
 * it as a tone would invite `tone = Premium` onto unrelated buttons, which is exactly the drift
 * `PapButtonTone` was capped at two entries to prevent. [UI-BUTTON-ONE-CANONICAL-CTA-001]
 *
 * Built on [PapPremiumSurface], so a change to the metal reaches the button too.
 */
@Composable
fun PapPremiumButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    PapPremiumSurface(
        modifier = modifier,
        shape = PapShapes.chip,
        onClick = onClick,
        enabled = enabled && !isLoading,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = PREMIUM_BUTTON_V_PAD_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(PREMIUM_BUTTON_SPINNER_DP.dp),
                    color = premiumInk,
                    strokeWidth = PREMIUM_BUTTON_SPINNER_STROKE_DP.dp,
                )
            } else {
                Text(
                    text = label,
                    style = PaparcarType.current.cta,
                    color = premiumInk,
                )
            }
        }
    }
}

/** Matches the canonical CTA's silhouette so the premium button is the same OBJECT in a different
 *  material, not a differently shaped button. */
private const val PREMIUM_BUTTON_V_PAD_DP = 14
private const val PREMIUM_BUTTON_SPINNER_DP = 20
private const val PREMIUM_BUTTON_SPINNER_STROKE_DP = 2


/** How present the lit edge is at the highlight corner. Below this the metal reads flat; above
 *  it the border competes with the CTA inside the card. */
private const val EDGE_BRIGHT_ALPHA = 0.72f

/** The far corner's catch. Deliberately a fifth of the near one: equals would read as a uniform
 *  outline, which is the opposite of what a rim is for. */
private const val EDGE_COUNTER_ALPHA = 0.14f

/** The secondary and third bands, as a fraction of the primary. Metal throws several highlights,
 *  but they are not equals — matched bands read as stripes, not as light. */
private const val SECONDARY_BAND_ALPHA = 0.48f
private const val FAINT_BAND_ALPHA = 0.26f

/** Spacing of the brushed micro-grooves — small enough to read as texture rather than as lines,
 *  fixed in dp so the grain keeps its size on a small card and on a full-width hero. */
private const val GRAIN_PERIOD_DP = 2

/** The grooves are barely there on purpose: this is texture, not decoration. Raised a notch from
 *  0.05/0.035 — brushed metal is a texture you can SEE, and at the old alphas it only survived on
 *  the full-width Plus card, disappearing entirely on the crest and the button.
 *  [UI-PAYWALL-THE-CREST-STANDS-BESIDE-ITS-NAME-001] */
private const val GRAIN_LIGHT_ALPHA = 0.075f
private const val GRAIN_DARK_ALPHA = 0.05f




/** Light falls from above: the top edge catches it, the bottom sits in its own shade.
 *
 *  The shoulders were tightened (0.18/0.82 → 0.13/0.87) rather than the alphas raised. Same idea as
 *  the sweep: a NARROWER catch reads as a harder, more metallic edge, while a stronger one just
 *  reads as a darker card. [UI-PAYWALL-THE-CREST-STANDS-BESIDE-ITS-NAME-001] */
private const val BEVEL_TOP_ALPHA = 0.18f
private const val BEVEL_BOTTOM_ALPHA = 0.30f

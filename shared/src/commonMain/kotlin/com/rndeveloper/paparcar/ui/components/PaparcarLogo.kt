package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.brand_name
import paparcar.composeapp.generated.resources.paparcar_iconmark_forest
import paparcar.composeapp.generated.resources.paparcar_iconmark_green
import paparcar.composeapp.generated.resources.paparcar_iconmark_white
import paparcar.composeapp.generated.resources.paparcar_imagotipo_dark
import paparcar.composeapp.generated.resources.paparcar_imagotipo_light

/**
 * The Paparcar mark standing alone — splash, onboarding hero, headers.
 *
 * ⛔ **Ya no lleva disco.** La marca de 2022 necesitaba uno oscuro detrás para
 * sostenerse sobre cualquier superficie; ésta no. Lo único que sigue cambiando con
 * el tema es el VERDE, y por una razón medida: `PapGreen` (#25F48C) sobre blanco da
 * **1,42:1**, así que en claro el relleno es `PapGreenLight` (#009F5E, 3,01:1) — el
 * mismo verde que el proyecto ya usa para botones, glifos y marcadores en tema claro.
 *
 * Sin el disco, esto y [PaparcarIconmark] pasan a ser la misma imagen y sólo se
 * diferencian en el tamaño por defecto. Se mantienen los dos nombres porque dicen
 * cosas distintas en el call site —*la marca sola* vs *la marca como glifo*— y
 * porque fusionarlos tocaría una docena de pantallas sin ganar nada.
 */
/**
 * The size the brand mark stands alone at — splash, auth, the Plus crest.
 *
 * One number, because a logo that is 96 dp on one screen and 60 dp on the next is not the same
 * logo. It lived as `PaparcarLogo`'s private default until the Plus crest invented its own 60 dp
 * and read as tiny beside every other appearance. [UI-PLUS-WEARS-THE-BRANDS-OWN-MARK-001]
 */
val PAPARCAR_LOGO_SIZE = 96.dp

/**
 * The size the brand mark stands at when it sits BESIDE the product's name, in a header row — the
 * auth screen, the paywall.
 *
 * A second number rather than a second opinion: standing alone the mark IS the composition and can
 * fill 96 dp; standing next to a 24 sp title it has to agree with a line of text, and 96 dp there
 * turns a header into a banner. What must not happen is each screen picking its own — this lived as
 * a `private val` inside the auth file, which is exactly the shape of the mistake that let the Plus
 * crest invent a 60 dp of its own. [UI-PAYWALL-THE-CREST-STANDS-BESIDE-ITS-NAME-001]
 */
val PAPARCAR_LOGO_BESIDE_NAME_SIZE = 72.dp

/**
 * The width the IMAGOTYPE stands at when it heads a screen — the auth header today.
 *
 * ⛔ A WIDTH, not a size, and that is the whole point: the imagotype is 7.320:1, so what a surface
 * can give it is horizontal room. Asking for a square `size` here is how a logo gets squashed.
 *
 * It is wider than [PAPARCAR_LOGO_BESIDE_NAME_SIZE] was (72 dp) because it now contains the name
 * that used to sit next to it as a `Text`. The mark inside it reads smaller than the old 72 dp
 * mark — measured, 33 dp of ink against 56 dp — and that is the trade a horizontal lockup makes:
 * on a 360 dp phone a mark at its old optical size would need 411 dp of lockup beside it. The
 * brand reads as one object instead of two, which is what it is.
 */
val PAPARCAR_IMAGOTIPO_WIDTH = 240.dp

/**
 * The lockup's width-to-height ratio, used ONLY when a painter reports no intrinsic size.
 *
 * ⛔ Not the source of truth — the painter is. This exists so a painter without an intrinsic size
 * degrades to the right shape instead of to a crash or a squashed logo, and it is deliberately the
 * only place in Kotlin that writes the number down.
 */
private const val PAPARCAR_IMAGOTIPO_RATIO = 321.67569f / 43.94414f

/**
 * The Paparcar IMAGOTYPE: the mark **and its name**, as one drawing.
 *
 * Use it wherever the brand says who it is — a screen header, a hero. Use [PaparcarLogo] where the
 * mark stands alone. The rule has no exceptions left: *mark alone → isotype; mark saying its name
 * → imagotype*, and never a `PaparcarLogo` with a `Text` of the name composed beside it, which is
 * what six surfaces were each doing their own way. [BRAND-THE-MARK-GAINS-ITS-WORDMARK-001]
 *
 * ⛔ **The word is drawn, not typed.** Its eight glyphs are outlines from the brand master, so it
 * carries no typographic decision: it does NOT pass through `PaparcarType`, and it is not a
 * typography-doctrine exception either — the three voices govern the app's TEXT, and this is an
 * illustration [UI-TYPE-TWO-VOICES-ONE-ROW-001].
 *
 * ⛔ **Never tint it.** Tier 3 brings its own colour, and the two fills here are what distinguish
 * the symbol from the word — a `ColorFilter` would flatten both into one and lose exactly that.
 * The theme picks a whole VARIANT instead, for the measured reason that already governs the mark:
 * `PapGreen` on white is 1.42:1, so light theme gets `PapGreenLight` plus `PapInk` for the word.
 *
 * ⚠️ The caller gives a WIDTH and the height comes from the drawing's own ratio — read off the
 * painter, never written here. A hand-copied 5.484 would be a fourth place where a number from the
 * master lives, and the one that goes stale in silence the day the drawing changes.
 *
 * ⛔ **Both sides of the box have to be constrained, and that is not a detail.** `Modifier.width()`
 * alone leaves the height at the drawable's intrinsic height, so the default `ContentScale.Fit`
 * scales by `min(240/183, 25/25) = 1.0` — it does not scale at all — and `Alignment.Center` splits
 * the leftover into equal gaps on each side. That shipped: on the login the mark drew at its
 * native width and sat 35 dp in from the left edge every other child aligned to, which reads as
 * two separate defects ("too small", "not aligned") and is one.
 * [BRAND-THE-IMAGOTIPO-MUST-FILL-ITS-WIDTH-001] [UI-AUTH-HEADER-ALIGNS-WITH-ITS-FIELDS-001]
 *
 * ⛔ `ContentScale.FillWidth` is NOT the fix: it would scale the drawing while the node still
 * measured its intrinsic height, so the logo would overflow its own box and clip. The box is what was wrong.
 *
 * ⚠️ The generated drawable is 183 × 25 dp because the exporter refuses a width
 * whose whole-dp height would skew the lockup by more than 0.2 % — so the ratio read back here is
 * the master's, to within that.
 */
@Composable
fun PaparcarImagotipo(
    modifier: Modifier = Modifier,
    width: Dp = PAPARCAR_IMAGOTIPO_WIDTH,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val resource = if (isDark) Res.drawable.paparcar_imagotipo_dark
                   else Res.drawable.paparcar_imagotipo_light
    val painter = painterResource(resource)
    val size = painter.intrinsicSize
    // A painter may report no intrinsic size; the lockup's own ratio is the only sane fallback,
    // and it is the one the exporter guarantees the drawable was written with.
    val ratio = if (size.isSpecified && size.height > 0f) size.width / size.height
                else PAPARCAR_IMAGOTIPO_RATIO
    Image(
        painter = painter,
        contentDescription = stringResource(Res.string.brand_name),
        modifier = modifier.width(width).aspectRatio(ratio),
    )
}

@Composable
fun PaparcarLogo(
    modifier: Modifier = Modifier,
    size: Dp = PAPARCAR_LOGO_SIZE,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val resource = if (isDark) Res.drawable.paparcar_iconmark_green
                   else Res.drawable.paparcar_iconmark_forest
    Image(
        painter = painterResource(resource),
        contentDescription = "Paparcar",
        modifier = modifier.size(size),
    )
}

/**
 * The Paparcar mark as a plain glyph in ONE colour, no disc and no tile — the brand used the way
 * a Material icon is used, beside a title or inside a row.
 *
 * Distinct from [PaparcarIconmark], which bakes in its own brand fill and picks a variant by
 * theme: this one takes the colour from the call site, because the surfaces it sits on decide it.
 * On a dark surface that is the brand green; on the filled metallic Plus bed it has to be the
 * surface's ink, since brand green on brand green is invisible.
 *
 * Uses `paparcar_iconmark_white`, the variant that exists precisely to be tinted — the green and
 * forest ones bake their fill in and must never be recoloured. [UI-PLUS-WEARS-THE-BRANDS-OWN-MARK-001]
 */
@Composable
fun PaparcarMark(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Image(
        painter = painterResource(Res.drawable.paparcar_iconmark_white),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}

/**
 * Iconmark only (no circle background). Auto-resolves the variant from the
 * current Material colour scheme:
 *  - dark theme  → neon-green car on translucent canvas
 *  - light theme → forest car on translucent canvas
 *
 * Use over coloured surfaces where the full logo's circle would clash.
 */
@Composable
fun PaparcarIconmark(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val resource = if (isDark) Res.drawable.paparcar_iconmark_green
                   else Res.drawable.paparcar_iconmark_forest
    Image(
        painter = painterResource(resource),
        contentDescription = "Paparcar",
        modifier = modifier.size(size),
    )
}

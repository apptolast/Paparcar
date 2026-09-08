package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.paparcar_iconmark_forest
import paparcar.composeapp.generated.resources.paparcar_iconmark_green
import paparcar.composeapp.generated.resources.paparcar_logo
import paparcar.composeapp.generated.resources.paparcar_logo_dark
import paparcar.composeapp.generated.resources.paparcar_iconmark_white

/**
 * Full Paparcar logo (disc + car glyph). Auto-resolves the disc treatment from
 * the current Material colour scheme so the badge stays visible on any surface:
 *  - light theme → Ink disc + neon-green glyph
 *  - dark theme  → neon-green disc + Ink glyph (the Ink disc would otherwise
 *                  blend into the dark surface)
 *
 * Use for splash, onboarding hero, headers — anywhere the brand needs to
 * stand alone on a neutral surface.
 */
/**
 * The size the brand mark stands alone at — splash, auth, the Plus crest.
 *
 * One number, because a logo that is 96 dp on one screen and 60 dp on the next is not the same
 * logo. It lived as `PaparcarLogo`'s private default until the Plus crest invented its own 60 dp
 * and read as tiny beside every other appearance. [UI-PLUS-WEARS-THE-BRANDS-OWN-MARK-001]
 */
val PAPARCAR_LOGO_SIZE = 96.dp

@Composable
fun PaparcarLogo(
    modifier: Modifier = Modifier,
    size: Dp = PAPARCAR_LOGO_SIZE,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val resource = if (isDark) Res.drawable.paparcar_logo_dark else Res.drawable.paparcar_logo
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

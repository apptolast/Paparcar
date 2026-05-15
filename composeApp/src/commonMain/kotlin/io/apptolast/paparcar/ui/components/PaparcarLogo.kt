package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.paparcar_iconmark_forest
import paparcar.composeapp.generated.resources.paparcar_iconmark_green
import paparcar.composeapp.generated.resources.paparcar_iconmark_white
import paparcar.composeapp.generated.resources.paparcar_logo

/**
 * Full Paparcar logo (neon-green circle + forest car glyph).
 *
 * Use for splash, onboarding hero, headers — anywhere the brand needs to
 * stand alone on a neutral surface.
 */
@Composable
fun PaparcarLogo(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    Image(
        painter = painterResource(Res.drawable.paparcar_logo),
        contentDescription = "Paparcar",
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

/**
 * White iconmark — tintable variant. Pair with a `colorFilter` to apply any
 * design-system colour (e.g. error red, manual blue, on-primary).
 */
@Composable
fun PaparcarIconmarkWhite(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    Image(
        painter = painterResource(Res.drawable.paparcar_iconmark_white),
        contentDescription = "Paparcar",
        modifier = modifier.size(size),
    )
}

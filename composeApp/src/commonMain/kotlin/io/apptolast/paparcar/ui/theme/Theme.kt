package io.apptolast.paparcar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Dark scheme — uses the Pap forest/neon-green palette ─────────────────────
private val DarkColorScheme = darkColorScheme(
    primary = PapGreen,
    onPrimary = PapForest,
    primaryContainer = PapGreenMuted,
    onPrimaryContainer = PapGreen,
    secondary = PapAmber,
    onSecondary = PapForest,
    secondaryContainer = PapAmberMuted,
    onSecondaryContainer = PapAmber,
    background = PapForest,
    onBackground = PapOnDark,
    surface = PapForestMid,
    onSurface = PapOnDark,
    surfaceVariant = PapGreenMuted,
    onSurfaceVariant = PapOnDarkMuted,
    outline = PapGreenElement,
    outlineVariant = PapGreenMuted,
)

// ── Light scheme — green-on-white counterpart ─────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary = PapGreenLight,
    onPrimary = PapOnGreenLight,
    primaryContainer = PapGreenContainerLight,
    onPrimaryContainer = PapOnGreenContainerLight,
    inversePrimary = PapGreen,              // neon green on dark inverse surfaces
    secondary = PapAmberLight,
    onSecondary = PapOnGreenLight,
    secondaryContainer = PapAmberContainerLight,
    onSecondaryContainer = PapOnAmberContainerLight,
    background = PapSurfaceLight,           // page background — light greenish tint
    onBackground = PapOnSurfaceLight,
    surface = PapCardLight,                 // card / sheet surface — white
    onSurface = PapOnSurfaceLight,
    surfaceVariant = PapVariantLight,
    onSurfaceVariant = PapOnVariantLight,
    outline = PapOutlineLight,
    outlineVariant = PapOutlineVariantLight, // subtle dividers
    inverseSurface = PapInverseSurfaceLight,
    inverseOnSurface = PapInverseOnSurfaceLight,
    scrim = Color(0xFF000000),
)

@Composable
fun PaparcarTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

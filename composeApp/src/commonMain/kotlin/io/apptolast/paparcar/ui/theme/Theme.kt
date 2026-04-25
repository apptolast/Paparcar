package io.apptolast.paparcar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Dark scheme — near-black ink surfaces with neon-green brand accent ──────
// Surfaces (background + surface family) now use the PapInk ramp; dark greens
// (PapForest*, PapGreen*) are reserved for interactive accents (primary container,
// outlines, hero cards) so the app reads as dark and elegant while keeping the
// brand's green identity on actionable elements.
private val DarkColorScheme = darkColorScheme(
    primary = PapGreen,
    onPrimary = PapInk,
    primaryContainer = PapGreenMuted,          // dark green as accent container
    onPrimaryContainer = PapGreen,
    secondary = PapAmber,
    onSecondary = PapInk,
    secondaryContainer = PapAmberMuted,
    onSecondaryContainer = PapAmber,
    background = PapInkDeep,
    onBackground = PapOnDark,
    surface = PapInk,
    onSurface = PapOnDark,
    surfaceVariant = PapInkHigh,
    onSurfaceVariant = PapOnDarkMuted,
    surfaceContainerLowest = PapInkDeep,
    surfaceContainerLow = PapInk,
    surfaceContainer = PapInkContainer,        // sheet + nav bar share this token
    surfaceContainerHigh = PapInkHigh,
    surfaceContainerHighest = PapInkHighest,
    surfaceTint = PapGreen,                    // elevation tonal overlay stays green
    outline = PapGreenElement,
    outlineVariant = PapGreenMuted,
    error = PapRed,
    errorContainer = PapRedMuted,
    onError = PapOnRed,
    onErrorContainer = PapRed,
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
    // Explicit surfaceContainer* ramp — overrides Material3's default rose-tinted
    // light palette with the neutral cool-grey PapMist ramp.
    surfaceContainerLowest = PapMistLowest,
    surfaceContainerLow = PapMistLow,
    surfaceContainer = PapMist,
    surfaceContainerHigh = PapMistHigh,
    surfaceContainerHighest = PapMistHighest,
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
        typography = rememberAppTypography(),
        shapes = AppShapes,
        content = content,
    )
}

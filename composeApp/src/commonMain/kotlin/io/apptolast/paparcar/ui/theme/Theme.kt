package io.apptolast.paparcar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// ── Dark scheme — uses the Eco forest/neon-green palette ─────────────────────
private val DarkColorScheme = darkColorScheme(
    primary = EcoGreen,
    onPrimary = EcoForest,
    primaryContainer = EcoGreenMuted,
    onPrimaryContainer = EcoGreen,
    secondary = AmberAccent,
    onSecondary = EcoForest,
    secondaryContainer = AmberMuted,
    onSecondaryContainer = AmberAccent,
    background = EcoForest,
    onBackground = EcoOnDark,
    surface = EcoForestMid,
    onSurface = EcoOnDark,
    surfaceVariant = EcoGreenMuted,
    onSurfaceVariant = EcoOnDarkMuted,
    outline = EcoGreenElement,
    outlineVariant = EcoGreenMuted,
)

// ── Light scheme — green-on-white counterpart ─────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary = EcoGreenLight,
    onPrimary = EcoOnGreenLight,
    primaryContainer = EcoGreenContainerLight,
    onPrimaryContainer = EcoOnGreenContainerLight,
    secondary = AmberLight,
    onSecondary = EcoOnGreenLight,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = OnAmberContainerLight,
    background = EcoSurfaceLight,
    onBackground = EcoOnSurfaceLight,
    surface = EcoSurfaceLight,
    onSurface = EcoOnSurfaceLight,
    surfaceVariant = EcoVariantLight,
    onSurfaceVariant = EcoOnVariantLight,
    outline = EcoOutlineLight,
    outlineVariant = EcoVariantLight,
)

@Composable
fun PaparcarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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
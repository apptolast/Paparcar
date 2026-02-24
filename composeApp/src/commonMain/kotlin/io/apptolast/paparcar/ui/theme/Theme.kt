
package io.apptolast.paparcar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimaryLightActual,
    onPrimary = GreenOnPrimaryLight,
    primaryContainer = GreenPrimaryContainerLight,
    onPrimaryContainer = GreenOnPrimaryContainerLight,
    secondary = GreenSecondaryLight,
    secondaryContainer = GreenSecondaryContainerLight,
    onSecondaryContainer = GreenOnSecondaryContainerLight,
    background = GreenBackgroundLight,
    surface = GreenSurfaceLight,
    surfaceVariant = GreenSurfaceVariantLight,
    onSurface = GreenOnSurfaceLight,
    onSurfaceVariant = GreenOnSurfaceVariantLight
)

private val DarkColorScheme = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = GreenOnPrimaryDark,
    primaryContainer = GreenPrimaryContainerDark,
    onPrimaryContainer = GreenOnPrimaryContainerDark,
    secondary = GreenSecondaryDark,
    secondaryContainer = GreenSecondaryContainerDark,
    onSecondaryContainer = GreenOnSecondaryContainerDark,
    background = GreenBackgroundDark,
    surface = GreenSurfaceDark,
    surfaceVariant = GreenSurfaceVariantDark,
    onSurface = GreenOnSurfaceDark,
    onSurfaceVariant = GreenOnSurfaceVariantDark
)

// --- 4. THEME COMPOSABLE ---
@Composable
fun PaparcarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

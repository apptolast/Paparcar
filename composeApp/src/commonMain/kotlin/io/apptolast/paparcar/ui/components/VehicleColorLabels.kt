package io.apptolast.paparcar.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.apptolast.paparcar.domain.model.VehicleColor
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.vehicle_color_black
import paparcar.composeapp.generated.resources.vehicle_color_blue
import paparcar.composeapp.generated.resources.vehicle_color_brown
import paparcar.composeapp.generated.resources.vehicle_color_default
import paparcar.composeapp.generated.resources.vehicle_color_gold
import paparcar.composeapp.generated.resources.vehicle_color_graphite
import paparcar.composeapp.generated.resources.vehicle_color_gray
import paparcar.composeapp.generated.resources.vehicle_color_navy
import paparcar.composeapp.generated.resources.vehicle_color_orange
import paparcar.composeapp.generated.resources.vehicle_color_red
import paparcar.composeapp.generated.resources.vehicle_color_silver
import paparcar.composeapp.generated.resources.vehicle_color_white
import paparcar.composeapp.generated.resources.vehicle_color_yellow

private const val DARK_SURFACE_LUMINANCE = 0.5f

/**
 * Localized display name for a vehicle paint colour. `null` resolves to the
 * "default" label (the brand-green icon). Keeps the domain [VehicleColor] enum
 * Compose- and translation-free, mirroring [CarbodyType.label]. [VEH-COLOR-001]
 */
@Composable
fun VehicleColor?.colorLabel(): String = stringResource(
    when (this) {
        null -> Res.string.vehicle_color_default
        VehicleColor.WHITE -> Res.string.vehicle_color_white
        VehicleColor.SILVER -> Res.string.vehicle_color_silver
        VehicleColor.GRAY -> Res.string.vehicle_color_gray
        VehicleColor.GRAPHITE -> Res.string.vehicle_color_graphite
        VehicleColor.BLACK -> Res.string.vehicle_color_black
        VehicleColor.BLUE -> Res.string.vehicle_color_blue
        VehicleColor.NAVY -> Res.string.vehicle_color_navy
        VehicleColor.RED -> Res.string.vehicle_color_red
        VehicleColor.ORANGE -> Res.string.vehicle_color_orange
        VehicleColor.YELLOW -> Res.string.vehicle_color_yellow
        VehicleColor.GOLD -> Res.string.vehicle_color_gold
        VehicleColor.BROWN -> Res.string.vehicle_color_brown
    },
)

/**
 * The colour to paint a selector swatch with, theme-aware so it matches the body
 * tint actually applied to the icon. `null` (default) uses the brand primary green.
 */
@Composable
fun VehicleColor?.swatchColor(): Color {
    if (this == null) return MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE
    return Color(if (isDark) bodyDarkArgb else bodyLightArgb)
}

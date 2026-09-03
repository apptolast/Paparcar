package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.VehicleColor
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons

/**
 * Resolves the icon to render for a vehicle given the bidimensional taxonomy.
 *
 * Which artwork is drawn is [vehicleArtOf]'s call — a two-wheeler resolves its own pictogram, a car
 * resolves its carbody (falling back to the canonical body for its length tier, so vehicles
 * registered before the carbody refactor still render a real pictogram). Only a vehicle we know
 * nothing about reaches [fallback], the catch-all ImageVector.
 *
 * Returning a [Painter] lets call sites use a single `Icon(painter = …)` overload
 * regardless of whether the underlying asset is a vector drawable or an inline
 * ImageVector.
 */
// Surface luminance below this reads as a dark theme → use the dark (white-outlined) pictogram.
private const val DARK_SURFACE_LUMINANCE = 0.5f

@Composable
fun vehicleIconPainter(
    carbody: CarbodyType?,
    size: VehicleSize?,
    fallback: ImageVector = PaparcarIcons.VehicleCar,
    defaultCarbody: CarbodyType? = null,
    color: VehicleColor? = null,
): Painter {
    val art = vehicleArtOf(carbody = carbody, size = size, defaultCarbody = defaultCarbody)
    // Theme-aware isometric pictogram: the dark variant adds a thin white outline on body + wheels
    // so the car lifts off a dark map/surface. Detect the active theme by surface luminance (honours
    // the app's ThemeMode override, not just the system -dark qualifier). [BOLT-MARKERS-001]
    val isDark = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE
    // Every vehicle renders through the same geometry builder so it always carries the white body +
    // wheel border (light and dark). A chosen [color] recolours only the body; with no colour we use
    // the identity brand-green palette, which reproduces the original artwork. [VEH-COLOR-001]
    // [CAR-WHITE-BORDER-001]
    if (art != null) {
        val palette = color?.let { carPaletteOf(it, isDark) } ?: defaultCarPalette(topdown = false)
        val image = remember(art, color, isDark) {
            buildCarImageVector(art.isoSpec(), palette, isDark, ISO_WHEEL_STROKE)
        }
        return rememberVectorPainter(image)
    }
    return rememberVectorPainter(fallback)
}

/**
 * Renders the vehicle pictogram for the given (carbody, size) pair.
 *
 * Drop-in replacement for `Icon(painter = vehicleIconPainter(...), tint = X)`
 * with one critical difference: this uses `Image` + `ContentScale.Fit` so the
 * side-profile SVGs (~1.78:1 aspect ratio) are centred inside a square
 * Modifier without horizontal stretch — the artwork stays proportional.
 *
 * The new side-profile pictograms are multi-colour (brand-green body + white
 * windows + dark wheels), so the default is [Color.Unspecified] — the artwork
 * renders in its native palette in lists, selectors and registration. Pass an
 * explicit [tint] (as [VehicleBadge] does) to flatten the pictogram to a single
 * status colour via [ColorFilter.tint] — the windows/wheels collapse into the
 * silhouette, which is the intended look inside the dark status badge. [BOLT-MARKERS-001]
 *
 * Pass a [color] to recolour only the body of the pictogram (keeping windows, wheels and
 * outline) — the vehicle's paint colour. null renders the default brand-green artwork. A
 * non-Unspecified [tint] still overrides everything by flattening to a single colour. [VEH-COLOR-001]
 */
@Composable
fun VehicleIcon(
    carbody: CarbodyType?,
    size: VehicleSize?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    fallback: ImageVector = PaparcarIcons.VehicleCar,
    defaultCarbody: CarbodyType? = null,
    color: VehicleColor? = null,
) {
    Image(
        painter = vehicleIconPainter(
            carbody = carbody,
            size = size,
            fallback = fallback,
            defaultCarbody = defaultCarbody,
            color = color,
        ),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint),
    )
}

package io.apptolast.paparcar.ui.components

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
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.VehicleColor
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.fallbackCarbody
import io.apptolast.paparcar.ui.icons.PaparcarIcons
import io.apptolast.paparcar.ui.icons.icon
import org.jetbrains.compose.resources.painterResource

/**
 * Resolves the icon to render for a vehicle given the bidimensional taxonomy.
 *
 * Resolution order:
 *  1. `carbody` → its [CarbodyType.icon] drawable (the new side-profile pictograms).
 *  2. `size.fallbackCarbody()` → the canonical body for that length tier.
 *     Lets vehicles registered before the carbody refactor (or via custom
 *     brand/model with no inference) still render a real pictogram instead
 *     of the legacy silhouette.
 *  3. `size.icon` ImageVector → only reachable for [VehicleSize.MOTORCYCLE]
 *     since every other tier resolves a fallback carbody.
 *  4. `fallback` → catch-all ImageVector (defaults to a generic car).
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
    val resolved = carbody ?: size?.fallbackCarbody() ?: defaultCarbody
    // Theme-aware isometric pictogram: the dark variant adds a thin white outline on body + wheels
    // so the car lifts off a dark map/surface. Detect the active theme by surface luminance (honours
    // the app's ThemeMode override, not just the system -dark qualifier). [BOLT-MARKERS-001]
    val isDark = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE
    // A chosen [color] recolours only the body of the pictogram, rebuilt from the embedded geometry
    // (see VehicleCarGeometry). null keeps the original brand-green drawable untouched. [VEH-COLOR-001]
    if (color != null && resolved != null) {
        val image = remember(resolved, color, isDark) {
            buildCarImageVector(isoCarSpec(resolved), carPaletteOf(color, isDark), isDark, ISO_WHEEL_STROKE_DARK)
        }
        return rememberVectorPainter(image)
    }
    return when {
        resolved != null -> painterResource(if (isDark) resolved.iconDark else resolved.icon)
        size != null -> rememberVectorPainter(size.icon)
        else -> rememberVectorPainter(fallback)
    }
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

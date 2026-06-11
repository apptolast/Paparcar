package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.Image
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import io.apptolast.paparcar.domain.model.CarbodyType
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
@Composable
fun vehicleIconPainter(
    carbody: CarbodyType?,
    size: VehicleSize?,
    fallback: ImageVector = PaparcarIcons.VehicleCar,
    defaultCarbody: CarbodyType? = null,
): Painter {
    val resolved = carbody ?: size?.fallbackCarbody() ?: defaultCarbody
    return when {
        resolved != null -> painterResource(resolved.icon)
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
 * The [tint] colour is applied via [ColorFilter.tint] so the outline still
 * follows the surrounding theme (e.g. amber-on-amber on the parked marker).
 * Pass [Color.Unspecified] to render the pictogram in its native colour.
 */
@Composable
fun VehicleIcon(
    carbody: CarbodyType?,
    size: VehicleSize?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    fallback: ImageVector = PaparcarIcons.VehicleCar,
    defaultCarbody: CarbodyType? = null,
) {
    Image(
        painter = vehicleIconPainter(
            carbody = carbody,
            size = size,
            fallback = fallback,
            defaultCarbody = defaultCarbody,
        ),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint),
    )
}

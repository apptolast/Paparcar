package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.VehicleColor
import com.rndeveloper.paparcar.domain.model.VehicleSize

/**
 * Top-down (aerial) vehicle pictogram with a baked heading wedge — used as the **location-active**
 * driving puck (the user's own car, rotated to the GPS bearing while detection is running). The
 * pictogram points "up" in its own frame; the marker composable rotates it to the heading.
 * [MAP-ICONS-V2]
 *
 * Rendered through the shared geometry builder so it always carries the white body + wheel border in
 * both themes (theme-neutral colours still follow surface luminance for dark glass/shadow). A non-null
 * [color] recolours only the body; with no colour the identity brand-green palette reproduces the
 * original artwork. A two-wheeler draws its own aerial pictogram; a car with no carbody falls back to
 * its length tier's canonical body, then SEDAN. [CAR-WHITE-BORDER-001]
 */
@Composable
fun VehicleTopdownIcon(
    carbody: CarbodyType?,
    size: VehicleSize?,
    modifier: Modifier = Modifier,
    color: VehicleColor? = null,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < TOPDOWN_DARK_LUMINANCE
    val art = vehicleArtOf(carbody = carbody, size = size, defaultCarbody = CarbodyType.SEDAN)
        ?: VehicleArt.Car(CarbodyType.SEDAN)
    val palette = color?.let { carPaletteOf(it, isDark) } ?: defaultCarPalette(topdown = true)
    val image = remember(art, color, isDark) {
        buildCarImageVector(art.topdownSpec(), palette, isDark, TOPDOWN_WHEEL_STROKE)
    }
    Image(
        painter = rememberVectorPainter(image),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

private const val TOPDOWN_DARK_LUMINANCE = 0.5f

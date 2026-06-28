package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.fallbackCarbody
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.ic_car_topdown_family
import paparcar.composeapp.generated.resources.ic_car_topdown_family_dark
import paparcar.composeapp.generated.resources.ic_car_topdown_hatchback_medium
import paparcar.composeapp.generated.resources.ic_car_topdown_hatchback_medium_dark
import paparcar.composeapp.generated.resources.ic_car_topdown_hatchback_small
import paparcar.composeapp.generated.resources.ic_car_topdown_hatchback_small_dark
import paparcar.composeapp.generated.resources.ic_car_topdown_pickup
import paparcar.composeapp.generated.resources.ic_car_topdown_pickup_dark
import paparcar.composeapp.generated.resources.ic_car_topdown_sedan
import paparcar.composeapp.generated.resources.ic_car_topdown_sedan_dark
import paparcar.composeapp.generated.resources.ic_car_topdown_suv_large
import paparcar.composeapp.generated.resources.ic_car_topdown_suv_large_dark
import paparcar.composeapp.generated.resources.ic_car_topdown_suv_medium
import paparcar.composeapp.generated.resources.ic_car_topdown_suv_medium_dark
import paparcar.composeapp.generated.resources.ic_car_topdown_suv_small
import paparcar.composeapp.generated.resources.ic_car_topdown_suv_small_dark
import paparcar.composeapp.generated.resources.ic_car_topdown_van_commercial
import paparcar.composeapp.generated.resources.ic_car_topdown_van_commercial_dark
import paparcar.composeapp.generated.resources.ic_car_topdown_van_light
import paparcar.composeapp.generated.resources.ic_car_topdown_van_light_dark

/**
 * Top-down (aerial) vehicle pictogram with a baked heading wedge — used as the **location-active**
 * driving puck (the user's own car, rotated to the GPS bearing while detection is running). The
 * pictogram points "up" in its own frame; the marker composable rotates it to the heading.
 * [MAP-ICONS-V2]
 *
 * Theme-aware (a dark variant with thin white outline) via surface luminance, mirroring
 * [vehicleIconPainter]. Falls back to the length tier's canonical carbody (then SEDAN) so a vehicle
 * with no explicit carbody still renders a real top-down silhouette.
 */
@Composable
fun VehicleTopdownIcon(
    carbody: CarbodyType?,
    size: VehicleSize?,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < TOPDOWN_DARK_LUMINANCE
    val resolved = carbody ?: size?.fallbackCarbody() ?: CarbodyType.SEDAN
    Image(
        painter = painterResource(topdownDrawable(resolved, isDark)),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

private const val TOPDOWN_DARK_LUMINANCE = 0.5f

private fun topdownDrawable(carbody: CarbodyType, isDark: Boolean): DrawableResource = when (carbody) {
    CarbodyType.HATCHBACK_SMALL -> if (isDark) Res.drawable.ic_car_topdown_hatchback_small_dark else Res.drawable.ic_car_topdown_hatchback_small
    CarbodyType.SUV_SMALL -> if (isDark) Res.drawable.ic_car_topdown_suv_small_dark else Res.drawable.ic_car_topdown_suv_small
    CarbodyType.HATCHBACK_MEDIUM -> if (isDark) Res.drawable.ic_car_topdown_hatchback_medium_dark else Res.drawable.ic_car_topdown_hatchback_medium
    CarbodyType.SUV_MEDIUM -> if (isDark) Res.drawable.ic_car_topdown_suv_medium_dark else Res.drawable.ic_car_topdown_suv_medium
    CarbodyType.SEDAN -> if (isDark) Res.drawable.ic_car_topdown_sedan_dark else Res.drawable.ic_car_topdown_sedan
    CarbodyType.FAMILY_LONG -> if (isDark) Res.drawable.ic_car_topdown_family_dark else Res.drawable.ic_car_topdown_family
    CarbodyType.SUV_LARGE -> if (isDark) Res.drawable.ic_car_topdown_suv_large_dark else Res.drawable.ic_car_topdown_suv_large
    CarbodyType.VAN_LIGHT -> if (isDark) Res.drawable.ic_car_topdown_van_light_dark else Res.drawable.ic_car_topdown_van_light
    CarbodyType.VAN_COMMERCIAL -> if (isDark) Res.drawable.ic_car_topdown_van_commercial_dark else Res.drawable.ic_car_topdown_van_commercial
    CarbodyType.PICKUP -> if (isDark) Res.drawable.ic_car_topdown_pickup_dark else Res.drawable.ic_car_topdown_pickup
}

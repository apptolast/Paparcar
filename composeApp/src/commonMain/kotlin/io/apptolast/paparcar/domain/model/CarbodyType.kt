package io.apptolast.paparcar.domain.model

import org.jetbrains.compose.resources.DrawableResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.ic_car_family
import paparcar.composeapp.generated.resources.ic_car_family_dark
import paparcar.composeapp.generated.resources.ic_car_hatchback_medium
import paparcar.composeapp.generated.resources.ic_car_hatchback_medium_dark
import paparcar.composeapp.generated.resources.ic_car_hatchback_small
import paparcar.composeapp.generated.resources.ic_car_hatchback_small_dark
import paparcar.composeapp.generated.resources.ic_car_pickup
import paparcar.composeapp.generated.resources.ic_car_pickup_dark
import paparcar.composeapp.generated.resources.ic_car_sedan
import paparcar.composeapp.generated.resources.ic_car_sedan_dark
import paparcar.composeapp.generated.resources.ic_car_suv_large
import paparcar.composeapp.generated.resources.ic_car_suv_large_dark
import paparcar.composeapp.generated.resources.ic_car_suv_medium
import paparcar.composeapp.generated.resources.ic_car_suv_medium_dark
import paparcar.composeapp.generated.resources.ic_car_suv_small
import paparcar.composeapp.generated.resources.ic_car_suv_small_dark
import paparcar.composeapp.generated.resources.ic_car_van_commercial
import paparcar.composeapp.generated.resources.ic_car_van_commercial_dark
import paparcar.composeapp.generated.resources.ic_car_van_light
import paparcar.composeapp.generated.resources.ic_car_van_light_dark

/**
 * Body-shape category of a [VehicleType.CAR] vehicle.
 *
 * Layered on top of [VehicleSize] to add the second dimension the user cares
 * about when judging a freed parking spot: width and ceiling height, not just
 * length. The pair `(carbodyType, sizeCategory)` is what powers the Spot Fit
 * indicator on the home peek (OPTIMAL / FITS / DOES_NOT_FIT).
 *
 * Each entry resolves a [sizeCategory] so the legacy length-based logic
 * (geofence radius, compatibility filter) keeps working without a separate
 * lookup. The [icon] is shown on the registration card, the map marker, and
 * the spot peek's "left-by" subline.
 *
 * [icon] is the light-theme isometric pictogram; [iconDark] is the dark-theme
 * variant (thin white outline on body + wheels, brighter green) from the
 * Bolt-green design system. `vehicleIconPainter` selects by the active theme.
 * Mapping to the new icon set is positional 1→10 (the design renamed some
 * mid-tier shapes — e.g. HATCHBACK_MEDIUM uses the "familiar" silhouette and
 * FAMILY_LONG the "monovolumen" — but the order matches). [BOLT-MARKERS-001]
 */
enum class CarbodyType(
    val sizeCategory: VehicleSize,
    val icon: DrawableResource,
    val iconDark: DrawableResource,
) {
    HATCHBACK_SMALL(VehicleSize.MICRO_SMALL, Res.drawable.ic_car_hatchback_small, Res.drawable.ic_car_hatchback_small_dark),
    SUV_SMALL(VehicleSize.MICRO_SMALL, Res.drawable.ic_car_suv_small, Res.drawable.ic_car_suv_small_dark),
    HATCHBACK_MEDIUM(VehicleSize.MEDIUM_SUV, Res.drawable.ic_car_hatchback_medium, Res.drawable.ic_car_hatchback_medium_dark),
    SUV_MEDIUM(VehicleSize.MEDIUM_SUV, Res.drawable.ic_car_suv_medium, Res.drawable.ic_car_suv_medium_dark),
    SEDAN(VehicleSize.LARGE_SEDAN, Res.drawable.ic_car_sedan, Res.drawable.ic_car_sedan_dark),
    FAMILY_LONG(VehicleSize.LARGE_SEDAN, Res.drawable.ic_car_family, Res.drawable.ic_car_family_dark),
    SUV_LARGE(VehicleSize.LARGE_SEDAN, Res.drawable.ic_car_suv_large, Res.drawable.ic_car_suv_large_dark),
    VAN_LIGHT(VehicleSize.VAN_HIGH, Res.drawable.ic_car_van_light, Res.drawable.ic_car_van_light_dark),
    VAN_COMMERCIAL(VehicleSize.VAN_HIGH, Res.drawable.ic_car_van_commercial, Res.drawable.ic_car_van_commercial_dark),
    PICKUP(VehicleSize.VAN_HIGH, Res.drawable.ic_car_pickup, Res.drawable.ic_car_pickup_dark),
}

/**
 * Canonical [CarbodyType] used as a visual fallback when a vehicle stores only
 * the length-based [VehicleSize] (legacy rows, or motorcycles/scooters/bikes
 * where the body dimension does not apply).
 *
 * Returns null for [VehicleSize.MOTORCYCLE] — bikes have no carbody, so the UI
 * falls back to the legacy two-wheel ImageVector via `vehicleIconPainter`.
 */
fun VehicleSize.fallbackCarbody(): CarbodyType? = when (this) {
    VehicleSize.MOTORCYCLE -> null
    VehicleSize.MICRO_SMALL -> CarbodyType.HATCHBACK_SMALL
    VehicleSize.MEDIUM_SUV -> CarbodyType.HATCHBACK_MEDIUM
    VehicleSize.LARGE_SEDAN -> CarbodyType.SEDAN
    VehicleSize.VAN_HIGH -> CarbodyType.VAN_LIGHT
}

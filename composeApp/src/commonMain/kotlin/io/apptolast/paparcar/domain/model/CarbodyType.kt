package io.apptolast.paparcar.domain.model

import org.jetbrains.compose.resources.DrawableResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.ic_car_family
import paparcar.composeapp.generated.resources.ic_car_hatchback_medium
import paparcar.composeapp.generated.resources.ic_car_hatchback_small
import paparcar.composeapp.generated.resources.ic_car_pickup
import paparcar.composeapp.generated.resources.ic_car_sedan
import paparcar.composeapp.generated.resources.ic_car_suv_large
import paparcar.composeapp.generated.resources.ic_car_suv_medium
import paparcar.composeapp.generated.resources.ic_car_suv_small
import paparcar.composeapp.generated.resources.ic_car_van_commercial
import paparcar.composeapp.generated.resources.ic_car_van_light

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
 */
enum class CarbodyType(
    val sizeCategory: VehicleSize,
    val icon: DrawableResource,
) {
    HATCHBACK_SMALL(VehicleSize.MICRO_SMALL, Res.drawable.ic_car_hatchback_small),
    SUV_SMALL(VehicleSize.MICRO_SMALL, Res.drawable.ic_car_suv_small),
    HATCHBACK_MEDIUM(VehicleSize.MEDIUM_SUV, Res.drawable.ic_car_hatchback_medium),
    SUV_MEDIUM(VehicleSize.MEDIUM_SUV, Res.drawable.ic_car_suv_medium),
    SEDAN(VehicleSize.LARGE_SEDAN, Res.drawable.ic_car_sedan),
    FAMILY_LONG(VehicleSize.LARGE_SEDAN, Res.drawable.ic_car_family),
    SUV_LARGE(VehicleSize.LARGE_SEDAN, Res.drawable.ic_car_suv_large),
    VAN_LIGHT(VehicleSize.VAN_HIGH, Res.drawable.ic_car_van_light),
    VAN_COMMERCIAL(VehicleSize.VAN_HIGH, Res.drawable.ic_car_van_commercial),
    PICKUP(VehicleSize.VAN_HIGH, Res.drawable.ic_car_pickup),
}

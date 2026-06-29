package io.apptolast.paparcar.domain.model

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
 * lookup. The pictogram for each shape is built in the presentation layer
 * (`VehicleCarGeometry` / `vehicleIconPainter`) — domain stays Kotlin-pure with
 * no drawable references. [CAR-WHITE-BORDER-001]
 */
enum class CarbodyType(
    val sizeCategory: VehicleSize,
) {
    HATCHBACK_SMALL(VehicleSize.MICRO_SMALL),
    SUV_SMALL(VehicleSize.MICRO_SMALL),
    HATCHBACK_MEDIUM(VehicleSize.MEDIUM_SUV),
    SUV_MEDIUM(VehicleSize.MEDIUM_SUV),
    SEDAN(VehicleSize.LARGE_SEDAN),
    FAMILY_LONG(VehicleSize.LARGE_SEDAN),
    SUV_LARGE(VehicleSize.LARGE_SEDAN),
    VAN_LIGHT(VehicleSize.VAN_HIGH),
    VAN_COMMERCIAL(VehicleSize.VAN_HIGH),
    PICKUP(VehicleSize.VAN_HIGH),
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

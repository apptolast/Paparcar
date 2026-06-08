package io.apptolast.paparcar.domain.model

/**
 * Approximate length-based category of the user's vehicle.
 *
 * Drives Spot compatibility (does my car fit this freed spot?) and geofence
 * radius calibration. For [VehicleType.CAR] vehicles it is derived from
 * [CarbodyType.sizeCategory]; for motorcycles / scooters / bikes it is
 * always [MOTORCYCLE].
 */
enum class VehicleSize {
    /** Motorcycles, scooters and bicycles. No carbody type applies. */
    MOTORCYCLE,
    /** Cars under ~4.10 m length (city / supermini). */
    MICRO_SMALL,
    /** Cars from ~4.11 m to ~4.55 m (compact / medium SUV). */
    MEDIUM_SUV,
    /** Cars from ~4.56 m to ~5.00 m (sedan / estate / large SUV). */
    LARGE_SEDAN,
    /** Cars over ~5.00 m length OR over ~1.82 m height (vans, MPVs, pickups). */
    VAN_HIGH,
}

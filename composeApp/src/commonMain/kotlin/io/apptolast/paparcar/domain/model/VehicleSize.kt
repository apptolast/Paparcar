package io.apptolast.paparcar.domain.model

/**
 * Approximate size category of the user's vehicle.
 * Shown on [Spot] so nearby drivers can judge if the space suits their car.
 * Mandatory — the user must select one during vehicle registration.
 */
enum class VehicleSize {
    MOTO,
    SMALL,
    MEDIUM,
    LARGE,
    VAN,
}

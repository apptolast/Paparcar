package com.rndeveloper.paparcar.domain.model

/**
 * Environmental badge of the vehicle — Spain's DGT names, presented abroad as a generic
 * "environmental label". Optional at registration; stored today, consumed when regulated-zone
 * rules land (SER). [VEH-FUEL-001]
 */
enum class EnergyLabel {
    ZERO,
    ECO,
    C,
    B,
    /** The vehicle has no badge (older than the scheme allows). Distinct from "not answered",
     *  which is a null [com.rndeveloper.paparcar.domain.model.Vehicle.energyLabel]. */
    NONE,
}

package com.rndeveloper.paparcar.domain.vehicle

import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.VehicleType

/**
 * Default consumption per 100 km when the user has not overridden it, decided in ONE place.
 * [VEH-FUEL-001]
 *
 * Round European fleet figures, deliberately conservative and NAMED — an estimate labelled
 * "aprox." downstream can afford round numbers; what it cannot afford is the same number
 * hardcoded in three screens. The unit is implicit in the fuel type ([FuelType.usesLiters] /
 * [FuelType.usesElectricity]); a plug-in hybrid carries BOTH figures.
 */
object FuelDefaults {

    /** Litres (or kWh for [FuelType.ELECTRIC]) per 100 km for the fuel's PRIMARY energy. */
    fun defaultConsumptionPer100(fuelType: FuelType, vehicleType: VehicleType): Float {
        // Asked of the type, not spelled as == MOTORCYCLE — a new type must answer the scale
        // question itself. [VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001]
        if (vehicleType.consumesAtMotorcycleScale) return MOTORCYCLE_LITERS_PER_100
        return when (fuelType) {
            FuelType.GASOLINE -> GASOLINE_LITERS_PER_100
            FuelType.DIESEL -> DIESEL_LITERS_PER_100
            FuelType.LPG_CNG -> LPG_LITERS_PER_100
            FuelType.HYBRID -> HYBRID_LITERS_PER_100
            FuelType.PLUG_IN_HYBRID -> PHEV_LITERS_PER_100
            FuelType.ELECTRIC -> EV_KWH_PER_100
        }
    }

    /** kWh/100 km of the SECONDARY (electric) energy — only a PHEV has one. */
    fun defaultElectricConsumptionPer100(fuelType: FuelType): Float? =
        if (fuelType.hasSecondaryElectric) PHEV_KWH_PER_100 else null

    const val GASOLINE_LITERS_PER_100 = 6.5f
    const val DIESEL_LITERS_PER_100 = 5.5f
    const val LPG_LITERS_PER_100 = 8.5f
    const val HYBRID_LITERS_PER_100 = 5.0f
    const val PHEV_LITERS_PER_100 = 3.0f
    const val PHEV_KWH_PER_100 = 12.0f
    const val EV_KWH_PER_100 = 17.0f
    const val MOTORCYCLE_LITERS_PER_100 = 3.5f
}

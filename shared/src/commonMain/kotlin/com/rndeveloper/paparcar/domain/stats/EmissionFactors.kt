package com.rndeveloper.paparcar.domain.stats

import com.rndeveloper.paparcar.domain.model.FuelType

/**
 * CO₂ per unit of energy, decided in ONE place. [STATS-CORE-001]
 *
 * Standard combustion factors (kg CO₂ per litre burned — chemistry, not opinion) plus a European
 * grid-mix average for the electric side. The figures feed an estimate the UI labels "aprox.";
 * round, NAMED constants are the right precision for that.
 */
object EmissionFactors {

    /** kg CO₂ per litre of this fuel. Hybrids burn gasoline; null for a pure EV (no litres). */
    fun kgCo2PerLiter(fuel: FuelType): Double? = when (fuel) {
        FuelType.GASOLINE, FuelType.HYBRID, FuelType.PLUG_IN_HYBRID -> KG_CO2_PER_LITER_GASOLINE
        FuelType.DIESEL -> KG_CO2_PER_LITER_DIESEL
        FuelType.LPG_CNG -> KG_CO2_PER_LITER_LPG
        FuelType.ELECTRIC -> null
    }

    const val KG_CO2_PER_LITER_GASOLINE = 2.31
    const val KG_CO2_PER_LITER_DIESEL = 2.68
    const val KG_CO2_PER_LITER_LPG = 1.66

    /** EU-27 average grid mix. A per-country factor is a follow-up (FUEL-PRICES-001 resolves the
     *  country); the estimate stays labelled approximate either way. */
    const val KG_CO2_PER_KWH_EU_MIX = 0.25
}

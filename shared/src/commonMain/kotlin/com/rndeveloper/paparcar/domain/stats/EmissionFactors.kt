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

    /**
     * kg CO₂ per litre of this fuel. Null for a pure EV (no litres).
     *
     * Chemistry answers about the LITRE, so the question is asked of the product the car buys —
     * hybrids burn petrol, and that sentence is [FuelType.pumpProduct]'s to say, not a third
     * private copy of it. [FUEL-A-HYBRID-FILLS-UP-WITH-PETROL-001]
     */
    fun kgCo2PerLiter(fuel: FuelType): Double? = when (fuel.pumpProduct) {
        FuelType.GASOLINE -> KG_CO2_PER_LITER_GASOLINE
        FuelType.DIESEL -> KG_CO2_PER_LITER_DIESEL
        FuelType.LPG_CNG -> KG_CO2_PER_LITER_LPG
        // Unreachable: pumpProduct maps both hybrids onto GASOLINE. Named rather than `else` so a
        // new FuelType has to come back here and answer.
        FuelType.ELECTRIC, FuelType.HYBRID, FuelType.PLUG_IN_HYBRID -> null
    }

    const val KG_CO2_PER_LITER_GASOLINE = 2.31
    const val KG_CO2_PER_LITER_DIESEL = 2.68
    const val KG_CO2_PER_LITER_LPG = 1.66

    /** EU-27 average grid mix. A per-country factor is a follow-up (FUEL-PRICES-001 resolves the
     *  country); the estimate stays labelled approximate either way. */
    const val KG_CO2_PER_KWH_EU_MIX = 0.25
}

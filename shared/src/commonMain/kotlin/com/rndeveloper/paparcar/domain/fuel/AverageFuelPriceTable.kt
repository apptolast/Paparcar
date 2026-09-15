package com.rndeveloper.paparcar.domain.fuel

import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money

/**
 * What one litre costs on average in one country. [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
 *
 * **Always per litre, always in that country's own currency.** Fourteen countries publish their
 * prices per gallon, and normalising them is a job for the boundary that reads the feed — the
 * generator for the embedded table, the parser for the network one. By the time a price is one of
 * these, the unit question is already answered and cannot be got wrong again. A gallon reaching
 * this class would inflate a US drive by 3.79× in silence, which is exactly the kind of bug that
 * has no symptom until someone checks the arithmetic.
 *
 * A missing fuel is null and stays null: a country that sells no LPG has no LPG price, and that is
 * an answer. Never a zero, never a neighbour's figure.
 */
data class CountryFuelPrices(
    /** ISO 4217 code — "EUR", "PLN", "RON", "TRY". Not everything is euros. */
    val currency: String,
    val gasoline: Double?,
    val diesel: Double?,
    val lpg: Double?,
)

/**
 * The national averages the app can quote right now, and when they were measured.
 *
 * One table, two possible origins — the generated snapshot in the binary, or the last refresh from
 * OpenVan — and deliberately the SAME type for both, so nothing downstream can start caring which
 * one it got. The ladder that does care about origin is [FuelPriceOrigin], and both of these are
 * the same rung of it: `NationalAverage`.
 */
data class AverageFuelPriceTable(
    /** ISO date the figures were measured. The UI dates the number; an undated price is invented. */
    val asOf: String,
    val rows: Map<String, CountryFuelPrices>,
) {

    /**
     * Average litre price for [fuel] in [countryCode], or null where this table cannot answer —
     * an unknown country, a fuel this country does not sell, or a car that burns no litres at all.
     *
     * Null is a real answer: the cost tile simply does not appear. Never a zero, and never a figure
     * wearing a currency the driver has not seen.
     */
    fun averageLitre(countryCode: String?, fuel: FuelType): Money? {
        if (!fuel.usesLiters) return null
        val code = countryCode?.uppercase() ?: return null
        val row = rows[code] ?: return null
        // The table prices PRODUCTS — the three a pump sells — so the car's powertrain is mapped
        // onto one by the shared [FuelType.pumpProduct]. ⛔ It used to be a private copy of that
        // rule here, and a second private copy in EmissionFactors, while the station lane had none:
        // the same sentence written twice and missing once is how every hybrid driver ended up with
        // a priceless map. [FUEL-A-HYBRID-FILLS-UP-WITH-PETROL-001]
        val amount = when (fuel.pumpProduct) {
            FuelType.GASOLINE -> row.gasoline
            FuelType.DIESEL -> row.diesel
            FuelType.LPG_CNG -> row.lpg
            // Unreachable: pumpProduct only ever returns the three products a pump sells, and
            // usesLiters already excluded the electric case. Named rather than `else` so a new
            // FuelType has to come back here and answer. [VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001]
            FuelType.ELECTRIC, FuelType.HYBRID, FuelType.PLUG_IN_HYBRID -> null
        } ?: return null
        return Money(amount, row.currency)
    }

    companion object {
        /**
         * The table shipped inside the binary — the floor every other rung falls back to.
         *
         * Lives here rather than in [EmbeddedAverageFuelPrices] because that file is GENERATED:
         * anything hand-written in it would be erased by the next run of the generator.
         */
        val Embedded: AverageFuelPriceTable by lazy {
            AverageFuelPriceTable(EmbeddedAverageFuelPrices.AS_OF, EmbeddedAverageFuelPrices.ROWS)
        }
    }
}

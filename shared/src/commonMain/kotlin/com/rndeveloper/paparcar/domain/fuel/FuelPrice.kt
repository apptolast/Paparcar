package com.rndeveloper.paparcar.domain.fuel

import com.rndeveloper.paparcar.domain.model.Money

/**
 * What one unit of energy costs — a litre, or a kWh — and WHERE that figure came from.
 * [FUEL-PRICE-RESOLVER-001]
 *
 * The provenance travels with the number on purpose. A spend computed from the national average and
 * one computed from the price the user actually pays are not the same claim, and the screen that
 * shows the figure has to be able to say which it is holding. Same reason `ScopedDistance` carries
 * its coverage: a figure that cannot describe itself gets described by whoever reads it.
 * [UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001]
 */
data class FuelPrice(
    /** Cost of one litre or one kWh, in the currency the source quotes. */
    val perUnit: Money,
    val source: FuelPriceOrigin,
    /**
     * ISO date (`yyyy-MM-dd`) the figure was measured, when the source can date itself. Null when
     * it genuinely cannot: the price the USER typed has no measurement date, and saying otherwise
     * would invent one. [FUEL-AN-AVERAGE-MUST-SAY-WHEN-IT-WAS-MEASURED-001]
     *
     * ⛔ It lives HERE, inside the price, and not as a second field beside it. The doctrine this
     * class was written for — *a figure that cannot describe itself gets described by whoever reads
     * it* — was only half kept: the provenance travelled and the date did not. Two parallel fields
     * could drift apart; one object cannot disagree with itself.
     */
    val measuredOn: String? = null,
)

/**
 * Where a [FuelPrice] came from, best first.
 *
 * [Station] is declared here although its adapter arrives with `FUEL-STATIONS-001`: an exhaustive
 * `when` over this enum is what will force every consumer to answer for the missing rung the day it
 * lands, instead of a new value slipping past an `else`. Same reason `VehicleType` answers its own
 * questions. [VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001]
 */
enum class FuelPriceOrigin {
    /** The user typed what they actually pay. Nothing outranks this. */
    UserOverride,

    /** A real pump near the car, from a national adapter. Arrives with FUEL-STATIONS-001. */
    Station,

    /** The national average shipped in the binary — no network, no precision, and it says so. */
    NationalAverage,
}

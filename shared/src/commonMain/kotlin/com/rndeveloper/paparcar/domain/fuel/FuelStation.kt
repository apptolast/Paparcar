package com.rndeveloper.paparcar.domain.fuel

import com.rndeveloper.paparcar.domain.model.FuelType

/**
 * Whether a station serves a given fuel — with **"we don't know" as a first-class answer**.
 * [FUEL-STATIONS-LIST-001]
 *
 * Two sources measured on 09-09-2026 disagree about what silence means, and collapsing them would
 * be wrong in one direction or the other:
 * - The Spanish feed reports "does not sell it" as an **empty string**, so silence there is a fact:
 *   265 of Cádiz's 292 stations state they have no LPG.
 * - OpenStreetMap tags `fuel:diesel=yes` on only **44%** of stations (11 of 25 measured in Madrid),
 *   and the other 56% are simply untagged. Reading that as "no diesel" would hide most of the
 *   stations in the world from most of the drivers in the world.
 *
 * So the same absence means [NOT_SOLD] in one source and [UNKNOWN] in the other, and only the source
 * knows which. Hiding a station is allowed on [NOT_SOLD]; on [UNKNOWN] it must still be shown,
 * without a claim about the pump.
 */
enum class FuelAvailability { SOLD, NOT_SOLD, UNKNOWN }

/**
 * A place you can refuel, and what it charges. [FUEL-STATIONS-LIST-001]
 *
 * [prices] holds only what this station is known to charge. An absent entry is never a zero — the
 * feeds report "no lo vende" as an empty string, and reading that as 0,000 € would put a free
 * petrol station at the top of a list whose whole job is ranking by price.
 *
 * [name] is nullable because not every source has one. Spain's feed carries the brand and OSM names
 * 100% of what it holds, but France's official feed carries none — which is why France gets its
 * names from OSM, joined on the official station id OSM stores in `ref:FR:prix-carburants`.
 * ⛔ Not joined by proximity: measured, that names only half the stations or mislabels one in ten.
 * [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
 */
data class FuelStation(
    /** Stable id WITHIN its source — the Spanish feed's `IDEESS`, an OSM element id, etc. */
    val id: String,
    val name: String?,
    val address: String?,
    val town: String?,
    val latitude: Double,
    val longitude: Double,
    /** Each price carries its own hour — see [StationPrice] for why that is not a response-level
     *  property. */
    val prices: Map<FuelType, StationPrice>,
    /**
     * What this station is known to serve. A fuel **absent from this map is [FuelAvailability.UNKNOWN]**,
     * which is why the map is keyed by verdict instead of being a plain set of what it sells: a set
     * cannot tell "we asked and it does not" from "nobody has said".
     */
    val availability: Map<FuelType, FuelAvailability> = emptyMap(),
    /** Opening hours as the source words them. Not parsed: 24 h, "L-D: 07:00-22:00" and "24H" all
     *  appear in the same feed, and a wrong parse would close a station that is open. */
    val schedule: String? = null,
) {
    /**
     * What we can honestly say about [fuel] here. A known price is proof of sale, so it outranks
     * whatever the availability map says.
     */
    fun availabilityOf(fuel: FuelType): FuelAvailability = when {
        prices.containsKey(fuel) -> FuelAvailability.SOLD
        else -> availability[fuel] ?: FuelAvailability.UNKNOWN
    }

    /** True only when this station is known NOT to serve [fuel] — the one case it may be hidden. */
    fun isKnownNotToSell(fuel: FuelType): Boolean =
        availabilityOf(fuel) == FuelAvailability.NOT_SOLD
}

package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource

/**
 * Picks who answers for a country, and falls back when they cannot. [FUEL-STATIONS-LIST-001]
 *
 * It is itself a [FuelStationsSource], so nothing upstream has to know that "the source" is really a
 * shortlist — adding France is adding one entry to [national], not touching a use case.
 *
 * **The fallback is the whole point.** A national feed can be down, rate-limited, or simply unable
 * to place the user (Spain's is queried by province, so a region name it does not recognise stops it
 * dead). Dropping to OSM there turns "nothing here" into "here is where you can refuel, we just
 * cannot price it" — the honest degradation, and the one the user can act on.
 *
 * ⛔ It never degrades the other way. A national price is never replaced by, mixed with, or corrected
 * against a second opinion: the source that answers, answers whole.
 */
class FuelStationsRegistry(
    private val national: List<FuelStationsSource>,
    private val fallback: FuelStationsSource,
) : FuelStationsSource {

    /** Always: the fallback covers the rest of the world by construction. */
    override fun covers(countryCode: String): Boolean = true

    override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> {
        val preferred = national.firstOrNull { it.covers(query.countryCode) }
            ?: return fallback.stationsNear(query)

        // ⛔ An EMPTY list from a national feed is a real answer — there is nothing within the radius
        // — so only a FAILURE hands over. Retrying a valid "none" against OSM would quietly turn a
        // priced country into an unpriced one, and the user would never see why the prices vanished.
        return preferred.stationsNear(query).recoverCatching {
            // ⛔ The fallback's answer is MARKED. Without this the lane could not tell «this country
            // publishes no prices» — true where no national feed exists — from «the national feed
            // failed and these are just the forecourts», and it said the first while the second was
            // happening: a verdict about a whole country that nobody verified.
            // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
            // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
            fallback.stationsNear(query).getOrThrow().copy(didFallBack = true)
        }
    }
}

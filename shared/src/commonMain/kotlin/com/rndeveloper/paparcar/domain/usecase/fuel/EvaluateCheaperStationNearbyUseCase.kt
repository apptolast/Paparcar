package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.util.haversineMeters

/** What the saving is being measured AGAINST — it goes in the copy, so it is part of the verdict. */
enum class CheaperReference {
    /** A station the user follows. The strong case: «cheaper than yours». */
    FOLLOWED,

    /** The median of what is around. Used when nothing is followed yet. */
    AREA_MEDIAN,
}

/**
 * There is a cheaper pump near you than the one you use.
 * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
 */
data class CheaperStationNearby(
    val station: FuelStation,
    val fuelType: FuelType,
    val price: Money,
    /** Positive, in the currency's unit: how much less per litre than [referencePrice]. */
    val savingPerUnit: Double,
    val referencePrice: Money,
    val referenceName: String?,
    val reference: CheaperReference,
    val distanceMeters: Double,
)

/**
 * Is any forecourt around worth the detour, compared with the one this driver actually uses.
 * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
 *
 * A verdict, not a predicate: it produces the notification's sentence, reference included — a saving
 * with no stated reference is a number the user cannot check.
 *
 * ⛔ **The reference is never the most expensive station around.** That version always fires: pick
 * the worst pump in town and everything is a bargain. It is the followed station when there is one,
 * and otherwise the MEDIAN of what is priced nearby — the value that makes «cheaper» mean «cheaper
 * than normal here».
 */
class EvaluateCheaperStationNearbyUseCase {

    /**
     * @param candidates stations near [latitude]/[longitude] **keyed by `source|id`**, as the feed
     *        answered them. Keyed and not a plain list because the identity is what decides which of
     *        them is the reference, and re-deriving it here from a bare id is the collision the whole
     *        composite key exists to prevent.
     * @param referenceStationKeys followed stations, in that same form. Their prices come from
     *        [candidates] when the answer holds them, which is the common case: your usual pump is
     *        usually near you.
     */
    operator fun invoke(
        latitude: Double,
        longitude: Double,
        fuelType: FuelType,
        candidates: Map<String, FuelStation>,
        referenceStationKeys: Set<String>,
        maxDistanceMeters: Double = MAX_DETOUR_METERS,
        minSavingPerUnit: Double = MIN_SAVING_PER_UNIT,
    ): CheaperStationNearby? {
        val priced = candidates.mapNotNull { (key, station) ->
            val price = station.prices[fuelType] ?: return@mapNotNull null
            PricedStation(
                station = station,
                key = key,
                price = price.perUnit,
                reportedAtMs = price.reportedAtMs,
                distanceMeters = haversineMeters(
                    latitude, longitude, station.latitude, station.longitude,
                ),
            )
        }
        if (priced.isEmpty()) return null

        // One currency at a time. A border area can answer in two, and the cheapest number across
        // them is not the cheapest price.
        val currency = priced.groupingBy { it.price.currency }.eachCount()
            .maxByOrNull { it.value }?.key ?: return null
        val sameCurrency = priced.filter { it.price.currency == currency }

        val followed = sameCurrency.filter { it.key in referenceStationKeys }
        val reference = followed.minByOrNull { it.distanceMeters }
        val referencePrice = reference?.price ?: medianPrice(sameCurrency, currency) ?: return null
        val referenceKind =
            if (reference != null) CheaperReference.FOLLOWED else CheaperReference.AREA_MEDIAN

        val best = sameCurrency
            .filter { it.key !in referenceStationKeys }
            .filter { it.distanceMeters <= maxDistanceMeters }
            // ⛔ A candidate quoted from an older reading than the reference is not proof of
            // anything: half the "savings" in a stale feed are just two different days compared.
            .filter { it.isAtLeastAsFreshAs(reference) }
            .minByOrNull { it.price.amount }
            ?: return null

        val saving = referencePrice.amount - best.price.amount
        if (saving < minSavingPerUnit) return null

        return CheaperStationNearby(
            station = best.station,
            fuelType = fuelType,
            price = best.price,
            savingPerUnit = saving,
            referencePrice = referencePrice,
            referenceName = reference?.station?.name,
            reference = referenceKind,
            distanceMeters = best.distanceMeters,
        )
    }

    private data class PricedStation(
        val station: FuelStation,
        val key: String,
        val price: Money,
        val reportedAtMs: Long?,
        val distanceMeters: Double,
    ) {
        /** Unknown stamps do not disqualify: most of the world's feeds publish none at all. */
        fun isAtLeastAsFreshAs(other: PricedStation?): Boolean {
            val mine = reportedAtMs ?: return true
            val theirs = other?.reportedAtMs ?: return true
            return mine >= theirs
        }
    }

    /** The middle price of what is around; the even case takes the lower of the two middles. */
    private fun medianPrice(stations: List<PricedStation>, currency: String): Money? {
        if (stations.isEmpty()) return null
        val sorted = stations.map { it.price.amount }.sorted()
        return Money(amount = sorted[(sorted.size - 1) / 2], currency = currency)
    }

    private companion object {
        /**
         * Two kilometres. Beyond that the detour spends what the saving earns: at a typical 7 l/100
         * km, a 4 km round trip burns ~0,28 l — about what a 40 l fill saves at 0,7 c/l.
         */
        const val MAX_DETOUR_METERS = 2_000.0

        /**
         * Three cents per litre — ~1,20 € on a 40 l fill. Chosen as the smallest number that still
         * pays for the detour above rather than as a round figure: below it the alert is asking the
         * driver to spend fuel to save fuel.
         */
        const val MIN_SAVING_PER_UNIT = 0.03
    }
}

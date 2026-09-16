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
 *
 * ⛔⛔ **And the cheapest price in an area is, on average, its oldest one.**
 * [FUEL-A-CHEAP-PRICE-WITH-NO-FLOOR-IS-THE-STALEST-ONE-001] A forecourt that stops reporting keeps
 * its last figure frozen, and since prices climb, the frozen one looks like the bargain of the
 * neighbourhood. Measured against the live Italian register on 16-09-2026, 5 km radius:
 *
 * | | age of the 10 cheapest | age of the rest |
 * |---|---|---|
 * | Roma | **175 h** | 28 h |
 * | Palermo | **171 h** | 34 h |
 * | Napoli | **166 h** | 47 h |
 * | Torino | **115 h** | 23 h |
 * | Milano | 21 h | 22 h |
 *
 * So this verdict needs BOTH gates, and they answer different questions: the comparative one asks
 * «is it fresher than what we compare it against», and [maxPriceAgeMs] asks «is it fresh enough to
 * send somebody there at all». With only the first, an unfollowed driver in Rome was told to detour
 * 1,9 km to save 0,455 €/L — 18 € on a tank — on a price six days old.
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
        /**
         * ⛔ Required, with no default: this verdict stays pure and synchronous, so the one thing
         * it cannot do is read a clock. A defaulted `0L` would have silently aged every price by
         * fifty-six years and muted the alert everywhere.
         */
        nowMs: Long,
        maxDistanceMeters: Double = MAX_DETOUR_METERS,
        minSavingPerUnit: Double = MIN_SAVING_PER_UNIT,
        maxPriceAgeMs: Long = MAX_PRICE_AGE_MS,
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
            // ⛔ …and that gate is INERT exactly where it is needed most: with nothing followed
            // yet — the state every user starts in, and the watch does not require a follow to
            // run — `reference` is null and the line above waves everything through. The floor is
            // what stands there. [FUEL-A-CHEAP-PRICE-WITH-NO-FLOOR-IS-THE-STALEST-ONE-001]
            .filter { it.isFreshEnoughToName(nowMs, maxPriceAgeMs) }
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

        /**
         * Old enough that nobody should be sent there on its word.
         * [FUEL-A-CHEAP-PRICE-WITH-NO-FLOOR-IS-THE-STALEST-ONE-001]
         *
         * ⛔ **An absent stamp still does not disqualify**, same as above and for the same reason:
         * Germany's MTS-K publishes no hour at all and guarantees recency by law, so reading null
         * as "ancient" would mute the alert in a whole country to fix another.
         *
         * ⛔ The floor is on the CANDIDATE only, never on the reference — the asymmetry is the
         * point. A stale candidate makes the app say «drive 1,9 km and pay 1,669 €», which is
         * simply false when you get there; a stale reference only makes the comparison «cheaper
         * than you usually pay» shakier, while the figure the driver acts on stays true. One sends
         * somebody to a wrong number, the other does not.
         */
        fun isFreshEnoughToName(nowMs: Long, maxAgeMs: Long): Boolean {
            val stamp = reportedAtMs ?: return true
            return nowMs - stamp <= maxAgeMs
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

        /**
         * Forty-eight hours, chosen off the measurement rather than as a round number: the median
         * price age in five Italian cities was **18 h**, so this is ~2,7× the normal and clears a
         * weekend without clearing a week.
         *
         * What it costs and what it buys, measured on the same data: 198 of 229 rows survive in
         * Rome and 106 of 139 in Naples, and Rome's recommendation moves from 1,669 € (158 h old)
         * to 1,709 € (5 h) — still a 0,415 €/L saving, and this one is true.
         * [FUEL-A-CHEAP-PRICE-WITH-NO-FLOOR-IS-THE-STALEST-ONE-001]
         */
        const val MAX_PRICE_AGE_MS = 48L * 60 * 60 * 1_000
    }
}

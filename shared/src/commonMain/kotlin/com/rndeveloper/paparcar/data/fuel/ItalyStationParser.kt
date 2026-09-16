@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * Reads Osservaprezzi's zone search — Italy's MIMIT register, asked by radius.
 * [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001]
 *
 * Measured against the live endpoint on 15/16-09-2026, and every rule below is one of those
 * measurements rather than a reading of the docs — there are none.
 *
 *  1. ⛔ **The gate is `success`, never the status code.** A malformed body answers HTTP 200 with
 *     `{"success":false, "results":[]}` and a `center` that repeats the latitude in both
 *     coordinates. Third country, third spelling of the same lie: France's unitless radius answered
 *     200 with `total_count: 0`, Tankerkönig's dead key answers 200 with `ok:false`. ⚠️ The
 *     asymmetry matters — an area with no forecourts answers `success:true, results:[]`, and that
 *     IS an answer.
 *  2. ⛔⛔ **The price depends on who pumps it.** 130 of 229 Roman stations quote the same fuel
 *     twice, `self` and `servito`, and the gap reaches **0,35 €/L**. The `self` quote wins because
 *     it is the mode a driver chooses and the cheaper one; where only `servito` exists — 99 of 229
 *     — the price is shown MARKED, never quietly. The mark rides in
 *     [StationPrice.isSelf], not beside it. Median gap is 0,00, which is what makes this dangerous:
 *     it does not show up in the test drive round the corner.
 *  3. ⛔ **`fuelId` is an OPEN vocabulary and only the base products are quoted.** `registry/fuels`
 *     lists six categories; the data carries **thirty** across four cities, because every chain
 *     registers its own premium (`Blue Diesel`, `Supreme Diesel`, `Hi-Q Diesel`, `Shell V Power`)
 *     and `HVO` alone appears under seven different ids. They are a DIFFERENT product at a
 *     different price — Blue Diesel's median is 2,378 against Gasolio's 2,259 — so quoting one as
 *     diesel would be this ticket's own mistake in another costume. See [ItalyProduct].
 *  4. ⛔ **Methane is not quoted at all**, though the feed carries it. It is sold by the KILO
 *     (`Metano` median 1,799) and would land in the same [FuelType.LPG_CNG] slot as `GPL`, sold by
 *     the litre (median 0,705): the same slot, 2,5× apart, so an LPG car would read its neighbour's
 *     forecourt as a quarter of the price. Spain and France quote only their LPG for this type too;
 *     here there is a number attached to the reason.
 *  5. **The hour is the station's, and it is the NEWEST of its prices.** Verified against
 *     `registry/servicearea/{id}`, which does date each pump: across ALL fuels a single station
 *     spreads up to **1.177 days**, but across the three products quoted here the worst spread in
 *     45 stations was **24,0 hours**, and 93% had none at all. The multi-year tail lives entirely
 *     in the premiums rule 3 already discards. So every price of a station wears that stamp, and
 *     what it can hide is bounded by a day — tighter than Spain, which stamps a whole response.
 *  6. **`brand` is 100% present and 14% of it is not a brand.** `PompeBianche` — "white pumps" — is
 *     the trade word for an unbranded independent, and 216 of 1.544 stations carry it, so left
 *     alone one word would name a seventh of the map. There the station's own name answers.
 *  7. **No address, ever** (null in 229 of 229). The inverse of France, which had the address and
 *     no name — so Italy needs no join with OSM: the row wears a name in every single case.
 */
object ItalyStationParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** @throws IllegalStateException when the feed says `success:false` — see trap 1. */
    fun parse(body: String): List<FuelStation> {
        val response = json.decodeFromString<ItalyResponse>(body)
        check(response.success) { "Osservaprezzi answered success=false" }
        return response.results.withoutSharedCoordinates().mapNotNull { record ->
            val location = record.location ?: return@mapNotNull null
            FuelStation(
                id = record.id.toString(),
                name = record.readName(),
                // Trap 7: the zone search has none. Nothing is invented from the name, which is
                // sometimes a street and sometimes a town and never reliably either.
                address = null,
                town = null,
                latitude = location.lat,
                longitude = location.lng,
                prices = record.readPrices(),
                // Like Germany, the feed cannot state a negative: a pump it does not sell is a
                // pump it does not mention, which is UNKNOWN and never NOT_SOLD.
                availability = emptyMap(),
                // Not in this response, and the detail endpoint's is a per-station extra request.
                schedule = null,
            )
        }
    }

    /**
     * Drops every station that shares its exact point with another — **all of them, not all but
     * one**. [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001]
     *
     * Seen on the device before it was seen in any data: three different UNION forecourts listed at
     * **932 m exactly**, with three different prices. Some operators register several sites against
     * one point — their registered office, by the look of it. Measured over Rome's 229: **220
     * distinct coordinates, 4 shared points, 13 stations (5,7%)**, and the names settle it —
     * `LUDOIL CIVITAVECCHIA` and `MONTEPORZIOCATONE-STAROIL` sit 60-80 km from the centre they claim
     * to be in. ⚠️ Not a universal defect: France answers 37 stations at 37 distinct coordinates.
     *
     * ⛔ **Why all of them.** At most one of a colliding group is really there and nothing in the
     * feed says which, so keeping one would be a coin toss that looks like an ordinary row and is
     * wrong three times in four. Dropping the group costs one real forecourt per point and leaves
     * no row that lies about where it is — the asymmetry the app already chooses when it would
     * rather miss a parking than plant a phantom one.
     *
     * ⛔ **Exact equality, and no distance clustering.** What is provable is that the feed wrote the
     * same number twice; anything fuzzier is a guess. Measured, the near-misses are genuinely
     * ambiguous: `TOTALERG` and `totalerg rampa di uscita` are 5,97 m apart and BOTH real, while
     * five Neapolitan names spread over 18 m are plainly not. Since the two 1,11 m blobs in Rome
     * are themselves two exact groups, they are dropped anyway without any of that.
     */
    private fun List<ItalyRecord>.withoutSharedCoordinates(): List<ItalyRecord> {
        val shared = groupingBy { it.location?.let { p -> p.lat to p.lng } }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        return filterNot { record -> record.location?.let { it.lat to it.lng } in shared }
    }

    /** Trap 6: the chain a driver recognises, unless the chain is the word for "no chain". */
    private fun ItalyRecord.readName(): String? {
        val chain = brand?.trim()?.takeIf { it.isNotEmpty() && !it.equals(UNBRANDED, true) }
        return chain ?: name?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * One price per product a car burns: the self-service quote, or the attended one marked as such.
     *
     * ⛔ The self quote is picked on its own [ItalyQuote.isSelf] flag, never on being the cheaper of
     * the two. They are equal at more than half the stations measured, so "cheapest" would silently
     * relabel an attended price as self exactly where the two agree, and the flag would stop meaning
     * anything the moment it was needed.
     */
    private fun ItalyRecord.readPrices(): Map<FuelType, StationPrice> = buildMap {
        val stamp = insertDate?.let(::parseIsoMillis)
        FuelType.entries.forEach { fuel ->
            // ⛔ Driven from the FUEL, taking the first product that answers, exactly like France
            // and Germany — not from the product list writing into a map. That version passed
            // every test and was one enum entry away from being wrong: two products sharing a
            // [FuelType] would have silently let the LAST declared one overwrite the first, so the
            // safety of `GPL` over `Metano` would have rested on alphabetical luck. Declaration
            // order is now the preference, and it is stated rather than emergent.
            val quote = ItalyProduct.entries
                .filter { it.fuel == fuel }
                .firstNotNullOfOrNull { product -> quoteFor(product) }
                ?: return@forEach
            put(
                fuel,
                StationPrice(
                    perUnit = Money(quote.price, CURRENCY),
                    // Trap 5: the station's stamp, worn by each of its prices.
                    reportedAtMs = stamp,
                    isSelf = quote.isSelf,
                ),
            )
        }
    }

    /** The self quote for [product], or the attended one, or nothing at all. */
    private fun ItalyRecord.quoteFor(product: ItalyProduct): ItalyQuote? {
        val quotes = fuels
            .filter { it.fuelId == product.fuelId }
            // Zero is discarded like in every other adapter, and here it is not theoretical: the
            // feed carries 0,100 € and 1,000 € rows among the premium products.
            .filter { it.price > 0.0 }
        return quotes.firstOrNull { it.isSelf } ?: quotes.firstOrNull()
    }

    /**
     * `"2026-09-15T21:43:09+02:00"` → epoch millis. Null on anything unreadable: a date we cannot
     * read costs the price its stamp, never the price itself.
     */
    private fun parseIsoMillis(raw: String): Long? =
        runCatching { Instant.parse(raw).toEpochMilliseconds() }.getOrNull()

    /**
     * The base products, and ONLY those (traps 3 and 4).
     *
     * Keyed by the numeric `fuelId` rather than the display name, because the name is what varies:
     * seven different ids all call themselves `HVO`. These four numbers are the ones
     * `registry/fuels` calls categories rather than products, and they are stable — they are what
     * the ministry's own filter is built on.
     */
    private enum class ItalyProduct(val fuelId: Int, val fuel: FuelType) {
        BENZINA(1, FuelType.GASOLINE),
        GASOLIO(2, FuelType.DIESEL),
        GPL(4, FuelType.LPG_CNG),
    }

    /** Trap 6: Italian for "white pumps" — an unbranded forecourt, not a chain called that. */
    private const val UNBRANDED = "PompeBianche"

    /** The feed is Italy-only, so its money is not a question. */
    private const val CURRENCY = "EUR"

    @Serializable
    private data class ItalyResponse(
        val success: Boolean = false,
        val results: List<ItalyRecord> = emptyList(),
    )

    @Serializable
    private data class ItalyRecord(
        val id: Long,
        val name: String? = null,
        val brand: String? = null,
        val location: ItalyLocation? = null,
        /** ISO-8601 with an offset. See trap 5 for whose hour this is. */
        val insertDate: String? = null,
        val fuels: List<ItalyQuote> = emptyList(),
    )

    @Serializable
    private data class ItalyLocation(val lat: Double, val lng: Double)

    @Serializable
    private data class ItalyQuote(
        val fuelId: Int = 0,
        val price: Double = 0.0,
        val isSelf: Boolean = false,
    )
}

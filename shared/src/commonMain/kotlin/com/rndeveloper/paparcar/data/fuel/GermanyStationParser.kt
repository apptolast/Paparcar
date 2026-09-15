package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Reads Tankerkönig's `list.php` — Germany's MTS-K prices, served whole.
 * [FUEL-GERMANY-THE-FEED-ANSWERS-WHOLE-001]
 *
 * The first country since Spain whose feed needs NO second source: name, brand, address and prices
 * arrive in one response, so there is no join here at all — the join France needed is a France
 * problem, not a shape. What this feed carries instead, measured against the live endpoint on
 * 14/15-09-2026:
 *
 *  1. ⛔ **A dead key answers HTTP 200 with `ok:false`** and a German sentence. Same family of lie
 *     as France's unitless radius (200 with `total_count: 0`) and the Swiss Overpass mirror (200
 *     with an empty base): the gate is the `ok` field, never the status code.
 *  2. **`null` on a fuel is ambiguous.** 3 of 347 Berlin stations price no E5; whether they do not
 *     sell it or did not report it, MTS-K does not say. So silence maps to no availability entry —
 *     [com.rndeveloper.paparcar.domain.fuel.FuelAvailability.UNKNOWN] — and nothing German is ever
 *     hidden. The feed cannot state a negative, unlike Spain's empty string or France's rupture.
 *  3. **E5 answers for petrol, E10 is the backup** — the inverse of France, and measured, not
 *     assumed: E5 priced at 344/347 (Berlin 25 km) and 130/131 (Munich 10 km), E10 at 344 and 122.
 *     Every petrol car burns E5; not every one may burn E10.
 *  4. **No hour, anywhere.** Neither the response nor the price carries a timestamp — MTS-K is
 *     near-real-time by law (stations must report changes within minutes), which is why the SOURCE
 *     says [com.rndeveloper.paparcar.domain.fuel.FuelStationsResult.pricesAreLive] instead of a
 *     stamp nobody wrote. Inventing one from our clock is the costura prohibida.
 *  5. **A closed station keeps its prices** (`isOpen: false` on 189 of 347 at night, all priced).
 *     They stay on the list: hiding half the map at 3 a.m. answers a question nobody asked, and
 *     [FuelStation] deliberately has no "open" concept to misparse.
 */
object GermanyStationParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** @throws IllegalStateException when the feed says `ok:false` — see trap 1. */
    fun parse(body: String): List<FuelStation> {
        val response = json.decodeFromString<GermanyResponse>(body)
        check(response.ok) {
            "Tankerkönig answered ok=false: ${response.message ?: response.status ?: "no message"}"
        }
        return response.stations.map { record ->
            FuelStation(
                id = record.id,
                // The brand reads like a name («ARAL», «HEM») while `name` often reads like an
                // address («Berlin, Wittestr. 16») — and only 2 of 347 brands came back blank.
                name = record.brand?.trim()?.takeIf { it.isNotEmpty() }
                    ?: record.name?.trim()?.takeIf { it.isNotEmpty() },
                address = listOfNotNull(record.street?.trim(), record.houseNumber?.trim())
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
                    .takeIf { it.isNotEmpty() },
                town = record.town?.trim()?.takeIf { it.isNotEmpty() },
                latitude = record.lat,
                longitude = record.lng,
                prices = record.readPrices(),
                // Trap 2: this feed cannot state a negative, so it states nothing.
                availability = emptyMap(),
                // `isOpen` is a boolean about the moment of the fetch, not a schedule — printing it
                // as one would close a station that opens in an hour.
                schedule = null,
            )
        }
    }

    /** The first grade with a price answers, in [GermanyProduct.preference] order (trap 3). */
    private fun GermanyRecord.readPrices(): Map<FuelType, StationPrice> = buildMap {
        FuelType.entries.forEach { fuel ->
            GermanyProduct.entries
                .filter { it.fuel == fuel }
                .sortedBy { it.preference }
                .firstNotNullOfOrNull { product -> priceOf(product) }
                ?.let { amount ->
                    // Trap 4: no stamp exists, and none is invented.
                    put(fuel, StationPrice(perUnit = Money(amount, CURRENCY)))
                }
        }
    }

    /**
     * Read defensively: `null` is the measured absence, but Tankerkönig's other endpoints spell it
     * `false`, and a `Double?` field would turn that spelling into a dropped RESPONSE instead of a
     * dropped price. Zero is discarded like everywhere else — a free forecourt would top a list
     * whose one promise is the ranking.
     */
    private fun GermanyRecord.priceOf(product: GermanyProduct): Double? = when (product) {
        GermanyProduct.E5 -> e5
        GermanyProduct.E10 -> e10
        GermanyProduct.DIESEL -> diesel
    }?.let { (it as? JsonPrimitive)?.doubleOrNull }?.takeIf { it > 0.0 }

    /** Feed product → the fuel a car burns. Lower [preference] wins a shared [fuel] (trap 3). */
    private enum class GermanyProduct(val fuel: FuelType, val preference: Int) {
        E5(FuelType.GASOLINE, 0),
        E10(FuelType.GASOLINE, 1),
        DIESEL(FuelType.DIESEL, 0),
    }

    /** The feed is Germany-only, so its money is not a question. */
    private const val CURRENCY = "EUR"

    @Serializable
    private data class GermanyResponse(
        val ok: Boolean = false,
        val status: String? = null,
        val message: String? = null,
        val stations: List<GermanyRecord> = emptyList(),
    )

    @Serializable
    private data class GermanyRecord(
        val id: String,
        val name: String? = null,
        val brand: String? = null,
        val street: String? = null,
        val houseNumber: String? = null,
        @SerialName("place") val town: String? = null,
        val lat: Double,
        val lng: Double,
        val e5: JsonElement? = null,
        val e10: JsonElement? = null,
        val diesel: JsonElement? = null,
    )
}

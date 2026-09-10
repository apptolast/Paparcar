package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelAvailability
import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * Reads France's `prix-des-carburants-en-france-flux-instantane-v2`.
 * [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
 *
 * The cleanest feed of the whole survey — prices already numeric, a native radius filter, and each
 * price stamped with its own hour — and it still carries four traps, all measured against the live
 * endpoint on 09/10-09-2026 rather than assumed:
 *
 *  1. **No name and no brand.** Only `adresse` and `ville`; there is no `nom`/`marque`/`enseigne`
 *     field at all. Names come from OSM, joined on the official id — see [OsmStationNames].
 *  2. ⛔ **`latitude`/`longitude` are unusable.** They are strings scaled ×100.000 (`'4361600'` =
 *     43,616), and 15 of 100 sampled records put a **decimal point inside them**
 *     (`'4802880.70289'`), so an integer parse does not merely lose precision, it throws. [geom] is
 *     the decimal pair and the only coordinate read here. ⚠️ It is quantised to ~110 m for 74% of
 *     stations — measured p50 = 43 m against OSM twins confirmed by official id — which is why the
 *     name join can never be a proximity join.
 *  3. **A fuel can be IN RUPTURE**, which is not the same as not selling it, and France uses the
 *     two kinds for two different facts. See [readAvailability].
 *  4. **Mapping only SP95 would leave France mute.** E10 replaced it: 5.272 stations declare a
 *     *definitive* SP95 rupture. Measured coverage of a 95-grade price: E10 alone 7.285 stations,
 *     E10 with an SP95 fallback **9.019 of 9.805 (92%)**. ⛔ SP98 stays out, exactly as Spain keeps
 *     its 98 out: a premium price shown as "the petrol price" inverts the ranking that is the only
 *     thing this list promises.
 */
object FranceStationParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fields asked of the API. Sending it shrank Paris/8 km from **275 KB to 13,7 KB**.
     *
     * Built from the same enum the parser reads, so a product added below cannot be left out of the
     * request — the failure that shape prevents is a field that parses fine and is never fetched.
     * ⚠️ `get()` rather than an initialiser: a plain `val` here would run before [FuelProduct] and
     * read an empty list.
     */
    val selectFields: String
        get() = buildString {
            append(BASE_FIELDS)
            FuelProduct.entries.forEach { product ->
                append(",${product.priceField},${product.updatedField},${product.ruptureField}")
            }
        }

    fun parse(body: String): List<FuelStation> =
        json.decodeFromString<FranceResponse>(body).results.mapNotNull { record ->
            // ⛔ Never `latitude`/`longitude`: see trap 2. A record with no `geom` is dropped rather
            // than placed at (0, 0), which is in the Gulf of Guinea.
            val geom = record.geom ?: return@mapNotNull null
            FuelStation(
                id = record.id?.toString() ?: return@mapNotNull null,
                // The feed has none. Filled in later from OSM, or left null and shown by address.
                name = null,
                address = record.address?.trim()?.takeIf { it.isNotEmpty() },
                town = record.town?.trim()?.takeIf { it.isNotEmpty() },
                latitude = geom.lat,
                longitude = geom.lon,
                prices = record.readPrices(),
                availability = record.readAvailability(),
                schedule = record.readSchedule(),
            )
        }

    /**
     * The cheapest honest answer per fuel: the first grade that carries a price, with ITS hour.
     *
     * Ordered by [FuelProduct.preference] so E10 answers for petrol where both exist — it is the
     * grade the overwhelming majority of French forecourts still price.
     */
    private fun FranceRecord.readPrices(): Map<FuelType, StationPrice> = buildMap {
        FuelType.entries.forEach { fuel ->
            productsFor(fuel).firstNotNullOfOrNull { product ->
                priceOf(product)?.let { amount ->
                    StationPrice(
                        perUnit = Money(amount, CURRENCY),
                        reportedAtMs = updatedAtOf(product)?.let(::parseIsoMillis),
                    )
                }
            }?.let { price -> put(fuel, price) }
        }
    }

    /**
     * What France states about each pump — and it **can state a negative**, which is the finding
     * that changed this mapping. Measured nationwide, every station+fuel pair falls in exactly one
     * bucket (gazole: 9.541 + 247 + 17 = 9.805, the whole country):
     *
     * | feed says | verdict | why |
     * |---|---|---|
     * | a price | [FuelAvailability.SOLD] | resolved by `FuelStation.availabilityOf`, not here |
     * | `rupture_type = "definitive"` | [FuelAvailability.NOT_SOLD] | a DECLARED negative — 6.644 stations say so of LPG, some since 2019 |
     * | `rupture_type = "temporaire"` | [FuelAvailability.SOLD] | it sells it and is out today; hiding it would be false tomorrow |
     * | neither | [FuelAvailability.UNKNOWN] | nobody has said — 1.149 stations for LPG |
     *
     * ⛔ Silence is **not** read as a negative even though France mostly does declare its negatives:
     * hiding 1.149 stations on an assumption breaks the rule this model exists for — a station is
     * hidden only on `NOT_SOLD`. `carburants_indisponibles` cannot break the tie either: it held
     * all 110 silent pairs of the sample AND the broken ones, being merely "not in disponibles".
     *
     * With two grades behind one [FuelType], the negative has to be unanimous: E10 stopped while
     * SP95 says nothing means "we do not know if it sells petrol", not "it sells no petrol".
     */
    private fun FranceRecord.readAvailability(): Map<FuelType, FuelAvailability> = buildMap {
        FuelType.entries.forEach { fuel ->
            val products = productsFor(fuel)
            if (products.isEmpty()) return@forEach
            val verdicts = products.map { product ->
                when {
                    priceOf(product) != null -> FuelAvailability.SOLD
                    ruptureOf(product) == RUPTURE_TEMPORARY -> FuelAvailability.SOLD
                    ruptureOf(product) == RUPTURE_DEFINITIVE -> FuelAvailability.NOT_SOLD
                    else -> FuelAvailability.UNKNOWN
                }
            }
            val verdict = when {
                verdicts.any { it == FuelAvailability.SOLD } -> FuelAvailability.SOLD
                verdicts.all { it == FuelAvailability.NOT_SOLD } -> FuelAvailability.NOT_SOLD
                else -> FuelAvailability.UNKNOWN
            }
            put(fuel, verdict)
        }
    }

    /**
     * As the source words it, like every other adapter — never parsed into opening logic. The
     * round-the-clock flag wins because a `horaires_jour` of day names says nothing a driver at
     * 3 a.m. can use.
     */
    private fun FranceRecord.readSchedule(): String? = when {
        alwaysOpen.equals(ALWAYS_OPEN_YES, ignoreCase = true) -> SCHEDULE_24H
        else -> schedule?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * `"2026-09-10T00:01:00+00:00"` → epoch millis. Null on anything unreadable: a date we cannot
     * read costs the price its stamp, never the price itself.
     */
    private fun parseIsoMillis(raw: String): Long? =
        runCatching { Instant.parse(raw).toEpochMilliseconds() }.getOrNull()

    private fun FranceRecord.priceOf(product: FuelProduct): Double? = when (product) {
        FuelProduct.GAZOLE -> gazolePrice
        FuelProduct.E10 -> e10Price
        FuelProduct.SP95 -> sp95Price
        FuelProduct.GPLC -> gplcPrice
    }?.takeIf { it > 0.0 }

    private fun FranceRecord.updatedAtOf(product: FuelProduct): String? = when (product) {
        FuelProduct.GAZOLE -> gazoleUpdated
        FuelProduct.E10 -> e10Updated
        FuelProduct.SP95 -> sp95Updated
        FuelProduct.GPLC -> gplcUpdated
    }

    private fun FranceRecord.ruptureOf(product: FuelProduct): String? = when (product) {
        FuelProduct.GAZOLE -> gazoleRupture
        FuelProduct.E10 -> e10Rupture
        FuelProduct.SP95 -> sp95Rupture
        FuelProduct.GPLC -> gplcRupture
    }

    private fun productsFor(fuel: FuelType): List<FuelProduct> =
        FuelProduct.entries.filter { it.fuel == fuel }.sortedBy { it.preference }

    /**
     * Feed product → the fuel a CAR burns. [preference] breaks the tie when one type has two
     * grades: lower wins, and E10 wins petrol by measurement (trap 4).
     */
    private enum class FuelProduct(
        val fuel: FuelType,
        val preference: Int,
        val priceField: String,
        val updatedField: String,
        val ruptureField: String,
    ) {
        GAZOLE(FuelType.DIESEL, 0, "gazole_prix", "gazole_maj", "gazole_rupture_type"),
        E10(FuelType.GASOLINE, 0, "e10_prix", "e10_maj", "e10_rupture_type"),
        SP95(FuelType.GASOLINE, 1, "sp95_prix", "sp95_maj", "sp95_rupture_type"),
        GPLC(FuelType.LPG_CNG, 0, "gplc_prix", "gplc_maj", "gplc_rupture_type"),
    }

    /** The feed is France-only, so its money is not a question. */
    private const val CURRENCY = "EUR"
    private const val BASE_FIELDS = "id,geom,adresse,ville,horaires_jour,horaires_automate_24_24"
    private const val RUPTURE_DEFINITIVE = "definitive"
    private const val RUPTURE_TEMPORARY = "temporaire"
    private const val ALWAYS_OPEN_YES = "Oui"
    private const val SCHEDULE_24H = "24/24"

    @Serializable
    private data class FranceResponse(val results: List<FranceRecord> = emptyList())

    @Serializable
    private data class FranceGeom(val lat: Double, val lon: Double)

    /**
     * ⚠️ `id` is a NUMBER here, not a string — and it is the official station id that OSM stores in
     * `ref:FR:prix-carburants`, which is what makes the name join exact.
     */
    @Serializable
    private data class FranceRecord(
        val id: Long? = null,
        val geom: FranceGeom? = null,
        @SerialName("adresse") val address: String? = null,
        @SerialName("ville") val town: String? = null,
        @SerialName("horaires_jour") val schedule: String? = null,
        @SerialName("horaires_automate_24_24") val alwaysOpen: String? = null,
        @SerialName("gazole_prix") val gazolePrice: Double? = null,
        @SerialName("gazole_maj") val gazoleUpdated: String? = null,
        @SerialName("gazole_rupture_type") val gazoleRupture: String? = null,
        @SerialName("e10_prix") val e10Price: Double? = null,
        @SerialName("e10_maj") val e10Updated: String? = null,
        @SerialName("e10_rupture_type") val e10Rupture: String? = null,
        @SerialName("sp95_prix") val sp95Price: Double? = null,
        @SerialName("sp95_maj") val sp95Updated: String? = null,
        @SerialName("sp95_rupture_type") val sp95Rupture: String? = null,
        @SerialName("gplc_prix") val gplcPrice: Double? = null,
        @SerialName("gplc_maj") val gplcUpdated: String? = null,
        @SerialName("gplc_rupture_type") val gplcRupture: String? = null,
    )
}

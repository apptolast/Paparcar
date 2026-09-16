package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.LITRES_PER_IMPERIAL_GALLON
import com.rndeveloper.paparcar.LITRES_PER_US_GALLON
import com.rndeveloper.paparcar.domain.fuel.AverageFuelPriceTable
import com.rndeveloper.paparcar.domain.fuel.CountryFuelPrices
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads OpenVan.camp's national average fuel prices. [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
 *
 * Data: OpenVan.camp, CC BY 4.0 — https://openvan.camp/
 *
 * In commonMain and under test like [MineturStationParser], for the same reason: the wire format is
 * where this integration will break, and only the socket needs a platform.
 *
 * ⛔ Five traps, all measured against the live feed rather than assumed:
 *  1. **Fourteen countries do not quote per litre.** `unit` is `gallon` (US and nine others) or
 *     `imperial_gallon` (four Caribbean ones). Reading a US price as litres inflates a drive by
 *     3.79× with no symptom other than wrong arithmetic. A unit this parser does not KNOW drops the
 *     country — it is never assumed to be litres.
 *  1b. **And the row's unit does not govern every fuel in it.** The feed ALSO ships a `units` map,
 *     keyed by fuel, and it disagrees with `unit` in 18 countries — because gases are not sold by
 *     volume: CNG goes by `kg` (Spain, Italy, India…) or `m3` (Brazil, Argentina, Bolivia) and LPG
 *     by `kg` in Ghana, Pakistan and Thailand. Reading Brazil's `4.8098 BRL/m3` as a litre price is
 *     not a scale error, it is a CATEGORY error: no factor converts a cubic metre of gas into a
 *     litre of liquid without knowing the pressure. So the unit of a price is the one of ITS fuel,
 *     and the row's is only the fallback — and an unknown one drops **that fuel**, not the country,
 *     because CNG sold by the cubic metre says nothing about the petrol at the same forecourt.
 *     [FUEL-A-FEED-QUOTES-EACH-FUEL-IN-ITS-OWN-UNIT-001]
 *  2. **The currency is local, and there are ~120 of them.** Poland quotes 7.02 PLN, Turkey 76.84
 *     TRY. Nothing here converts: [Money] carries the currency, and the period aggregate already
 *     refuses to add two of them together.
 *  3. **`is_excluded` is the feed distrusting its own row** (six countries on the day this was
 *     written). Respected, not second-guessed.
 *  4. **Freshness is per row, not just global.** 167 of 168 rows were same-day; one (Micronesia)
 *     was four months old. A row older than [MAX_ROW_AGE_DAYS] is dropped, because an undated —
 *     or long-dated — price is a number somebody made up.
 *
 * Prices are JSON numbers here, not the decimal-comma strings the ministry serves, so there is no
 * locale trap in this one.
 */
object OpenVanFuelPricesParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * The table [body] describes, or a failure when the feed says it failed or carries no usable
     * country at all. An empty table is treated as a failure on purpose: silently installing zero
     * countries over a good embedded table would turn every cost tile blank at once.
     */
    fun parse(body: String): Result<AverageFuelPriceTable> = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        // Only an EXPLICIT false is a refusal. A payload that omits the flag but carries countries
        // is still usable, and treating "absent" as "failed" would throw away a good answer.
        if (root[FIELD_SUCCESS]?.jsonPrimitive?.booleanOrNull == false) {
            throw IllegalStateException("OpenVan reported success=false")
        }

        val asOf = root[FIELD_META]?.jsonObject
            ?.get(FIELD_UPDATED_AT)?.jsonPrimitive?.contentOrNull?.take(DATE_LENGTH)
            ?: throw IllegalStateException("OpenVan payload carries no meta.updated_at")

        val data = root[FIELD_DATA]?.jsonObject
            ?: throw IllegalStateException("OpenVan payload carries no data object")
        val rows = data
            .mapNotNull { (code, element) -> element.jsonObject.toRow(asOf)?.let { code to it } }
            .toMap()

        if (rows.isEmpty()) throw IllegalStateException("OpenVan payload carried no usable country")
        AverageFuelPriceTable(asOf = asOf, rows = rows)
    }

    /** One country, already normalised to litres, or null when this row must not be trusted. */
    private fun JsonObject.toRow(asOf: String): CountryFuelPrices? {
        if (this[FIELD_EXCLUDED]?.jsonPrimitive?.booleanOrNull == true) return null

        val rowUnit = this[FIELD_UNIT]?.jsonPrimitive?.contentOrNull
        // The row's own unit still has to be one we know: without it the fuels that DON'T declare
        // their own would have nothing to fall back on, and that is the whole country.
        if (LITRES_PER_UNIT[rowUnit] == null) return null
        val currency = this[FIELD_CURRENCY]?.jsonPrimitive?.contentOrNull ?: return null
        if (isStale(asOf)) return null

        val prices = this[FIELD_PRICES]?.jsonObject ?: return null
        val units = this[FIELD_UNITS]?.jsonObject

        /** Litres in one unit of [key], asking the fuel first and the row only as a fallback. */
        fun litresPerUnitOf(key: String): Double? {
            val declared = units?.get(key)?.jsonPrimitive?.contentOrNull ?: rowUnit
            return LITRES_PER_UNIT[declared]
        }

        fun perLitre(key: String): Double? {
            val factor = litresPerUnitOf(key) ?: return null
            return prices[key]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0.0 }?.div(factor)
        }

        val gasoline = perLitre(KEY_GASOLINE)
        val diesel = perLitre(KEY_DIESEL)
        // A row that prices neither of the two fuels almost every car burns says nothing useful.
        if (gasoline == null && diesel == null) return null

        return CountryFuelPrices(
            currency = currency,
            gasoline = gasoline,
            diesel = diesel,
            // LPG and CNG are one FuelType here, and LPG is the one a car actually finds. The
            // fallback survives; what changed is that CNG only answers when it arrives in a unit
            // this parser can read — which, for the four countries that reach it, it never does.
            lpg = perLitre(KEY_LPG) ?: perLitre(KEY_CNG),
        )
    }

    /**
     * Whether this row lags the payload's own `updated_at` by more than [MAX_ROW_AGE_DAYS].
     *
     * Compared as ISO date STRINGS rather than parsed instants: both sides come from the same feed
     * in the same format, only the date part matters, and lexicographic order is chronological
     * order for `YYYY-MM-DD`. Parsing the row's offset-carrying timestamp would buy nothing and
     * add a way to throw.
     */
    private fun JsonObject.isStale(asOf: String): Boolean {
        val fetched = this[FIELD_FETCHED_AT]?.jsonPrimitive?.contentOrNull?.take(DATE_LENGTH)
            ?: return true
        return daysBetween(fetched, asOf) > MAX_ROW_AGE_DAYS
    }

    /**
     * Whole days from [earlier] to [later], both `YYYY-MM-DD`, via a plain day count.
     *
     * A month is treated as 31 days and a year as 366 — deliberately GENEROUS, so the comparison
     * can only ever be lenient by a few days and never rejects a fresh row. The one thing it has to
     * catch is a row months behind the rest, and it does.
     */
    private fun daysBetween(earlier: String, later: String): Int {
        fun days(date: String): Int {
            val parts = date.split("-")
            if (parts.size != DATE_PARTS) return 0
            val year = parts[0].toIntOrNull() ?: return 0
            val month = parts[1].toIntOrNull() ?: return 0
            val day = parts[2].toIntOrNull() ?: return 0
            return year * DAYS_PER_YEAR + month * DAYS_PER_MONTH + day
        }
        return days(later) - days(earlier)
    }

    /**
     * Litres in one unit of each thing the feed quotes. ⛔ A unit absent from this map DROPS what it
     * measures — the whole point is that an unknown unit can never be mistaken for litres.
     *
     * ⛔ **`kg` and `m3` are absent ON PURPOSE and must stay absent.** They are how the feed prices
     * gases, and adding them with some factor would be inventing one: a kilo of LPG is about 1.96
     * litres but the pump charges the kilo, and a cubic metre of CNG depends on the pressure. A fuel
     * quoted that way has no litre price, and null is the honest answer.
     *
     * The two gallons are the SHARED constants, not a local copy: the outbound boundary writes
     * prices back out with the same numbers, and one figure spelled in two files is one figure that
     * eventually disagrees with itself. [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001]
     */
    private val LITRES_PER_UNIT = mapOf(
        "liter" to 1.0,
        "gallon" to LITRES_PER_US_GALLON,
        "imperial_gallon" to LITRES_PER_IMPERIAL_GALLON,
    )

    private const val MAX_ROW_AGE_DAYS = 30
    private const val DATE_LENGTH = 10
    private const val DATE_PARTS = 3
    private const val DAYS_PER_YEAR = 366
    private const val DAYS_PER_MONTH = 31

    private const val FIELD_SUCCESS = "success"
    private const val FIELD_META = "meta"
    private const val FIELD_UPDATED_AT = "updated_at"
    private const val FIELD_DATA = "data"
    private const val FIELD_UNIT = "unit"
    private const val FIELD_UNITS = "units"
    private const val FIELD_CURRENCY = "currency"
    private const val FIELD_PRICES = "prices"
    private const val FIELD_EXCLUDED = "is_excluded"
    private const val FIELD_FETCHED_AT = "fetched_at"

    private const val KEY_GASOLINE = "gasoline"
    private const val KEY_DIESEL = "diesel"
    private const val KEY_LPG = "lpg"
    private const val KEY_CNG = "cng"
}

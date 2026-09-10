package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelAvailability
import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads the Spanish ministry's fuel feed. [FUEL-STATIONS-LIST-001]
 *
 * Lives in commonMain and under test on purpose, like `OverpassPlaceParser`: the wire format is
 * where this integration will actually break, and only the socket needs a platform.
 *
 * ⛔ Four traps, all measured against the live feed on 08-09-2026 rather than assumed:
 *  1. **Decimal comma** — `"1,779"`, in prices AND in coordinates (`"36,460000"`). `toDouble()`
 *     returns null on those, so a naive parser silently drops every station.
 *  2. **Keys carry accents and spaces** — `Precio Gasóleo A`, `Longitud (WGS84)`. They are read by
 *     literal name; there is no camelCase version to fall back on.
 *  3. **Empty string means "does not sell it"**, not zero. Reading `""` as 0,000 € would rank a
 *     station that has no petrol as the cheapest petrol in town.
 *  4. **The province field is misspelled at the source**: `IDPovincia`, no 'r'. Ours to live with,
 *     not to fix — see [SpanishProvinces].
 */
object MineturStationParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Every station in [body] that carries a usable position. A row without coordinates is dropped
     * rather than placed at (0, 0), which is in the Atlantic.
     */
    fun parseStations(body: String): List<FuelStation> {
        val root = json.parseToJsonElement(body.removePrefix(BOM)).jsonObject
        val list = root[FIELD_LIST]?.jsonArray ?: return emptyList()
        // Spain stamps the RESPONSE, not the price, so every price this feed carries shares one
        // hour. Read once and handed down, so the station rows cannot disagree about it.
        // [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
        val reportedAtMs = root.text(FIELD_DATE)?.let(::parseFeedDateMillis)
        return list.mapNotNull { element ->
            val station = element.jsonObject
            val lat = station.decimal(FIELD_LAT) ?: return@mapNotNull null
            val lon = station.decimal(FIELD_LON) ?: return@mapNotNull null
            FuelStation(
                id = station.text(FIELD_ID) ?: return@mapNotNull null,
                name = station.text(FIELD_BRAND),
                address = station.text(FIELD_ADDRESS),
                town = station.text(FIELD_TOWN),
                latitude = lat,
                longitude = lon,
                prices = station.readPrices(reportedAtMs),
                availability = station.readAvailability(),
                schedule = station.text(FIELD_SCHEDULE),
            )
        }
    }

    /**
     * `"08/09/2026 23:52:46"` → epoch millis. The ministry writes peninsular Spanish wall-clock
     * time with no offset, so the zone has to be supplied; getting it wrong would age every price
     * by an hour or two.
     *
     * Null on anything unreadable — a date we cannot read costs the stamp, never the price. That is
     * also why this cannot throw: the format is the ministry's to change, and a list of real prices
     * must not disappear because a separator moved.
     */
    private fun parseFeedDateMillis(raw: String): Long? = runCatching {
        val (date, time) = raw.trim().split(' ', limit = 2)
        val (day, month, year) = date.split('/').map { it.toInt() }
        val parts = time.split(':').map { it.toInt() }
        LocalDateTime(year, month, day, parts[0], parts[1], parts.getOrElse(2) { 0 })
            .toInstant(TimeZone.of(FEED_TIME_ZONE))
            .toEpochMilliseconds()
    }.getOrNull()

    /**
     * The timestamp the feed stamps on the whole response, verbatim ("08/09/2026 23:52:46").
     *
     * Shown next to the prices because a price with no hour is a price nobody can weigh — and this
     * feed is the reason the phase exists: a proxy of it ran twelve hours behind and disagreed with
     * 30% of the stations. Not parsed into a date here: it is displayed, not calculated with.
     */
    fun parseReportedAt(body: String): String? =
        json.parseToJsonElement(body.removePrefix(BOM)).jsonObject.text(FIELD_DATE)

    /** Only the products the app can reason about; the feed carries 23, including hydrogen. */
    private fun JsonObject.readPrices(reportedAtMs: Long?): Map<FuelType, StationPrice> = buildMap {
        PRODUCTS.forEach { (key, fuel) ->
            decimal(key)?.let { amount ->
                put(fuel, StationPrice(Money(amount, CURRENCY), reportedAtMs))
            }
        }
    }

    /**
     * What this feed can state about each pump — and it can state a **negative**, which most sources
     * cannot: the ministry reports "does not sell it" as an empty string, so a present-but-empty key
     * is evidence, not a gap. Measured in Cádiz: 265 of 292 stations declare no LPG that way.
     *
     * A key that is absent entirely stays out of the map, which reads as
     * [FuelAvailability.UNKNOWN] — the feed simply did not speak about it.
     */
    private fun JsonObject.readAvailability(): Map<FuelType, FuelAvailability> = buildMap {
        PRODUCTS.forEach { (key, fuel) ->
            val raw = this@readAvailability[key]?.jsonPrimitive?.contentOrNullSafe() ?: return@forEach
            put(fuel, if (raw.isBlank()) FuelAvailability.NOT_SOLD else FuelAvailability.SOLD)
        }
    }

    /** Trimmed text, or null for absent AND for the empty string the feed uses as "no aplica". */
    private fun JsonObject.text(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNullSafe()?.trim()?.takeIf { it.isNotEmpty() }

    /** A number written the Spanish way. Null when absent, empty or unreadable — never 0. */
    private fun JsonObject.decimal(key: String): Double? =
        text(key)?.replace(',', '.')?.toDoubleOrNull()

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        runCatching { content }.getOrNull()

    /**
     * Ministry product → our fuel. Only three map: the app's [FuelType] describes what a CAR burns,
     * not the 23 products a forecourt can sell. CNG and LPG share a type in our model, and the GLP
     * pump is the one a driver of that car looks for.
     */
    private val PRODUCTS: List<Pair<String, FuelType>> = listOf(
        "Precio Gasolina 95 E5" to FuelType.GASOLINE,
        "Precio Gasoleo A" to FuelType.DIESEL,
        "Precio Gases licuados del petróleo" to FuelType.LPG_CNG,
    )

    /** The feed is Spain-only, so its money is not a question. */
    private const val CURRENCY = "EUR"

    /** The ministry publishes peninsular time; the Canaries run an hour behind it and the feed
     *  does not say so, which is a rounding error next to a price's useful life. */
    private const val FEED_TIME_ZONE = "Europe/Madrid"
    private const val BOM = "﻿"
    private const val FIELD_LIST = "ListaEESSPrecio"
    private const val FIELD_DATE = "Fecha"
    private const val FIELD_ID = "IDEESS"
    private const val FIELD_BRAND = "Rótulo"
    private const val FIELD_ADDRESS = "Dirección"
    private const val FIELD_TOWN = "Municipio"
    private const val FIELD_SCHEDULE = "Horario"
    private const val FIELD_LAT = "Latitud"
    private const val FIELD_LON = "Longitud (WGS84)"
}

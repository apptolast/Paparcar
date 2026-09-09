package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelAvailability
import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.model.FuelType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * OpenStreetMap's `amenity=fuel`: the floor under every country we have no feed for.
 * [FUEL-STATIONS-LIST-001]
 *
 * 567.615 stations worldwide and **not one price** — the diesel price tag has zero global uses — so
 * this source answers "where can I refuel" and never "at what". Where a national feed exists it wins
 * outright and this is not consulted: a national feed returns the whole object, prices included, so
 * there is nothing to merge.
 *
 * Measured in Madrid on 09-09-2026 (25 stations within 3 km), which is what the shape below is built
 * against rather than assumed:
 * - **`name` on 25/25 and `brand` on 24/25.** OSM names what it holds, so this is the piece France's
 *   official feed is missing, not a competing catalogue.
 * - **`fuel:*` tags on only ~44%** (`fuel:diesel` and `fuel:octane_95` on 11 of 25). ⛔ Their absence
 *   is [FuelAvailability.UNKNOWN], never [FuelAvailability.NOT_SOLD] — reading it as a negative
 *   would hide most stations in the world from most drivers in the world. This source can only ever
 *   say `SOLD`.
 */
object OsmFuelStationParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Every fuel station within [radiusMeters] of the point.
     *
     * `out center` rather than `out bb`: a forecourt is used as a destination, so its middle is the
     * useful point — unlike the POI naming query, which measures to a place's edge.
     */
    fun buildQuery(lat: Double, lon: Double, radiusMeters: Int, timeoutSeconds: Int): String =
        "[out:json][timeout:$timeoutSeconds];" +
            "nwr(around:$radiusMeters,$lat,$lon)[amenity=fuel];" +
            "out center $MAX_RESULTS;"

    fun parse(body: String): List<FuelStation> =
        json.decodeFromString<OsmResponse>(body).elements.mapNotNull { element ->
            val lat = element.lat ?: element.center?.lat ?: return@mapNotNull null
            val lon = element.lon ?: element.center?.lon ?: return@mapNotNull null
            val tags = element.tags
            FuelStation(
                id = "osm/${element.type}/${element.id}",
                // `brand` before `name` because a forecourt's brand is what a driver scans for; the
                // 1 in 25 with neither is shown by where it is, not by an invented name.
                name = tags["brand"] ?: tags["name"] ?: tags["operator"],
                address = tags["addr:street"]?.let { street ->
                    tags["addr:housenumber"]?.let { "$street $it" } ?: street
                },
                town = tags["addr:city"],
                latitude = lat,
                longitude = lon,
                // No prices, ever. Saying so in the type is the point of this source.
                prices = emptyMap(),
                availability = tags.readAvailability(),
                schedule = tags["opening_hours"],
            )
        }

    /** Only positives: a missing `fuel:*` tag means nobody has said, not that the pump is absent. */
    private fun Map<String, String>.readAvailability(): Map<FuelType, FuelAvailability> = buildMap {
        FUEL_TAGS.forEach { (tag, fuel) ->
            if (this@readAvailability[tag].equals("yes", ignoreCase = true)) {
                put(fuel, FuelAvailability.SOLD)
            }
        }
    }

    /**
     * OSM tags a forecourt by product, and several map onto one of our types: a car that burns
     * petrol does not care whether the pump is 95 or 98, and LPG and CNG share a type in our model.
     */
    private val FUEL_TAGS: List<Pair<String, FuelType>> = listOf(
        "fuel:diesel" to FuelType.DIESEL,
        "fuel:GTL_diesel" to FuelType.DIESEL,
        "fuel:octane_95" to FuelType.GASOLINE,
        "fuel:octane_98" to FuelType.GASOLINE,
        "fuel:octane_100" to FuelType.GASOLINE,
        "fuel:e10" to FuelType.GASOLINE,
        "fuel:lpg" to FuelType.LPG_CNG,
        "fuel:cng" to FuelType.LPG_CNG,
    )

    private const val MAX_RESULTS = 60

    @Serializable
    private data class OsmResponse(val elements: List<OsmElement> = emptyList())

    @Serializable
    private data class OsmElement(
        val type: String = "node",
        val id: Long = 0,
        val lat: Double? = null,
        val lon: Double? = null,
        val center: OsmCenter? = null,
        @SerialName("tags") val tags: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class OsmCenter(val lat: Double, val lon: Double)
}

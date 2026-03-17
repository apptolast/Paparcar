package io.apptolast.paparcar.location

import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.places.PlacesDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Queries the public Overpass API (OpenStreetMap) to find the most relevant
 * named POI within ~150 m of a given coordinate.
 *
 * Uses `out center` so that ways and relations return their geometric centre,
 * enabling distance sorting when multiple same-priority POIs are found.
 *
 * Only nodes/ways tagged with [amenity], [shop], [tourism], or [leisure] are
 * considered, so generic building names are excluded. Results are sorted first
 * by category priority (fuel > supermarket > …) then by distance.
 *
 * No API key required. Degrades gracefully to null on network errors or timeout.
 */
class OverpassPlacesDataSourceImpl : PlacesDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getNearbyPlace(lat: Double, lon: Double): Result<PlaceInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = postOverpass(buildQuery(lat, lon)) ?: return@runCatching null
                parseResponse(body, lat, lon)
            }
        }

    // ── Query ────────────────────────────────────────────────────────────────

    private fun buildQuery(lat: Double, lon: Double): String {
        val around = "around:$RADIUS_METERS,$lat,$lon"
        return "[out:json][timeout:8];" +
            "(nwr($around)[name][amenity];" +
            "nwr($around)[name][shop];" +
            "nwr($around)[name][tourism];" +
            "nwr($around)[name][leisure];);" +
            "out center $MAX_RESULTS;"
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────

    private fun postOverpass(query: String): String? {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 6_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val payload = "data=${URLEncoder.encode(query, "UTF-8")}"
            connection.outputStream.bufferedWriter().use { it.write(payload) }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().readText()
            } else null
        } finally {
            connection.disconnect()
        }
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    private fun parseResponse(body: String, originLat: Double, originLon: Double): PlaceInfo? {
        val elements = json.decodeFromString<OverpassResponse>(body).elements
        return elements
            .mapNotNull { el ->
                val name = el.tags["name"] ?: return@mapNotNull null
                val elLat = el.lat ?: el.center?.lat ?: return@mapNotNull null
                val elLon = el.lon ?: el.center?.lon ?: return@mapNotNull null
                val dist = haversineMeters(originLat, originLon, elLat, elLon)
                Triple(name, resolveCategory(el.tags), dist)
            }
            .sortedWith(compareBy(
                { CATEGORY_PRIORITY.indexOf(it.second).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                { it.third },
            ))
            .firstOrNull()
            ?.let { (name, category, _) -> PlaceInfo(name = name, category = category) }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun resolveCategory(tags: Map<String, String>): PlaceCategory {
        val amenity = tags["amenity"]
        val shop    = tags["shop"]
        val tourism = tags["tourism"]
        val leisure = tags["leisure"]
        return when {
            amenity == "fuel"                                          -> PlaceCategory.FUEL
            shop in listOf("supermarket", "grocery", "convenience")   -> PlaceCategory.SUPERMARKET
            shop in listOf("mall", "department_store",
                           "shopping_centre", "wholesale")            -> PlaceCategory.MALL
            amenity in listOf("restaurant", "fast_food", "food_court")-> PlaceCategory.RESTAURANT
            amenity == "cafe"                                          -> PlaceCategory.CAFE
            amenity == "pharmacy"                                      -> PlaceCategory.PHARMACY
            amenity in listOf("hospital", "clinic", "doctors")        -> PlaceCategory.HOSPITAL
            amenity == "parking"                                       -> PlaceCategory.PARKING
            amenity in listOf("bank", "atm")                          -> PlaceCategory.BANK
            tourism in listOf("hotel", "motel", "hostel",
                              "guest_house", "apartment")             -> PlaceCategory.HOTEL
            amenity in listOf("school", "university", "college",
                              "kindergarten")                         -> PlaceCategory.SCHOOL
            amenity in listOf("gym", "fitness_centre")                -> PlaceCategory.GYM
            leisure == "fitness_centre"                                -> PlaceCategory.GYM
            else                                                       -> PlaceCategory.OTHER
        }
    }

    // ── Serialization models ─────────────────────────────────────────────────

    @Serializable
    private data class OverpassResponse(
        val elements: List<OverpassElement> = emptyList(),
    )

    @Serializable
    private data class OverpassElement(
        val type: String = "",
        // nodes have lat/lon directly; ways/relations have a "center" object
        val lat: Double? = null,
        val lon: Double? = null,
        val center: Center? = null,
        val tags: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class Center(
        val lat: Double = 0.0,
        val lon: Double = 0.0,
    )

    companion object {
        private const val ENDPOINT = "https://overpass-api.de/api/interpreter"
        private const val RADIUS_METERS = 150
        private const val MAX_RESULTS = 20

        /** Lower index = shown first when multiple POIs are found in the radius. */
        private val CATEGORY_PRIORITY = listOf(
            PlaceCategory.FUEL,
            PlaceCategory.SUPERMARKET,
            PlaceCategory.MALL,
            PlaceCategory.HOSPITAL,
            PlaceCategory.PHARMACY,
            PlaceCategory.RESTAURANT,
            PlaceCategory.CAFE,
            PlaceCategory.HOTEL,
            PlaceCategory.PARKING,
            PlaceCategory.BANK,
            PlaceCategory.SCHOOL,
            PlaceCategory.GYM,
            PlaceCategory.OTHER,
        )
    }
}
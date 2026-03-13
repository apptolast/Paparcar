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

/**
 * Queries the public Overpass API (OpenStreetMap) to find the most relevant
 * named POI within ~50 m of a given coordinate.
 *
 * Only nodes/ways tagged with [amenity], [shop], [tourism], or [leisure] are
 * considered, so generic building names are excluded. Results are sorted by
 * category priority (fuel > supermarket > …) and the top match is returned.
 *
 * No API key required. Degrades gracefully to null on network errors or timeout.
 */
class OverpassPlacesDataSourceImpl : PlacesDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getNearbyPlace(lat: Double, lon: Double): Result<PlaceInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = postOverpass(buildQuery(lat, lon)) ?: return@runCatching null
                parseResponse(body)
            }
        }

    // ── Query ────────────────────────────────────────────────────────────────

    private fun buildQuery(lat: Double, lon: Double): String {
        val around = "around:50,$lat,$lon"
        return "[out:json][timeout:5];" +
            "(nwr($around)[name][amenity];" +
            "nwr($around)[name][shop];" +
            "nwr($around)[name][tourism];" +
            "nwr($around)[name][leisure];);" +
            "out 5;"
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────

    private fun postOverpass(query: String): String? {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 6_000
            connection.readTimeout = 8_000
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

    private fun parseResponse(body: String): PlaceInfo? {
        val elements = json.decodeFromString<OverpassResponse>(body).elements
        return elements
            .mapNotNull { el ->
                val name = el.tags["name"] ?: return@mapNotNull null
                PlaceInfo(name = name, category = resolveCategory(el.tags))
            }
            .minByOrNull { CATEGORY_PRIORITY.indexOf(it.category).let { i -> if (i < 0) Int.MAX_VALUE else i } }
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
        val tags: Map<String, String> = emptyMap(),
    )

    companion object {
        private const val ENDPOINT = "https://overpass-api.de/api/interpreter"

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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.location

import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.places.PlacesDataSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURLSession
import platform.Foundation.characterSetWithCharactersInString
import platform.Foundation.dataUsingEncoding
import platform.Foundation.create
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * iOS equivalent of OverpassPlacesDataSourceImpl — same Overpass API query,
 * NSURLSession instead of HttpURLConnection. [IOS-PLACES-001]
 */
class IosOverpassPlacesDataSourceImpl : PlacesDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getNearbyPlace(lat: Double, lon: Double): Result<PlaceInfo?> =
        runCatching {
            val body = postOverpass(buildQuery(lat, lon)) ?: return@runCatching null
            parseResponse(body, lat, lon)
        }

    // ── Query ─────────────────────────────────────────────────────────────────

    private fun buildQuery(lat: Double, lon: Double): String {
        val around = "around:$RADIUS_METERS,$lat,$lon"
        return "[out:json][timeout:$QUERY_TIMEOUT_S];" +
            "(nwr($around)[name][amenity];" +
            "nwr($around)[name][shop];" +
            "nwr($around)[name][tourism];" +
            "nwr($around)[name][leisure];);" +
            "out center $MAX_RESULTS;"
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private suspend fun postOverpass(query: String): String? =
        suspendCancellableCoroutine { continuation ->
            val url = NSURL.URLWithString(ENDPOINT)
            if (url == null) { continuation.resume(null); return@suspendCancellableCoroutine }

            val request = NSMutableURLRequest(url)
            request.setHTTPMethod("POST")
            request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField = "Content-Type")
            request.setValue(CONNECT_TIMEOUT_S.toString(), forHTTPHeaderField = "X-Connect-Timeout")
            request.timeoutInterval = READ_TIMEOUT_S.toDouble()

            // percent-encode the query as an application/x-www-form-urlencoded value
            val unreserved = NSCharacterSet.characterSetWithCharactersInString(
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~",
            )
            val encodedQuery = (query as NSString)
                .stringByAddingPercentEncodingWithAllowedCharacters(unreserved) ?: query
            val bodyString = "data=$encodedQuery"
            val bodyData = (bodyString as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            if (bodyData == null) { continuation.resume(null); return@suspendCancellableCoroutine }
            request.setHTTPBody(bodyData)

            val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, _, error ->
                if (error != null || data == null) {
                    continuation.resume(null)
                } else {
                    val text = NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
                    continuation.resume(text)
                }
            }
            task.resume()
            continuation.invokeOnCancellation { task.cancel() }
        }

    // ── Parsing ───────────────────────────────────────────────────────────────

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
            .sortedWith(
                compareBy(
                    { CATEGORY_PRIORITY.indexOf(it.second).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                    { it.third },
                ),
            )
            .firstOrNull()
            ?.let { (name, category, _) -> PlaceInfo(name = name, category = category) }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) *
            sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun resolveCategory(tags: Map<String, String>): PlaceCategory {
        val amenity = tags["amenity"]
        val shop    = tags["shop"]
        val tourism = tags["tourism"]
        val leisure = tags["leisure"]
        return when {
            amenity == "fuel"                                            -> PlaceCategory.FUEL
            shop in listOf("supermarket", "grocery", "convenience")     -> PlaceCategory.SUPERMARKET
            shop in listOf("mall", "department_store",
                           "shopping_centre", "wholesale")              -> PlaceCategory.MALL
            amenity in listOf("restaurant", "fast_food", "food_court") -> PlaceCategory.RESTAURANT
            amenity == "cafe"                                           -> PlaceCategory.CAFE
            amenity == "pharmacy"                                       -> PlaceCategory.PHARMACY
            amenity in listOf("hospital", "clinic", "doctors")         -> PlaceCategory.HOSPITAL
            amenity == "parking"                                        -> PlaceCategory.PARKING
            amenity in listOf("bank", "atm")                           -> PlaceCategory.BANK
            tourism in listOf("hotel", "motel", "hostel",
                              "guest_house", "apartment")              -> PlaceCategory.HOTEL
            amenity in listOf("school", "university", "college",
                              "kindergarten")                          -> PlaceCategory.SCHOOL
            amenity in listOf("gym", "fitness_centre")                 -> PlaceCategory.GYM
            leisure == "fitness_centre"                                 -> PlaceCategory.GYM
            else                                                        -> PlaceCategory.OTHER
        }
    }

    // ── Serialization models ──────────────────────────────────────────────────

    @Serializable
    private data class OverpassResponse(
        val elements: List<OverpassElement> = emptyList(),
    )

    @Serializable
    private data class OverpassElement(
        val type: String = "",
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

    private companion object {
        const val ENDPOINT = "https://overpass-api.de/api/interpreter"
        const val RADIUS_METERS = 80
        const val MAX_RESULTS = 20
        const val QUERY_TIMEOUT_S = 8
        const val CONNECT_TIMEOUT_S = 6
        const val READ_TIMEOUT_S = 10
        const val EARTH_RADIUS_METERS = 6_371_000.0

        val CATEGORY_PRIORITY = listOf(
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

package com.rndeveloper.paparcar.data.places

import com.rndeveloper.paparcar.domain.model.PlaceCategory
import com.rndeveloper.paparcar.domain.places.NearbyPlacePolicy
import com.rndeveloper.paparcar.domain.places.PlaceCandidate
import com.rndeveloper.paparcar.domain.util.BoundingBox
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The Overpass (OpenStreetMap) wire format: how the question is asked and how the answer is read.
 * Platform data sources own the HTTP call and nothing else. [POI-A-PLACE-IS-NAMED-ONLY-IF-YOU-ARE-AT-IT-001]
 *
 * This used to be two files — `OverpassPlacesDataSourceImpl` in androidMain and
 * `IosOverpassPlacesDataSourceImpl` in iosMain — carrying literal copies of the query, the category
 * table, the priority list and a hand-rolled haversine. Nothing of it lived in commonMain, so
 * nothing of it had a test, and a rule fixed on one platform stayed broken on the other.
 */
object OverpassPlaceParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Asks for named amenities/shops/tourism/leisure around a point.
     *
     * `out bb` (not `out center`) is the load-bearing choice: it adds each element's **bounding
     * box** — for a node it is the node itself, for a way the box enclosing all its nodes — while
     * `body` still carries the tags. That is what lets [NearbyPlacePolicy] measure to a place's edge
     * instead of to the middle of its building, and it costs far less payload than `out geom`.
     */
    fun buildQuery(lat: Double, lon: Double, timeoutSeconds: Int): String {
        val around = "around:${NearbyPlacePolicy.QUERY_RADIUS_METERS},$lat,$lon"
        return "[out:json][timeout:$timeoutSeconds];" +
            "(nwr($around)[name][amenity];" +
            "nwr($around)[name][shop];" +
            "nwr($around)[name][tourism];" +
            "nwr($around)[name][leisure];);" +
            "out bb ${NearbyPlacePolicy.MAX_RESULTS};"
    }

    /**
     * Every named element of the response that carries a position, as candidates.
     *
     * Position is read `bounds` → `center` → `lat`/`lon`, so a mirror that ignored `bb` and answered
     * like the old `out center` degrades to point distances instead of dropping the place entirely.
     */
    fun parseCandidates(body: String): List<PlaceCandidate> =
        json.decodeFromString<OverpassResponse>(body).elements.mapNotNull { element ->
            val name = element.tags["name"] ?: return@mapNotNull null
            val bounds = element.bounds?.toBoundingBox()
            val lat = element.lat ?: element.center?.lat ?: bounds?.let { (it.minLat + it.maxLat) / 2 }
            val lon = element.lon ?: element.center?.lon ?: bounds?.let { (it.minLon + it.maxLon) / 2 }
            if (lat == null || lon == null) return@mapNotNull null
            PlaceCandidate(
                name = name,
                category = resolveCategory(element.tags),
                lat = lat,
                lon = lon,
                bounds = bounds,
            )
        }

    fun resolveCategory(tags: Map<String, String>): PlaceCategory {
        val amenity = tags["amenity"]
        val shop = tags["shop"]
        val tourism = tags["tourism"]
        val leisure = tags["leisure"]
        return when {
            amenity == "fuel" -> PlaceCategory.FUEL
            shop in listOf("supermarket", "grocery", "convenience") -> PlaceCategory.SUPERMARKET
            shop in listOf("mall", "department_store", "shopping_centre", "wholesale") -> PlaceCategory.MALL
            amenity in listOf("restaurant", "fast_food", "food_court") -> PlaceCategory.RESTAURANT
            amenity == "cafe" -> PlaceCategory.CAFE
            amenity == "pharmacy" -> PlaceCategory.PHARMACY
            amenity in listOf("hospital", "clinic", "doctors") -> PlaceCategory.HOSPITAL
            amenity == "parking" -> PlaceCategory.PARKING
            amenity in listOf("bank", "atm") -> PlaceCategory.BANK
            tourism in listOf("hotel", "motel", "hostel", "guest_house", "apartment") -> PlaceCategory.HOTEL
            amenity in listOf("school", "university", "college", "kindergarten") -> PlaceCategory.SCHOOL
            amenity in listOf("gym", "fitness_centre") -> PlaceCategory.GYM
            leisure == "fitness_centre" -> PlaceCategory.GYM
            else -> PlaceCategory.OTHER
        }
    }

    @Serializable
    private data class OverpassResponse(
        val elements: List<OverpassElement> = emptyList(),
    )

    @Serializable
    private data class OverpassElement(
        val type: String = "",
        // nodes carry lat/lon directly; ways and relations carry "bounds" under `out bb`
        val lat: Double? = null,
        val lon: Double? = null,
        val bounds: Bounds? = null,
        val center: Center? = null,
        val tags: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class Bounds(
        @SerialName("minlat") val minLat: Double,
        @SerialName("minlon") val minLon: Double,
        @SerialName("maxlat") val maxLat: Double,
        @SerialName("maxlon") val maxLon: Double,
    ) {
        fun toBoundingBox() = BoundingBox(
            minLat = minLat,
            maxLat = maxLat,
            minLon = minLon,
            maxLon = maxLon,
        )
    }

    @Serializable
    private data class Center(
        val lat: Double = 0.0,
        val lon: Double = 0.0,
    )
}

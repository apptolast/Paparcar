package com.rndeveloper.paparcar.data.places

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.places.RoadWay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The Overpass wire format for drivable roads: how the question is asked and how the answer is read.
 * The data source owns the HTTP call and nothing else — same split as [OverpassPlaceParser].
 * [ROUTE-SNAP-001]
 *
 * It lived inside `OverpassRoadNetworkDataSourceImpl` in androidMain until
 * [OVERPASS-A-SINGLE-MIRROR-HAS-NO-FAILOVER-001], which is why the query and the `isMinor` rule
 * below — a rule with real consequences on the drawn line — had no test at all.
 */
internal object OverpassRoadParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Only drivable highway classes, excluding footways/cycleways/paths so the trail never snaps
     * onto a pedestrian way.
     *
     * `out tags geom` (not plain `out geom`) is the load-bearing choice: `geom` returns each way's
     * full vertex geometry inline, and `tags` is what carries the highway class through to
     * [RoadWay.isMinor]. [ROUTE-QUALITY-001]
     */
    fun buildQuery(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
        timeoutSeconds: Int,
    ): String {
        // bbox order is (south, west, north, east).
        val bbox = "$minLat,$minLon,$maxLat,$maxLon"
        return "[out:json][timeout:$timeoutSeconds];" +
            "way[\"highway\"~\"^($DRIVABLE)$\"]($bbox);" +
            "out tags geom;"
    }

    /** Every way that carries a usable geometry; a segment needs two vertices to be a segment. */
    fun parse(body: String): List<RoadWay> =
        json.decodeFromString<OverpassResponse>(body).elements.mapNotNull { element ->
            val geometry = element.geometry ?: return@mapNotNull null
            if (geometry.size < MIN_VERTICES) return@mapNotNull null
            RoadWay(
                points = geometry.map { vertex ->
                    // Named arguments: `GpsPoint` has eight fields and only the first two carry
                    // meaning here — a road vertex has no accuracy, no clock and no speed.
                    // [FUEL-A-PARKING-WITHOUT-A-COUNTRY-HAS-NO-PRICE-001]
                    GpsPoint(
                        latitude = vertex.lat,
                        longitude = vertex.lon,
                        accuracy = 0f,
                        timestamp = 0L,
                        speed = 0f,
                    )
                },
                // Service ways (parking aisles, driveways, drop-off loops) stay matchable but
                // handicapped so they never steal a straight main-road stretch. [ROUTE-QUALITY-001]
                isMinor = element.tags?.get(TAG_HIGHWAY) == HIGHWAY_SERVICE,
            )
        }

    @Serializable
    private data class OverpassResponse(val elements: List<OverpassWay> = emptyList())

    @Serializable
    private data class OverpassWay(
        val type: String = "",
        val geometry: List<Geom>? = null,
        val tags: Map<String, String>? = null,
    )

    @Serializable
    private data class Geom(val lat: Double = 0.0, val lon: Double = 0.0)

    private const val MIN_VERTICES = 2
    private const val TAG_HIGHWAY = "highway"
    private const val HIGHWAY_SERVICE = "service"
    private const val DRIVABLE =
        "motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service" +
            "|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link"
}

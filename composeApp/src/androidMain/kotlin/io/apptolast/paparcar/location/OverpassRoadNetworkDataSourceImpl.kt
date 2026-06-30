package io.apptolast.paparcar.location

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.places.RoadNetworkDataSource
import io.apptolast.paparcar.domain.places.RoadWay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches drivable OSM roads inside a bbox from the public Overpass API (OpenStreetMap) — the free,
 * no-API-key way to map-match the trip trail onto streets. Mirrors [OverpassPlacesDataSourceImpl]'s
 * HTTP pattern. `out geom` returns each way's full vertex geometry inline. Degrades gracefully:
 * network/parse errors return [Result.failure] and the caller keeps the raw GPS trail. [ROUTE-SNAP-001]
 */
class OverpassRoadNetworkDataSourceImpl : RoadNetworkDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getRoads(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
    ): Result<List<RoadWay>> = withContext(Dispatchers.IO) {
        runCatching {
            val query = buildQuery(minLat, minLon, maxLat, maxLon)
            val body = postOverpass(query) ?: return@runCatching emptyList()
            json.decodeFromString<OverpassResponse>(body).elements
                .mapNotNull { el ->
                    val geom = el.geometry ?: return@mapNotNull null
                    if (geom.size < 2) return@mapNotNull null
                    RoadWay(geom.map { GpsPoint(it.lat, it.lon, 0f, 0L, 0f) })
                }
        }
    }

    private fun buildQuery(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): String {
        // bbox order is (south, west, north, east). Only drivable highway classes, excluding
        // footways/cycleways/paths so the trail never snaps onto a pedestrian way.
        val bbox = "$minLat,$minLon,$maxLat,$maxLon"
        return "[out:json][timeout:$QUERY_TIMEOUT_S];" +
            "way[\"highway\"~\"^($DRIVABLE)$\"]($bbox);" +
            "out geom;"
    }

    private fun postOverpass(query: String): String? {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
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

    @Serializable
    private data class OverpassResponse(val elements: List<OverpassWay> = emptyList())

    @Serializable
    private data class OverpassWay(
        val type: String = "",
        val geometry: List<Geom>? = null,
    )

    @Serializable
    private data class Geom(val lat: Double = 0.0, val lon: Double = 0.0)

    private companion object {
        const val ENDPOINT = "https://overpass-api.de/api/interpreter"
        const val QUERY_TIMEOUT_S = 20
        const val CONNECT_TIMEOUT_MS = 6_000
        const val READ_TIMEOUT_MS = 15_000
        const val DRIVABLE =
            "motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service" +
                "|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link"
    }
}

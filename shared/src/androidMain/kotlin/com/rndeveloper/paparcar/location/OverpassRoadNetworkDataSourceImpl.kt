package com.rndeveloper.paparcar.location

import com.rndeveloper.paparcar.data.places.OverpassRoadParser
import com.rndeveloper.paparcar.domain.places.RoadNetworkDataSource
import com.rndeveloper.paparcar.domain.places.RoadWay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Android transport for the road-network query — HttpURLConnection against the public Overpass API
 * (the free, no-API-key way to map-match the trip trail onto streets). The query and the reading of
 * the answer live in commonMain ([OverpassRoadParser]); this file owns the HTTP call and nothing
 * else. Degrades gracefully: network/parse errors return [Result.failure] and the caller keeps the
 * raw GPS trail. [ROUTE-SNAP-001]
 */
class OverpassRoadNetworkDataSourceImpl : RoadNetworkDataSource {

    override suspend fun getRoads(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
    ): Result<List<RoadWay>> = withContext(Dispatchers.IO) {
        runCatching {
            val query = OverpassRoadParser.buildQuery(minLat, minLon, maxLat, maxLon)
            val body = postOverpass(query) ?: return@runCatching emptyList()
            OverpassRoadParser.parseRoads(body)
        }
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

    private companion object {
        const val ENDPOINT = "https://overpass-api.de/api/interpreter"
        const val CONNECT_TIMEOUT_MS = 6_000
        const val READ_TIMEOUT_MS = 15_000
    }
}

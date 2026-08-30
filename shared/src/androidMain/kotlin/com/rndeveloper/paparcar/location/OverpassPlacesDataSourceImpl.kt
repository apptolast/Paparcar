package com.rndeveloper.paparcar.location

import com.rndeveloper.paparcar.data.places.OverpassPlaceParser
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.places.NearbyPlacePolicy
import com.rndeveloper.paparcar.domain.places.PlacesDataSource
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TAG = "OverpassDS"

/**
 * Android transport for the public Overpass API (OpenStreetMap). HTTP only — which place is worth
 * naming is [NearbyPlacePolicy]'s call and the wire format is [OverpassPlaceParser]'s, both in
 * commonMain and both under test. [POI-A-PLACE-IS-NAMED-ONLY-IF-YOU-ARE-AT-IT-001]
 *
 * No API key required. Degrades gracefully to null on network errors or timeout.
 */
class OverpassPlacesDataSourceImpl : PlacesDataSource {

    override suspend fun getNearbyPlace(lat: Double, lon: Double): Result<PlaceInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = postOverpass(OverpassPlaceParser.buildQuery(lat, lon, QUERY_TIMEOUT_S))
                    ?: return@runCatching null
                val candidates = OverpassPlaceParser.parseCandidates(body)
                NearbyPlacePolicy.pick(candidates, lat, lon).also { picked ->
                    PaparcarLogger.d(TAG, "${candidates.size} candidates near ($lat, $lon) → $picked")
                }
            }.onFailure { e -> PaparcarLogger.w(TAG, "Overpass lookup failed for ($lat, $lon)", e) }
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
        const val QUERY_TIMEOUT_S = 8
        const val CONNECT_TIMEOUT_MS = 6_000
        const val READ_TIMEOUT_MS = 10_000
    }
}

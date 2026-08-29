@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.location

import com.rndeveloper.paparcar.data.places.OverpassRoadParser
import com.rndeveloper.paparcar.domain.places.RoadNetworkDataSource
import com.rndeveloper.paparcar.domain.places.RoadWay
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import kotlin.coroutines.resume

/**
 * iOS transport for the road-network query — NSURLSession instead of HttpURLConnection, same
 * pattern as [IosOverpassPlacesDataSourceImpl]. The query and the reading of the answer live in
 * commonMain ([OverpassRoadParser]). Binding this activates [HomeTripController]'s live
 * map-matching on iOS — the pipeline was already common, only the fetch was missing.
 * [ROUTE-SNAP-001][IOS-A-PARITY-TAIL-001]
 */
class IosOverpassRoadNetworkDataSourceImpl : RoadNetworkDataSource {

    override suspend fun getRoads(
        minLat: Double,
        minLon: Double,
        maxLat: Double,
        maxLon: Double,
    ): Result<List<RoadWay>> = runCatching {
        val query = OverpassRoadParser.buildQuery(minLat, minLon, maxLat, maxLon)
        val body = postOverpass(query) ?: return@runCatching emptyList()
        OverpassRoadParser.parseRoads(body)
    }

    private suspend fun postOverpass(query: String): String? =
        suspendCancellableCoroutine { continuation ->
            val url = NSURL.URLWithString(ENDPOINT)
            if (url == null) { continuation.resume(null); return@suspendCancellableCoroutine }

            val request = NSMutableURLRequest(url)
            request.setHTTPMethod("POST")
            request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField = "Content-Type")
            request.setTimeoutInterval(READ_TIMEOUT_S.toDouble())

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

    private companion object {
        const val ENDPOINT = "https://overpass-api.de/api/interpreter"
        /** The road query returns full way geometry (`out geom`) — heavier than the places answer,
         *  so the read timeout matches the Android transport, not the places one. */
        const val READ_TIMEOUT_S = 15
    }
}

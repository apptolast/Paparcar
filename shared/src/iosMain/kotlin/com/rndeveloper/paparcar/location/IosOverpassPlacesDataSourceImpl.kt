@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.location

import com.rndeveloper.paparcar.data.places.OverpassPlaceParser
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.places.NearbyPlacePolicy
import com.rndeveloper.paparcar.domain.places.PlacesDataSource
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
 * iOS transport for the same Overpass query — NSURLSession instead of HttpURLConnection.
 * [IOS-PLACES-001]
 *
 * The query, the wire format and the choice of place all live in commonMain
 * ([OverpassPlaceParser], [NearbyPlacePolicy]); this file used to be a literal copy of the Android
 * one, which is how a rule could be fixed on Android and stay broken here.
 * [POI-A-PLACE-IS-NAMED-ONLY-IF-YOU-ARE-AT-IT-001]
 */
class IosOverpassPlacesDataSourceImpl : PlacesDataSource {

    override suspend fun getNearbyPlace(lat: Double, lon: Double): Result<PlaceInfo?> =
        runCatching {
            val query = OverpassPlaceParser.buildQuery(lat, lon, QUERY_TIMEOUT_S)
            val body = postOverpass(query) ?: return@runCatching null
            NearbyPlacePolicy.pick(OverpassPlaceParser.parseCandidates(body), lat, lon)
        }

    private suspend fun postOverpass(query: String): String? =
        suspendCancellableCoroutine { continuation ->
            val url = NSURL.URLWithString(ENDPOINT)
            if (url == null) { continuation.resume(null); return@suspendCancellableCoroutine }

            val request = NSMutableURLRequest(url)
            request.setHTTPMethod("POST")
            request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField = "Content-Type")
            request.setValue(CONNECT_TIMEOUT_S.toString(), forHTTPHeaderField = "X-Connect-Timeout")
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
        const val QUERY_TIMEOUT_S = 8
        const val CONNECT_TIMEOUT_S = 6
        const val READ_TIMEOUT_S = 10
    }
}

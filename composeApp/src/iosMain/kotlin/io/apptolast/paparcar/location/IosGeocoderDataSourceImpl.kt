@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.location

import io.apptolast.paparcar.domain.geocoder.GeocoderDataSource
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.SearchResult
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLPlacemark
import platform.Foundation.NSError

/**
 * iOS implementation of [GeocoderDataSource] backed by [CLGeocoder].
 *
 * - [getAddress] uses `reverseGeocodeLocation` (Apple's native reverse geocoder).
 * - [searchByName] uses `geocodeAddressString` (Apple's native forward geocoder).
 *
 * Difference vs the Android impl: Android does forward search against the Photon
 * (Komoot) HTTP API for richer autocomplete coverage. iOS uses CLGeocoder for
 * platform parity — note Apple rate-limits CLGeocoder to ~50 requests/min, which
 * is fine for our search-bar usage but should be re-evaluated if we later add
 * heavy autocomplete-as-you-type traffic.
 */
class IosGeocoderDataSourceImpl : GeocoderDataSource {

    private val geocoder = CLGeocoder()

    override suspend fun getAddress(lat: Double, lon: Double): Result<AddressInfo> = runCatching {
        val placemark = reverseGeocode(lat, lon)
        placemark?.toAddressInfo() ?: AddressInfo(null, null, null, null)
    }

    override suspend fun searchByName(query: String, maxResults: Int): Result<List<SearchResult>> =
        runCatching {
            val placemarks = forwardGeocode(query)
            placemarks.take(maxResults).mapNotNull { it.toSearchResult() }
        }

    private suspend fun reverseGeocode(lat: Double, lon: Double): CLPlacemark? =
        suspendCancellableCoroutine { cont ->
            val location = CLLocation(latitude = lat, longitude = lon)
            geocoder.reverseGeocodeLocation(location) { placemarks: List<*>?, error: NSError? ->
                if (error != null) {
                    cont.resume(null)
                } else {
                    cont.resume(placemarks?.filterIsInstance<CLPlacemark>()?.firstOrNull())
                }
            }
            cont.invokeOnCancellation { geocoder.cancelGeocode() }
        }

    private suspend fun forwardGeocode(query: String): List<CLPlacemark> =
        suspendCancellableCoroutine { cont ->
            geocoder.geocodeAddressString(query) { placemarks: List<*>?, error: NSError? ->
                if (error != null) {
                    cont.resume(emptyList())
                } else {
                    cont.resume(placemarks?.filterIsInstance<CLPlacemark>().orEmpty())
                }
            }
            cont.invokeOnCancellation { geocoder.cancelGeocode() }
        }

    private fun CLPlacemark.toAddressInfo(): AddressInfo = AddressInfo(
        street = listOfNotNull(thoroughfare, subThoroughfare)
            .joinToString(" ")
            .ifBlank { null },
        city = locality ?: subLocality,
        region = administrativeArea,
        country = country,
        countryCode = isoCountryCode?.lowercase(),
    )

    private fun CLPlacemark.toSearchResult(): SearchResult? {
        val coordinate = location?.coordinate ?: return null
        val displayName = listOfNotNull(
            listOfNotNull(thoroughfare, subThoroughfare).joinToString(" ").ifBlank { name },
            locality,
            country,
        ).joinToString(", ").ifBlank { return null }
        return coordinate.useContents {
            SearchResult(
                displayName = displayName,
                lat = latitude,
                lon = longitude,
            )
        }
    }
}

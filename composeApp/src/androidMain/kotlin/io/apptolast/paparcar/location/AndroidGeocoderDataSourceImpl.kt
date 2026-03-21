package io.apptolast.paparcar.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import io.apptolast.paparcar.domain.geocoder.GeocoderDataSource
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

@Serializable
private data class NominatimResult(
    @SerialName("display_name") val displayName: String,
    val lat: String,
    val lon: String,
)

private val nominatimJson = Json { ignoreUnknownKeys = true }

class AndroidGeocoderDataSourceImpl(private val context: Context) : GeocoderDataSource {

    override suspend fun getAddress(lat: Double, lon: Double): Result<AddressInfo> =
        runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                fetchAddressApi33(geocoder, lat, lon)
            } else {
                fetchAddressLegacy(geocoder, lat, lon)
            }
            address?.toAddressInfo() ?: AddressInfo(null, null, null, null)
        }

    private suspend fun fetchAddressApi33(
        geocoder: Geocoder,
        lat: Double,
        lon: Double,
    ): Address? = suspendCancellableCoroutine { cont ->
        geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                cont.resume(addresses.firstOrNull())
            }

            override fun onError(errorMessage: String?) {
                cont.resume(null)
            }
        })
    }

    @Suppress("DEPRECATION")
    private suspend fun fetchAddressLegacy(
        geocoder: Geocoder,
        lat: Double,
        lon: Double,
    ): Address? = withContext(Dispatchers.IO) {
        geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
    }

    override suspend fun searchByName(query: String, maxResults: Int): Result<List<SearchResult>> =
        runCatching {
            withContext(Dispatchers.IO) {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = URL(
                    "https://nominatim.openstreetmap.org/search" +
                        "?q=$encoded&format=json&limit=$maxResults&accept-language=${Locale.getDefault().language}",
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "Paparcar/1.0")
                val json = connection.inputStream.bufferedReader().readText()
                connection.disconnect()
                nominatimJson.decodeFromString<List<NominatimResult>>(json).map { result ->
                    SearchResult(
                        displayName = result.displayName,
                        lat = result.lat.toDouble(),
                        lon = result.lon.toDouble(),
                    )
                }
            }
        }

    private fun Address.toAddressInfo(): AddressInfo = AddressInfo(
        street = listOfNotNull(thoroughfare, subThoroughfare)
            .joinToString(" ")
            .ifBlank { null },
        city = locality ?: subAdminArea,
        region = adminArea,
        country = countryName,
    )
}

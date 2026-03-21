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

// ── Photon (Komoot) — autocomplete API ───────────────────────────────────────

@Serializable
private data class PhotonResponse(
    val features: List<PhotonFeature> = emptyList(),
)

@Serializable
private data class PhotonFeature(
    val properties: PhotonProperties,
    val geometry: PhotonGeometry,
)

@Serializable
private data class PhotonProperties(
    val name: String? = null,
    val street: String? = null,
    val housenumber: String? = null,
    val city: String? = null,
    val country: String? = null,
)

@Serializable
private data class PhotonGeometry(
    @SerialName("coordinates") val coordinates: List<Double>, // [lon, lat]
)

private fun PhotonProperties.toDisplayName(): String =
    listOfNotNull(
        listOfNotNull(street, housenumber).joinToString(" ").ifBlank { name },
        city,
        country,
    ).joinToString(", ")

private val photonJson = Json { ignoreUnknownKeys = true }

// ─────────────────────────────────────────────────────────────────────────────

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
                val deviceLang = Locale.getDefault().language.take(2)
                val lang = if (deviceLang in setOf("de", "en", "fr", "it")) deviceLang else "en"
                val url = URL(
                    "https://photon.komoot.io/api/?q=$encoded&limit=$maxResults&lang=$lang",
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "Paparcar/1.0")
                val json = connection.inputStream.bufferedReader().readText()
                connection.disconnect()
                photonJson.decodeFromString<PhotonResponse>(json).features.map { feature ->
                    SearchResult(
                        displayName = feature.properties.toDisplayName(),
                        lat = feature.geometry.coordinates[1],
                        lon = feature.geometry.coordinates[0],
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

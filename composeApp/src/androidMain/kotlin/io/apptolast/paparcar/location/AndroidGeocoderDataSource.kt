package io.apptolast.paparcar.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import io.apptolast.paparcar.domain.geocoder.GeocoderPort
import io.apptolast.paparcar.domain.model.AddressInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class AndroidGeocoderDataSource(private val context: Context) : GeocoderPort {

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

    private fun Address.toAddressInfo(): AddressInfo = AddressInfo(
        street = listOfNotNull(thoroughfare, subThoroughfare)
            .joinToString(" ")
            .ifBlank { null },
        city = locality ?: subAdminArea,
        region = adminArea,
        country = countryName,
    )
}

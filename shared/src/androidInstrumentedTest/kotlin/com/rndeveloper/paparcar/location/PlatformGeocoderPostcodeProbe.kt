package com.rndeveloper.paparcar.location

import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * ⛔ **Una SONDA, no un test.** No afirma nada sobre nuestro código: pregunta una cosa al sistema
 * operativo y escribe la respuesta. [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]
 *
 * La pregunta, y de ella depende el ticket entero: **¿el `Geocoder` de plataforma rellena
 * `postalCode` en España?** Si lo hace, `postalCode.take(2)` es el `IDProvincia` del ministerio
 * (sus ids son los códigos INE) y toda la cadena de emparejar nombres —acentos, artículos entre
 * paréntesis, comarcas, comunidades autónomas— deja de ser el camino principal.
 *
 * No se puede contestar con Robolectric: allí el `Geocoder` no tiene backend detrás. Hace falta un
 * teléfono de verdad, con red y con Play Services.
 *
 * Imprime también `subAdminArea` y `adminArea` porque son las claves de hoy, y verlas al lado del
 * postal en los MISMOS puntos es lo que permite comparar en vez de opinar.
 *
 * Correr con:
 * ```
 * ./gradlew :shared:connectedDebugAndroidTest --tests '*PlatformGeocoderPostcodeProbe*'
 * adb logcat -d -s POSTCODE_PROBE
 * ```
 */
class PlatformGeocoderPostcodeProbe {

    private data class Point(val name: String, val lat: Double, val lon: Double)

    /**
     * Los seis puntos que ya se midieron contra Photon el 14-09-2026 (para poder comparar fuente a
     * fuente) más seis que atacan lo que puede romperlo: un pueblo pequeño, un área de servicio de
     * autovía, un polígono industrial, una isla y las dos ciudades autónomas.
     */
    private val points = listOf(
        Point("El Puerto de Santa María", 36.6019, -6.2598),
        Point("Madrid · Sol", 40.4169, -3.7035),
        Point("Sevilla · Plaza Nueva", 37.3886, -5.9953),
        Point("Girona", 41.9794, 2.8214),
        Point("Alicante", 38.2699, -0.6983),
        Point("Leon", 42.5987, -5.5671),
        Point("Trujillo (pueblo pequeno)", 39.4590, -5.8820),
        Point("A-4 pk 300 (autovia)", 38.0870, -3.6320),
        Point("Poligono San Fernando de Henares", 40.4230, -3.5300),
        Point("Palma de Mallorca", 39.5696, 2.6502),
        Point("Ceuta", 35.8894, -5.3213),
        Point("Melilla", 35.2923, -2.9381),
    )

    @Test
    fun probe_platformGeocoder_postalCodeCoverageInSpain() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Fijo, no `Locale.getDefault()` como la app: una sonda que cambia de idioma según el móvil
        // no se puede comparar con la de mañana. El locale del device se imprime abajo.
        val geocoder = Geocoder(context, Locale.forLanguageTag("es-ES"))

        log(
            "=== inicio · API ${Build.VERSION.SDK_INT} · ${Build.MANUFACTURER} ${Build.MODEL}" +
                " · device locale=${Locale.getDefault()} ===",
        )
        var withPostcode = 0
        var answered = 0

        points.forEach { point ->
            val address = runCatching { fetch(geocoder, point) }.getOrNull()
            if (address != null) answered++
            if (!address?.postalCode.isNullOrBlank()) withPostcode++
            log(
                buildString {
                    append(point.name.padEnd(34))
                    append(" postal=").append(address?.postalCode ?: "-")
                    append(" | locality=").append(address?.locality ?: "-")
                    append(" | subAdminArea=").append(address?.subAdminArea ?: "-")
                    append(" | adminArea=").append(address?.adminArea ?: "-")
                    append(" | cc=").append(address?.countryCode ?: "-")
                },
            )
        }

        log("=== RESUMEN: $withPostcode/${points.size} con postal · $answered/${points.size} contestaron ===")
    }

    /** La MISMA bifurcación que usa `AndroidGeocoderDataSourceImpl`, para medir el camino real. */
    private fun fetch(geocoder: Geocoder, point: Point): Address? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            var result: Address? = null
            val latch = CountDownLatch(1)
            geocoder.getFromLocation(point.lat, point.lon, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    result = addresses.firstOrNull()
                    latch.countDown()
                }

                override fun onError(errorMessage: String?) {
                    log("${point.name}: onError -> $errorMessage")
                    latch.countDown()
                }
            })
            latch.await(GEOCODE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            result
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(point.lat, point.lon, 1)?.firstOrNull()
        }

    private fun log(message: String) = android.util.Log.i(TAG, message)

    private companion object {
        const val TAG = "POSTCODE_PROBE"
        const val GEOCODE_TIMEOUT_SECONDS = 15L
    }
}

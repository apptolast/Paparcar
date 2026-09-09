package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The whole Spanish adapter, request included, driven from commonMain with no network.
 *
 * This is the payoff of putting the transport in commonMain with Ktor instead of duplicating it per
 * platform: what gets tested here is the part that actually breaks — which URL we ask for, what we
 * do with the answer — and it is tested once for both platforms. [FUEL-STATIONS-LIST-001]
 */
class MineturStationsSourceTest {

    private val provincesJson = """
        [{"IDPovincia":"11","IDCCAA":"01","Provincia":"CÁDIZ","CCAA":"Andalucia"},
         {"IDPovincia":"02","IDCCAA":"07","Provincia":"ALBACETE","CCAA":"Castilla la Mancha"}]
    """.trimIndent()

    /** Only latitude varies, so the expected order is unambiguous: ~33 m, ~1,1 km, ~19 km. */
    private val stationsJson = """
        {
          "Fecha": "09/09/2026 08:30:00",
          "ListaEESSPrecio": [
            {"IDEESS":"LEJOS","Rótulo":"Lejos","Latitud":"36,700000","Longitud (WGS84)":"-6,292400","Precio Gasolina 95 E5":"1,700"},
            {"IDEESS":"MEDIO","Rótulo":"Medio","Latitud":"36,540000","Longitud (WGS84)":"-6,292400","Precio Gasolina 95 E5":"1,800"},
            {"IDEESS":"CERCA","Rótulo":"Cerca","Latitud":"36,530000","Longitud (WGS84)":"-6,292400","Precio Gasolina 95 E5":"1,900"}
          ]
        }
    """.trimIndent()

    private val asked = mutableListOf<String>()

    private fun cadiz() = FuelStationsQuery(
        latitude = 36.5297,
        longitude = -6.2924,
        countryCode = "ES",
        regionName = "Cádiz",
        radiusMeters = 5_000,
    )

    private fun source(
        stationsStatus: HttpStatusCode = HttpStatusCode.OK,
    ): MineturStationsSource {
        val engine = MockEngine { request ->
            val url = request.url.toString()
            asked += url
            when {
                url.contains("Provincias") -> respond(
                    provincesJson,
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respond(
                    if (stationsStatus == HttpStatusCode.OK) stationsJson else "",
                    stationsStatus,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        return MineturStationsSource(HttpClient(engine))
    }

    @Test
    fun `should_answerForSpainOnly`() {
        val source = source()
        assertTrue(source.covers("ES"))
        assertTrue(source.covers("es"))
        assertFalse(source.covers("FR"))
    }

    @Test
    fun `should_askForTheProvinceOfTheRegionItWasGiven`() = runTest {
        source().stationsNear(cadiz()).getOrThrow()

        // The province id has to reach the URL — the whole country is 12 MB.
        assertTrue(asked.any { it.endsWith("/Listados/Provincias/") }, "asked: $asked")
        assertTrue(asked.any { it.endsWith("/FiltroProvincia/11") }, "asked: $asked")
    }

    @Test
    fun `should_keepOnlyWhatIsWithinRadius_nearestFirst`() = runTest {
        val result = source()
            .stationsNear(cadiz())
            .getOrThrow()

        // A province is a whole province: the radius filter is ours, and so is the order.
        assertEquals(listOf("CERCA", "MEDIO"), result.stations.map { it.id })
        assertEquals("09/09/2026 08:30:00", result.reportedAt)
    }

    @Test
    fun `should_fetchTheProvinceCatalogueOnce_acrossCalls`() = runTest {
        val source = source()
        repeat(3) {
            source.stationsNear(cadiz()).getOrThrow()
        }

        assertEquals(1, asked.count { it.contains("Provincias") }, "asked: $asked")
        assertEquals(3, asked.count { it.contains("FiltroProvincia") })
    }

    @Test
    fun `should_failLoudly_whenTheRegionIsNotASpanishProvince`() = runTest {
        // Not silence and not an empty list: "no stations near you" and "we could not ask" are
        // different sentences, and the UI must never say the first when it means the second.
        val result = source().stationsNear(
            FuelStationsQuery(48.8566, 2.3522, countryCode = "ES", regionName = "Île-de-France")
        )

        assertTrue(result.isFailure)
        assertTrue(asked.none { it.contains("FiltroProvincia") }, "asked: $asked")
    }

    @Test
    fun `should_failLoudly_whenTheFeedIsDown`() = runTest {
        val result = source(stationsStatus = HttpStatusCode.ServiceUnavailable)
            .stationsNear(cadiz())

        assertTrue(result.isFailure)
    }
}

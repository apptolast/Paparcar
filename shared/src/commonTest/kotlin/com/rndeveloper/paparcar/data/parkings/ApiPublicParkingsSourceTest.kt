package com.rndeveloper.paparcar.data.parkings

import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.ParkingZoneGrid
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsResult
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **La API ganando, y sobre todo la API PERDIENDO.** [PARKINGS-ONE-CALL-MERGED-001]
 *
 * ⛔ El grueso son negativas, y no por costumbre: lo que este decorador tiene que garantizar es que
 * **Overpass siga contestando cuando él no pueda**. La 1.5.0 está publicada y lee Overpass; si el
 * fallback no entra, la pantalla dice *«aquí no hay parkings»* — la mentira que todo este expediente
 * existe para no contar. Un fallback que nadie ha visto fallar no es un fallback.
 */
class ApiPublicParkingsSourceTest {

    /** Sol. El caso de uso ya ajusta el punto al centro de su zona, así que la celda es la misma. */
    private val query = PublicParkingsQuery(latitude = 40.4168, longitude = -3.7038, countryCode = "es")

    private val cell = ParkingZoneGrid.zoneOf(query.latitude, query.longitude).id

    private fun body(serve: Boolean = true, parkings: String = ONE_PARKING) = """
        {"cell":"$cell","baseVersion":"2026-W45","generatedAt":1762000000000,
         "serve":$serve,
         "licences":[{"id":"odbl-1.0","source":"osm","attribution":"© OpenStreetMap contributors"}],
         "parkings":[$parkings]}
    """.trimIndent()

    private fun client(handler: MockEngine) = HttpClient(handler)

    @Test
    fun should_answerFromTheApi_when_itServes() = runTest {
        val fallback = SpyFallback()
        val engine = MockEngine { request ->
            // La celda viaja en la URL, y es lo que hace que el CDN acierte: toda la zona pregunta
            // por la MISMA URL.
            assertTrue(request.url.toString().contains("cell=$cell"), "la celda va en la URL: ${request.url}")
            assertTrue(request.url.toString().contains("v=1"), "el esquema va en la URL")
            respond(body(), HttpStatusCode.OK, jsonHeaders)
        }
        val source = ApiPublicParkingsSource(client(engine), fallback)

        val result = source.parkingsNear(query).getOrThrow()

        assertEquals(1, result.parkings.size)
        assertEquals("osm/way/1", result.parkings.single().id)
        assertEquals(PublicParkingKind.UNDERGROUND, result.parkings.single().kind)
        assertEquals(FeeInfo.PAID, result.parkings.single().fee)
        // ⛔ Recién llegado de la red, así que NO se marca como recordado: lo contrario pintaría una
        // respuesta viva con la etiqueta de la caché.
        assertNull(result.rememberedAtMs)
        assertEquals(0, fallback.asks, "la API contestó: Overpass no tiene por qué enterarse")
    }

    @Test
    fun should_fallBackToOverpass_when_theApiFailsAnyWay() = runTest {
        // Una LISTA de formas de fallar, no un `catch` genérico: cada una es un camino real por el
        // que esto se cae en producción, y las cuatro tienen que acabar igual.
        val ways = listOf<MockEngine>(
            MockEngine { respondError(HttpStatusCode.ServiceUnavailable) },
            MockEngine { respondError(HttpStatusCode.NotFound) },
            MockEngine { respondError(HttpStatusCode.InternalServerError) },
            MockEngine { respond("no soy json", HttpStatusCode.OK, jsonHeaders) },
        )
        ways.forEach { engine ->
            val fallback = SpyFallback()
            val result = ApiPublicParkingsSource(client(engine), fallback).parkingsNear(query).getOrThrow()
            assertEquals(1, fallback.asks, "cada forma de fallar tiene que ir a Overpass")
            assertEquals(FALLBACK_ID, result.parkings.single().id)
        }
    }

    @Test
    fun should_fallBackToOverpass_when_theBackendSaysDoNotServe() = runTest {
        // El interruptor de §D.3: la app no tiene Remote Config, así que la orden viaja en la
        // respuesta. No es un error y no se trata como tal — el efecto es el mismo y sin ruido.
        val fallback = SpyFallback()
        val engine = MockEngine { respond(body(serve = false), HttpStatusCode.OK, jsonHeaders) }

        val result = ApiPublicParkingsSource(client(engine), fallback).parkingsNear(query).getOrThrow()

        assertEquals(1, fallback.asks)
        assertEquals(FALLBACK_ID, result.parkings.single().id)
    }

    @Test
    fun should_keepServing_when_theBackendSaysNothingAboutTheSwitch() = runTest {
        // Un backend viejo, de antes de que existiera la bandera, NO puede apagar el carril.
        val fallback = SpyFallback()
        val engine = MockEngine {
            respond(
                """{"cell":"$cell","baseVersion":"x","parkings":[$ONE_PARKING]}""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }

        val result = ApiPublicParkingsSource(client(engine), fallback).parkingsNear(query).getOrThrow()

        assertEquals(0, fallback.asks, "sin bandera se sirve")
        assertEquals("osm/way/1", result.parkings.single().id)
    }

    @Test
    fun should_sortByDistanceFromTheCaller_when_itAnswers() = runTest {
        // Mismo orden que `OsmPublicParkingsSource`: medido desde el punto por el que se PREGUNTÓ,
        // no por el que contestó el servidor. La fila dice «a 300 m» y tiene que ser verdad.
        val far = """{"id":"lejos","lat":40.44,"lon":-3.70,"kind":"SURFACE","fee":"UNKNOWN"}"""
        val near = """{"id":"cerca","lat":40.4169,"lon":-3.7039,"kind":"SURFACE","fee":"UNKNOWN"}"""
        val engine = MockEngine { respond(body(parkings = "$far,$near"), HttpStatusCode.OK, jsonHeaders) }

        val result = ApiPublicParkingsSource(client(engine), SpyFallback()).parkingsNear(query).getOrThrow()

        assertEquals(listOf("cerca", "lejos"), result.parkings.map { it.id })
    }

    @Test
    fun should_notInventACategory_when_theBackendIsNewerThanTheApp() = runTest {
        // El backend puede desplegarse antes que la app. Un `kind` o un `fee` que no conocemos cae a
        // UNKNOWN: un parking que no sabemos clasificar SIGUE ESTANDO AHÍ.
        val odd = """{"id":"raro","lat":40.4168,"lon":-3.7038,"kind":"HELIPUERTO","fee":"QUIZA"}"""
        val engine = MockEngine { respond(body(parkings = odd), HttpStatusCode.OK, jsonHeaders) }

        val parking = ApiPublicParkingsSource(client(engine), SpyFallback())
            .parkingsNear(query).getOrThrow().parkings.single()

        assertEquals(PublicParkingKind.UNKNOWN, parking.kind)
        assertEquals(FeeInfo.UNKNOWN, parking.fee)
    }

    @Test
    fun should_delegateWhatItRemembers_when_askedWithoutTheNetwork() = runTest {
        // La red no recuerda nada, así que `remembered` tiene que seguir llegando a quien SÍ
        // sostiene algo. Si este decorador contestara null, el mapa volvería a salir vacío en frío.
        val fallback = SpyFallback()
        val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }

        val remembered = ApiPublicParkingsSource(client(engine), fallback).remembered(query)

        assertEquals(FALLBACK_ID, remembered?.parkings?.single()?.id)
    }

    private class SpyFallback : PublicParkingsSource {
        var asks = 0
        override fun covers(countryCode: String) = true
        override suspend fun parkingsNear(query: PublicParkingsQuery): Result<PublicParkingsResult> {
            asks++
            return Result.success(PublicParkingsResult(listOf(fallbackParking)))
        }
        override suspend fun remembered(query: PublicParkingsQuery) =
            PublicParkingsResult(listOf(fallbackParking), rememberedAtMs = 1)
    }

    private companion object {
        const val FALLBACK_ID = "overpass/1"

        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

        val ONE_PARKING = """
            {"id":"osm/way/1","lat":40.4168,"lon":-3.7038,"kind":"UNDERGROUND","fee":"PAID",
             "name":"Probe"}
        """.trimIndent()

        val fallbackParking = PublicParking(
            id = FALLBACK_ID,
            name = null,
            operator = null,
            kind = PublicParkingKind.SURFACE,
            latitude = 40.0,
            longitude = -3.0,
        )
    }
}

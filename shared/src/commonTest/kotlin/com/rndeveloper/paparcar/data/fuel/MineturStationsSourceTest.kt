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

    /**
     * Only latitude varies, so the expected order is unambiguous: ~33 m, ~1,1 km, ~19 km.
     *
     * `NEXT_TOWN` se añadió con [FUEL-A-RADIUS-DOES-NOT-RESPECT-A-TOWN-BOUNDARY-001]: dentro del
     * radio (~330 m) y con el `Municipio` del pueblo de al lado, que es la fila que el atajo por
     * municipio hacía desaparecer. Va entre MEDIO y CERCA en distancia, así que también vigila que
     * el orden siga siendo por cercanía y no por el municipio de nadie.
     */
    private val stationsJson = """
        {
          "Fecha": "09/09/2026 08:30:00",
          "ListaEESSPrecio": [
            {"IDEESS":"LEJOS","Rótulo":"Lejos","Municipio":"Puerto de Santa María (El)","Latitud":"36,700000","Longitud (WGS84)":"-6,292400","Precio Gasolina 95 E5":"1,700"},
            {"IDEESS":"MEDIO","Rótulo":"Medio","Municipio":"Puerto de Santa María (El)","Latitud":"36,540000","Longitud (WGS84)":"-6,292400","Precio Gasolina 95 E5":"1,800"},
            {"IDEESS":"NEXT_TOWN","Rótulo":"BP","Municipio":"Puerto Real","Latitud":"36,532700","Longitud (WGS84)":"-6,292400","Precio Gasolina 95 E5":"1,850"},
            {"IDEESS":"CERCA","Rótulo":"Cerca","Municipio":"Puerto de Santa María (El)","Latitud":"36,530000","Longitud (WGS84)":"-6,292400","Precio Gasolina 95 E5":"1,900"}
          ]
        }
    """.trimIndent()

    /**
     * Lo que `FiltroMunicipio/1789` contestaría de verdad: **sólo las filas de ese municipio**.
     * [FUEL-A-RADIUS-DOES-NOT-RESPECT-A-TOWN-BOUNDARY-001]
     *
     * ⛔ Existe porque sin él el testigo pasaba por la razón equivocada: el `MockEngine` servía el
     * fixture ENTERO a cualquier URL de estaciones, así que la consulta por municipio devolvía
     * también la del pueblo de al lado — o sea, el mock no podía reproducir el bug que el test dice
     * vigilar. Reinstaurado el atajo, el testigo seguía verde.
     */
    private val townStationsJson = """
        {
          "Fecha": "09/09/2026 08:30:00",
          "ListaEESSPrecio": [
            {"IDEESS":"LEJOS","Rótulo":"Lejos","Municipio":"Puerto de Santa María (El)","Latitud":"36,700000","Longitud (WGS84)":"-6,292400","Precio Gasolina 95 E5":"1,700"},
            {"IDEESS":"MEDIO","Rótulo":"Medio","Municipio":"Puerto de Santa María (El)","Latitud":"36,540000","Longitud (WGS84)":"-6,292400","Precio Gasolina 95 E5":"1,800"},
            {"IDEESS":"CERCA","Rótulo":"Cerca","Municipio":"Puerto de Santa María (El)","Latitud":"36,530000","Longitud (WGS84)":"-6,292400","Precio Gasolina 95 E5":"1,900"}
          ]
        }
    """.trimIndent()

    /** Verbatim from `/Listados/Municipios/`, read live on 10-09-2026 — article in brackets and
     *  `IDProvincia` WITH its 'r', unlike the province listing above.
     *  [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001] */
    private val municipalitiesJson = """
        [{"IDMunicipio":"1789","IDProvincia":"11","IDCCAA":"01","Municipio":"Puerto de Santa María (El)","Provincia":"CÁDIZ","CCAA":"Andalucia"}]
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
        /** Una PROVINCIA que contesta vacía. [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]
         *  ⛔ Ya no hay gemelo para el municipio: no se le pregunta.
         *  [FUEL-A-RADIUS-DOES-NOT-RESPECT-A-TOWN-BOUNDARY-001] */
        provinceStations: String? = null,
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
                url.endsWith("/Listados/Municipios/") -> respond(
                    municipalitiesJson,
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
                // El endpoint de municipio contesta lo de ESE municipio — si sirviera el fixture
                // entero, ningún test podría ver lo que el atajo escondía.
                // [FUEL-A-RADIUS-DOES-NOT-RESPECT-A-TOWN-BOUNDARY-001]
                url.contains("/FiltroMunicipio/") -> respond(
                    townStationsJson,
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respond(
                    when {
                        stationsStatus != HttpStatusCode.OK -> ""
                        provinceStations != null -> provinceStations
                        else -> stationsJson
                    },
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

    // ── El pueblo NOMBRA la provincia, y es lo que salvó a España ────────────
    // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
    // ⛔ Lo que ya no hace es SERVIR la lista. [FUEL-A-RADIUS-DOES-NOT-RESPECT-A-TOWN-BOUNDARY-001]

    @Test
    fun `should_askForTheProvinceOfTheTown_when_theAddressCarriesOne`() = runTest {
        source().stationsNear(cadiz().copy(cityName = "El Puerto de Santa María")).getOrThrow()

        assertTrue(asked.any { it.endsWith("/FiltroProvincia/11") }, "asked: $asked")
        assertTrue(
            asked.none { it.contains("/FiltroMunicipio/") },
            "un municipio define un payload, no la cobertura de un radio: $asked",
        )
    }

    @Test
    fun `should_askSpainAnyway_when_theRegionIsAnAutonomousCommunity`() = runTest {
        // ⛔ THE bug, in one test. Measured on a real phone: the region came through as «Andalucía»,
        // which is no Spanish province, so the source threw and the registry fell back to a feed
        // with no prices — and the lane told the user Spain publishes none. With the town as the
        // first key the ministry gets asked, and it answers.
        val result = source()
            .stationsNear(cadiz().copy(regionName = "Andalucía", cityName = "El Puerto de Santa María"))

        assertTrue(result.isSuccess, "the ministry has to be asked, not given up on")
        assertTrue(asked.any { it.endsWith("/FiltroProvincia/11") }, "asked: $asked")
    }

    @Test
    fun `should_bringAStationOfTheNextTown_when_itIsInsideTheRadius`() = runTest {
        // ⛔ EL TESTIGO de la issue #113. `NEXT_TOWN` está a ~330 m del usuario y en OTRO municipio:
        // preguntando por `FiltroMunicipio` no salía, y el usuario tenía la gasolinera delante.
        // Medido sobre el feed real de Cádiz, el atajo escondía el 11 % de lo que hay a 5 km — el
        // 90 % en la bahía. [FUEL-A-RADIUS-DOES-NOT-RESPECT-A-TOWN-BOUNDARY-001]
        val result = source()
            .stationsNear(cadiz().copy(cityName = "El Puerto de Santa María"))
            .getOrThrow()

        assertTrue(
            result.stations.any { it.id == "NEXT_TOWN" },
            "una estación dentro del radio no desaparece por estar en el pueblo de al lado: " +
                "${result.stations.map { it.id }}",
        )
    }

    @Test
    fun `should_notFetchTheTownCatalogue_when_thereIsNoTownToLookUp`() = runTest {
        // ⛔ Un catálogo entero de municipios para no mirarlo. Lo cazaron los tests de provincia.
        source().stationsNear(cadiz()).getOrThrow()

        assertTrue(asked.none { it.endsWith("/Listados/Municipios/") }, "asked: ${'$'}asked")
    }

    @Test
    fun `should_notFetchTheProvinceCatalogue_when_thereIsNoRegionToLookUp`() = runTest {
        // ⛔ El MISMO fallo que el de arriba, tres líneas más abajo y sin cazar: `provinces()` es un
        // argumento de `idFor`, así que se evaluaba aunque `regionName` fuese null — 52 provincias
        // descargadas para devolver null acto seguido.
        // [FUEL-AN-UNKNOWN-COUNTRY-IS-NOT-A-COUNTRY-WITHOUT-PRICES-001]
        //
        // ⚠️ El punto es PARÍS y no Cádiz como cuando se escribió, y el motivo importa: desde
        // [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001] un punto español sin ciudad ni región ya no es un
        // callejón — lo rescatan las coordenadas, que es justo la gracia de aquel ticket. Lo que
        // este test vigila no era el callejón sino la DESCARGA inútil, así que hace falta un punto
        // que ninguna clave pueda resolver. Fuera de España no hay caja que valga, y la pregunta
        // sigue siendo la misma: sin región que buscar, el catálogo no se toca.
        val result = source().stationsNear(
            FuelStationsQuery(48.8566, 2.3522, countryCode = "ES", regionName = null),
        )

        assertTrue(result.isFailure, "with nothing to key on, the ministry cannot be asked")
        assertTrue(asked.isEmpty(), "nothing at all should have been downloaded: $asked")
    }

    @Test
    fun `should_askByPostalCode_before_theRegionName_andWithoutAnyCatalogue`() = runTest {
        // [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001] «Andalucía» es el caso que costó dos tickets: no
        // es una provincia, así que el camino por nombre no puede resolverla. El postal sí, y sin
        // descargar catálogo ninguno — porque no empareja nombres, hace aritmética.
        val result = source().stationsNear(
            cadiz().copy(regionName = "Andalucía", cityName = null, postalCode = "11500"),
        )

        assertTrue(result.isSuccess)
        assertTrue(asked.any { it.endsWith("/FiltroProvincia/11") }, "asked: ${'$'}asked")
        assertTrue(asked.none { it.contains("/Listados/") }, "el postal no necesita catálogo: ${'$'}asked")
    }

    @Test
    fun `should_ignoreAPostalCodeThatIsNotSpanish_andFallBackToTheName`() = runTest {
        // Un postal extranjero no puede secuestrar el enrutado: se descarta por su forma y manda la
        // clave de siempre. Aquí la región SÍ es una provincia, así que contesta esa.
        val result = source().stationsNear(cadiz().copy(cityName = null, postalCode = "75001"))

        assertTrue(result.isSuccess)
        assertTrue(asked.any { it.endsWith("/Listados/Provincias/") }, "asked: ${'$'}asked")
        assertTrue(asked.any { it.endsWith("/FiltroProvincia/11") }, "asked: ${'$'}asked")
    }

    // ── La tercera clave: las coordenadas ────────────────────────────────────
    // [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]

    @Test
    fun `should_askByCoordinates_when_thereIsNeitherTownNorRegion`() = runTest {
        // Lo que deja un geocode mudo: ni ciudad, ni región, ni país. Antes esto no se podía
        // preguntar y el usuario se quedaba sin precios en un país que los publica; ahora el punto
        // se reconoce solo. El Puerto de Santa María → provincia 11.
        val result = source().stationsNear(
            FuelStationsQuery(36.5297, -6.2924, countryCode = "", regionName = null),
        )

        assertTrue(result.isSuccess, "las coordenadas son clave suficiente: ${'$'}asked")
        assertTrue(asked.any { it.endsWith("/FiltroProvincia/11") }, "asked: ${'$'}asked")
        // Y sin catálogos: la clave numérica no empareja nombres, así que no hay nada que descargar.
        assertTrue(asked.none { it.contains("/Listados/") }, "asked: ${'$'}asked")
    }

    @Test
    fun `should_claimTheQuery_onlyWhenNobodyNamedTheCountry`() = runTest {
        val source = source()
        // País desconocido + punto español: lo reclama, que es el caso que existe para arreglar.
        assertTrue(source.claims(FuelStationsQuery(36.5297, -6.2924, countryCode = "")))
        // ⛔ Pero NO corrige a quien sí contestó: una caja envolvente rebosa por las fronteras, y
        // «PT» es una respuesta, no un hueco.
        assertFalse(source.claims(FuelStationsQuery(36.5297, -6.2924, countryCode = "PT")))
        // Y fuera de España no reclama nada aunque nadie haya dicho el país.
        assertFalse(source.claims(FuelStationsQuery(48.8566, 2.3522, countryCode = "")))
    }

    @Test
    fun `should_failLoudly_when_aGuessedProvinceAnswersEmpty`() = runTest {
        // ⛔ EL testigo del ticket. Una provincia adivinada por coordenadas acierta el 94,6 %; el
        // resto son puntos de frontera donde se pregunta a la provincia de al lado. Devolver esa
        // lista vacía diría «no hay ninguna gasolinera a 5 km» — la clase de frase que este código
        // existe para no decir. Fallar manda al registro al feed sin precios, que es lo que había
        // antes de poder adivinar: no se pierde nada y no se miente.
        val result = source(provinceStations = "{\"ListaEESSPrecio\": []}")
            .stationsNear(FuelStationsQuery(36.5297, -6.2924, countryCode = "", regionName = null))

        assertTrue(result.isFailure, "un vacío desde una provincia adivinada no es un veredicto")
    }

    @Test
    fun `should_trustAnEmptyAnswer_when_theProvinceWasNamed`() = runTest {
        // La otra mitad de la regla, y va adosada: con provincia NOMBRADA el vacío sí es respuesta
        // — lo dice quien sabe dónde está. Sin este par, la regla de arriba podría convertirse en
        // «un vacío nunca es respuesta» y Cádiz dejaría de poder decir que no hay nada cerca.
        val result = source(provinceStations = "{\"ListaEESSPrecio\": []}")
            .stationsNear(cadiz())

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().stations.isEmpty())
    }

    @Test
    fun `should_keepOnlyWhatIsWithinRadius_nearestFirst`() = runTest {
        val result = source()
            .stationsNear(cadiz())
            .getOrThrow()

        // A province is a whole province: the radius filter is ours, and so is the order — que es
        // por CERCANÍA, sin que el municipio de cada fila pinte nada.
        // [FUEL-A-RADIUS-DOES-NOT-RESPECT-A-TOWN-BOUNDARY-001]
        assertEquals(listOf("CERCA", "NEXT_TOWN", "MEDIO"), result.stations.map { it.id })
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

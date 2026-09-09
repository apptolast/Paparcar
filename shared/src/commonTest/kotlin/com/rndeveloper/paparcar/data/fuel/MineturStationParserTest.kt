package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelAvailability
import com.rndeveloper.paparcar.domain.model.FuelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The fixture is a verbatim trim of the live feed (Cádiz, 08-09-2026): same keys, same accents,
 * same decimal commas, same empty strings. A parser tested against a tidied-up sample is a parser
 * tested against a format that does not exist. [FUEL-STATIONS-LIST-001]
 */
class MineturStationParserTest {

    private val fixture = """
        {
          "Fecha": "08/09/2026 23:52:46",
          "ListaEESSPrecio": [
            {
              "IDEESS": "6707",
              "Rótulo": "BP",
              "Dirección": "AVENIDA PUERTO DE LEVANTE, S/N",
              "Municipio": "Alcalá de los Gazules",
              "Horario": "L-D: 07:00-22:00",
              "Latitud": "36,460000",
              "Longitud (WGS84)": "-5,730944",
              "Precio Gasolina 95 E5": "1,945",
              "Precio Gasoleo A": "1,879",
              "Precio Gases licuados del petróleo": ""
            },
            {
              "IDEESS": "1234",
              "Rótulo": "Repsol",
              "Dirección": "CALLE LARGA 1",
              "Municipio": "El Puerto",
              "Horario": "24H",
              "Latitud": "36,594000",
              "Longitud (WGS84)": "-6,233200",
              "Precio Gasolina 95 E5": "1,955",
              "Precio Gasoleo A": "1,889",
              "Precio Gases licuados del petróleo": "0,999"
            },
            {
              "IDEESS": "9999",
              "Rótulo": "Sin coordenadas",
              "Latitud": "",
              "Longitud (WGS84)": "",
              "Precio Gasolina 95 E5": "1,700"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `should_readDecimalCommas_inPricesAndCoordinates`() {
        // The trap that drops EVERY station silently: "1,945".toDouble() is null.
        val station = MineturStationParser.parseStations(fixture).first()
        assertEquals(1.945, station.prices.getValue(FuelType.GASOLINE).amount)
        assertEquals(36.46, station.latitude, absoluteTolerance = 1e-6)
        assertEquals(-5.730944, station.longitude, absoluteTolerance = 1e-6)
    }

    @Test
    fun `should_readKeysWithAccentsAndSpaces`() {
        val station = MineturStationParser.parseStations(fixture).first()
        assertEquals("BP", station.name)
        assertEquals("AVENIDA PUERTO DE LEVANTE, S/N", station.address)
        assertEquals("Alcalá de los Gazules", station.town)
    }

    @Test
    fun `should_treatAnEmptyPrice_asNotSold_neverAsZero`() {
        // ⛔ The one that would break the feature: a station with no LPG read as 0,00 € would rank
        // as the cheapest LPG in town.
        val bp = MineturStationParser.parseStations(fixture).first()
        assertNull(bp.prices[FuelType.LPG_CNG])
        assertTrue(bp.isKnownNotToSell(FuelType.LPG_CNG))

        val repsol = MineturStationParser.parseStations(fixture)[1]
        assertEquals(FuelAvailability.SOLD, repsol.availabilityOf(FuelType.LPG_CNG))
        assertEquals(0.999, repsol.prices.getValue(FuelType.LPG_CNG).amount)
    }

    @Test
    fun `should_sayNotSold_notUnknown_becauseThisFeedCanStateANegative`() {
        // The distinction earns its keep against OSM, where an untagged fuel means nobody said —
        // 56% of stations measured. Here an empty string is the ministry SAYING there is no pump,
        // so this station may be hidden from an LPG driver and an OSM one may not.
        val bp = MineturStationParser.parseStations(fixture).first()
        assertEquals(FuelAvailability.NOT_SOLD, bp.availabilityOf(FuelType.LPG_CNG))
        assertEquals(FuelAvailability.SOLD, bp.availabilityOf(FuelType.GASOLINE))
    }

    @Test
    fun `should_dropAStationWithoutCoordinates_ratherThanPlaceItInTheAtlantic`() {
        val ids = MineturStationParser.parseStations(fixture).map { it.id }
        assertEquals(listOf("6707", "1234"), ids)
    }

    @Test
    fun `should_surviveTheUtf8Bom_thatTheFeedSends`() {
        val withBom = "﻿$fixture"
        assertEquals(2, MineturStationParser.parseStations(withBom).size)
    }

    @Test
    fun `should_readTheFeedsOwnTimestamp_verbatim`() {
        // Shown next to the prices: a price with no hour is a price nobody can weigh.
        assertEquals("08/09/2026 23:52:46", MineturStationParser.parseReportedAt(fixture))
    }

    @Test
    fun `should_quoteEveryPriceInEuros`() {
        val station = MineturStationParser.parseStations(fixture)[1]
        assertTrue(station.prices.values.all { it.currency == "EUR" })
    }
}

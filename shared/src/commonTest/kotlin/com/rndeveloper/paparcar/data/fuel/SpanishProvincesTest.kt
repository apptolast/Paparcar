package com.rndeveloper.paparcar.data.fuel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Fixture verbatim from `/Listados/Provincias/`, typo included. [FUEL-STATIONS-LIST-001] */
class SpanishProvincesTest {

    private val provinces = SpanishProvinces.parse(
        """
        [
          {"IDPovincia": "02", "IDCCAA": "07", "Provincia": "ALBACETE", "CCAA": "Castilla la Mancha"},
          {"IDPovincia": "04", "IDCCAA": "01", "Provincia": "ALMERÍA", "CCAA": "Andalucia"},
          {"IDPovincia": "11", "IDCCAA": "01", "Provincia": "CÁDIZ", "CCAA": "Andalucia"},
          {"IDPovincia": "20", "IDCCAA": "16", "Provincia": "GIPUZKOA", "CCAA": "Pais Vasco"}
        ]
        """.trimIndent(),
    )

    @Test
    fun `should_readTheMisspelledIdField_becauseThatIsWhatTheFeedSends`() {
        // ⛔ IDPovincia, no 'r'. Reading IDProvincia returns null for all 52 rows and the feature
        // dies with no error anywhere.
        assertEquals(4, provinces.size)
        assertEquals("11", provinces["cadiz"])
    }

    @Test
    fun `should_matchWhatTheGeocoderWrites_againstWhatTheFeedShouts`() {
        // The geocoder says "Cádiz", the ministry says "CÁDIZ", and a user in Almería types neither.
        assertEquals("11", SpanishProvinces.idFor("Cádiz", provinces))
        assertEquals("11", SpanishProvinces.idFor("CÁDIZ", provinces))
        assertEquals("11", SpanishProvinces.idFor("cadiz", provinces))
        assertEquals("11", SpanishProvinces.idFor("  Cádiz  ", provinces))
        assertEquals("04", SpanishProvinces.idFor("Almeria", provinces))
    }

    @Test
    fun `should_answerNothing_outsideSpain_whichIsTheNormalCase`() {
        // Most of the world is not a Spanish province. That is an answer, not an error.
        assertNull(SpanishProvinces.idFor("Lisboa", provinces))
        assertNull(SpanishProvinces.idFor(null, provinces))
        assertNull(SpanishProvinces.idFor("", provinces))
    }
}

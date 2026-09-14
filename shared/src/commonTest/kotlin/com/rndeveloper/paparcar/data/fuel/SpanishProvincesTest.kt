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

    // ── El código postal YA trae el id dentro ────────────────────────────────
    // [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]

    @Test
    fun `should_readTheProvince_straightOutOfThePostalCode`() {
        // 🔑 Los ids del ministerio son los códigos INE, y los códigos INE son por construcción los
        // dos primeros dígitos del código postal. Verificado en vivo contra `/Listados/Provincias/`
        // el 14-09-2026: 52 filas, ids '01'…'52'. Esto no empareja NINGÚN nombre, que es la mitad
        // de su valor: ni acentos, ni artículos, ni comunidades donde debería ir una provincia.
        assertEquals("11", SpanishProvinces.idFromPostalCode("11500")) // El Puerto de Santa María
        assertEquals("28", SpanishProvinces.idFromPostalCode("28013")) // Madrid
        assertEquals("07", SpanishProvinces.idFromPostalCode("07001")) // Illes Balears
        // 🔑 Y las dos que ninguna otra clave alcanza: medido en device, Ceuta y Melilla NO traen
        // `subAdminArea`, así que el camino por nombre se queda sin provincia que buscar.
        assertEquals("51", SpanishProvinces.idFromPostalCode("51001")) // Ceuta
        assertEquals("52", SpanishProvinces.idFromPostalCode("52001")) // Melilla
    }

    @Test
    fun `should_refuseAnythingThatIsNotASpanishPostalCode`() {
        // ⛔ Valida la FORMA antes de creérsela. Un postal extranjero tiene otra pinta y no puede
        // convertirse en una provincia española por accidente: «75001» de París daría «75», que no
        // existe, y «SW1A» ni siquiera es numérico.
        assertNull(SpanishProvinces.idFromPostalCode("75001"), "París: no hay provincia 75")
        assertNull(SpanishProvinces.idFromPostalCode("53001"), "53 está fuera del rango 01..52")
        assertNull(SpanishProvinces.idFromPostalCode("00123"), "00 no es una provincia")
        assertNull(SpanishProvinces.idFromPostalCode("SW1A 1AA"))
        assertNull(SpanishProvinces.idFromPostalCode("2801"), "cuatro dígitos no es un postal español")
        assertNull(SpanishProvinces.idFromPostalCode(null))
        assertNull(SpanishProvinces.idFromPostalCode(""))
    }
}

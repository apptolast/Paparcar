package com.rndeveloper.paparcar.data.fuel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The lookup that turns a geocoder's town into the ministry's ids.
 * [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
 *
 * The rows below are copied VERBATIM from `/Listados/Municipios/`, read live on 10-09-2026 — the
 * article in brackets, the uppercase province, the id field spelled with its 'r'. Paraphrasing the
 * feed in a fixture is how a test ends up proving that our own idea of the feed is self-consistent.
 */
class SpanishMunicipalitiesTest {

    private val body = """
        [{"IDMunicipio":"1789","IDProvincia":"11","IDCCAA":"01","Municipio":"Puerto de Santa María (El)","Provincia":"CÁDIZ","CCAA":"Andalucia"},
         {"IDMunicipio":"5675","IDProvincia":"28","IDCCAA":"13","Municipio":"Madrid","Provincia":"MADRID","CCAA":"Madrid, Comunidad de"},
         {"IDMunicipio":"4276","IDProvincia":"17","IDCCAA":"09","Municipio":"Escala (L')","Provincia":"GIRONA","CCAA":"Cataluña"}]
    """.trimIndent()

    @Test
    fun should_findTheTown_when_theFeedMovesTheArticleToTheEnd() {
        // ⛔ THE assertion of this file. The ministry writes «Puerto de Santa María (El)»; a phone
        // reports «El Puerto de Santa María». Matching them is the whole difference between asking
        // Spain for its prices and telling the user Spain has none.
        val entry = SpanishMunicipalities.find("El Puerto de Santa María", SpanishMunicipalities.parse(body))
        assertEquals("1789", entry?.municipalityId)
        assertEquals("11", entry?.provinceId, "the town's row is where the province comes from too")
    }

    @Test
    fun should_findTheTown_when_itArrivesShoutedAndWithoutAccents() {
        val entry = SpanishMunicipalities.find("PUERTO DE SANTA MARIA", SpanishMunicipalities.parse(body))
        assertEquals("1789", entry?.municipalityId)
    }

    @Test
    fun should_findTheTown_when_theArticleIsCatalanAndApostrophed() {
        // «Escala (L')» against a geocoder's «L'Escala» — same trick, another language.
        val entry = SpanishMunicipalities.find("L'Escala", SpanishMunicipalities.parse(body))
        assertEquals("4276", entry?.municipalityId)
    }

    @Test
    fun should_findATownThatNeverHadAnArticle() {
        assertEquals("5675", SpanishMunicipalities.find("Madrid", SpanishMunicipalities.parse(body))?.municipalityId)
    }

    @Test
    fun should_findNothing_when_theNameIsNotASpanishTown() {
        // Normal everywhere else in the world, and NOT a failure: the caller widens to the province,
        // and only if that fails too does anything hand over to a feed without prices.
        val municipalities = SpanishMunicipalities.parse(body)
        assertNull(SpanishMunicipalities.find("Bordeaux", municipalities))
        assertNull(SpanishMunicipalities.find(null, municipalities))
        assertNull(SpanishMunicipalities.find("  ", municipalities))
    }

    @Test
    fun should_notMatchAnAutonomousCommunity() {
        // The bug this ticket exists for, from the other end: «Andalucía» is not a town either, so
        // nothing about it can be made to resolve — which is why the TOWN had to become the key.
        assertNull(SpanishMunicipalities.find("Andalucía", SpanishMunicipalities.parse(body)))
    }
}

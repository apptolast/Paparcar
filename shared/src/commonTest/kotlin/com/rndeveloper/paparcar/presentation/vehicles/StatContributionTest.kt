package com.rndeveloper.paparcar.presentation.vehicles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [STATS-THE-ONE-GREEN-BELONGS-TO-WHAT-YOU-GAVE-001]
 *
 * ⛔ El ticket (#52) pedía destacar **CO₂ y gasto** como «impacto positivo». Estos testigos existen
 * para que eso no pueda pasar por descuido: el CO₂ de la rejilla es lo que tu conducción EMITE y el
 * coste es lo que PAGAS. Un número no se vuelve bueno por enseñarlo en otro color.
 */
class StatContributionTest {

    @Test
    fun should_markTheSpotsYouGave_as_theContribution() {
        assertTrue(isContribution(StatTileKey.SPOTS_SHARED))
    }

    @Test
    fun should_neverMarkACost_as_aContribution() {
        // ⛔ EL testigo del ticket, y el que hay que mirar antes de ampliar la lista.
        assertFalse(isContribution(StatTileKey.CO2), "el CO2 de la rejilla es lo que EMITES")
        assertFalse(isContribution(StatTileKey.COST), "el coste es lo que PAGAS")
        assertFalse(isContribution(StatTileKey.FUEL))
        assertFalse(isContribution(StatTileKey.ENERGY))
        assertFalse(isContribution(StatTileKey.SEARCH_TIME), "el tiempo buscando es lo que te costó")
    }

    @Test
    fun should_keepTheListToExactlyOne() {
        // La rejilla tiene UN verde. Si algún día son dos, que sea una decisión y no una copia del
        // tile de al lado — este testigo es el que obliga a pararse a pensarlo.
        assertEquals(
            listOf(StatTileKey.SPOTS_SHARED),
            StatTileKey.entries.filter { isContribution(it) },
        )
    }

    @Test
    fun should_notMarkTheDetectionRate_even_thoughItIsAlreadyGreen() {
        // Su anillo verde dice «esto lo ha hecho la app»; la aportación dice «esto lo has hecho tú».
        // Mismo significado —marca y acción— y por eso pueden convivir; pero la superficie es de
        // una sola, o la rejilla deja de tener jerarquía.
        assertFalse(isContribution(StatTileKey.AUTO_DETECTED))
    }
}

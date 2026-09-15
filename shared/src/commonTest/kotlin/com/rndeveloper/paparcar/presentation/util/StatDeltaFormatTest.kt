package com.rndeveloper.paparcar.presentation.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cómo se dice que una cifra se movió. [STATS-A-DELTA-THAT-READS-AS-A-FAULT-001] */
class StatDeltaFormatTest {

    /**
     * ⛔ El caso que abrió el ticket, medido en el Redmi: de 2 a 46 minutos buscando plaza. La cifra
     * «2200 %» es correcta y se lee como una avería de la app; «×23» es como lo diría una persona.
     */
    @Test
    fun should_sayTimes_when_theRiseIsEnormous() {
        val delta = statDelta(current = 46.0, previous = 2.0)!!
        assertEquals("×23", delta.amountText)
        assertTrue(delta.isUp)
    }

    @Test
    fun should_keepThePercentage_forAnOrdinaryMove() {
        assertEquals("30%", statDelta(current = 130.0, previous = 100.0)?.amountText)
        assertEquals("18%", statDelta(current = 82.0, previous = 100.0)?.amountText)
    }

    /** Una bajada nunca pasa de −100 %, así que el porcentaje ya se lee bien. */
    @Test
    fun should_keepThePercentage_forEveryFall() {
        val delta = statDelta(current = 2.0, previous = 46.0)!!
        assertEquals("96%", delta.amountText)
        assertTrue(!delta.isUp)
    }

    /** Media vez importa cuando son dos y no cuando son veintitrés. */
    @Test
    fun should_keepOneDecimal_onlyBelowTen() {
        assertEquals("×3.5", statDelta(current = 35.0, previous = 10.0)?.amountText)
        assertEquals("×12", statDelta(current = 123.0, previous = 10.0)?.amountText)
    }

    @Test
    fun should_refuseToCompare_when_thereIsNothingToCompareWith() {
        assertNull(statDelta(current = 10.0, previous = null))
        // Cualquier cambio desde cero es «infinitamente más», que no es una afirmación.
        assertNull(statDelta(current = 10.0, previous = 0.0))
    }

    @Test
    fun should_staySilent_when_theMoveRoundsToNothing() {
        assertNull(statDelta(current = 100.4, previous = 100.0), "un ▲ 0 % es ruido con ropa de dato")
    }

    /** El umbral es 3×: justo debajo sigue siendo porcentaje, justo encima ya son veces. */
    @Test
    fun should_switchExactlyAtTheThreshold() {
        assertEquals("190%", statDelta(current = 29.0, previous = 10.0)?.amountText)
        assertEquals("×3", statDelta(current = 30.0, previous = 10.0)?.amountText)
    }
}

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.vehicles

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Cómo se LLAMA el final de un periodo. [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]
 *
 * La card «Si sigue este ritmo» proyecta el final del periodo SELECCIONADO y rotulaba esa fila con
 * la constante «cierre de mes»: la semana cinco días de cada siete, y el año de abril a finales de
 * noviembre. La cifra siempre fue correcta; lo que mentía era el texto, porque el final de un
 * periodo se decidía en un `when` y su NOMBRE en otro.
 */
class PeriodCloseTest {

    /**
     * ⛔ **El testigo.** Un periodo que puede terminar tiene que poder nombrar su final, y uno que no
     * termina no puede tener nombre de cierre. Sin esta comprobación los dos `when` vuelven a
     * derivar en cuanto entre un filtro nuevo — que es exactamente lo que pasó cuando llegaron
     * `ThisWeek` y `ThisYear` [STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001].
     */
    @Test
    fun should_nameItsClose_exactlyWhenThePeriodHasOne() {
        val nowMs = LocalDateTime(2026, 9, 16, 12, 0)
            .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        allFilters.forEach { filter ->
            val ends = VehicleHistoryCalculator.windowEndMs(filter, nowMs) != null
            val named = filter.close() != null
            assertEquals(ends, named, "$filter: termina=$ends pero se sabe nombrar=$named")
        }
    }

    @Test
    fun should_mapEachCalendarPeriod_toItsOwnUnit() {
        assertEquals(PeriodClose.WEEK, StatsPeriod.ThisWeek.close())
        assertEquals(PeriodClose.MONTH, StatsPeriod.ThisMonth.close())
        assertEquals(PeriodClose.MONTH, StatsPeriod.LastMonth.close())
        assertEquals(PeriodClose.YEAR, StatsPeriod.ThisYear.close())
        assertEquals(PeriodClose.YEAR, StatsPeriod.Year(2025).close())
    }

    /** Ventanas RODANTES: cubren siempre el mismo tramo y no terminan nunca, así que su final no
     *  tiene nombre porque no tiene fecha. */
    @Test
    fun should_haveNoClose_forTheRollingWindows() {
        assertNull(StatsPeriod.Last7Days.close())
        assertNull(StatsPeriod.Last3Months.close())
        assertNull(StatsPeriod.All.close())
    }

    /** Y el que sí lo tiene, lo tiene de verdad: que el testigo no pueda pasar por vacío. */
    @Test
    fun should_coverBothAnswers_soTheWitnessProvesItLooked() {
        assertNotNull(allFilters.firstOrNull { it.close() != null })
        assertNotNull(allFilters.firstOrNull { it.close() == null })
    }

    /**
     * ⛔ **El trinquete de [allFilters].** Sin reflexión (`sealedSubclasses` es sólo JVM y esta suite
     * corre también en Native), una lista escrita a mano puede quedarse corta en silencio y el
     * testigo de arriba pasaría sin mirar el filtro nuevo. [tag] es un `when` EXHAUSTIVO: un
     * subtipo nuevo rompe la compilación aquí, y entonces esta cuenta obliga a añadirlo a la lista.
     */
    @Test
    fun should_listEveryFilter_soTheWitnessCannotMissOne() {
        assertEquals(EXPECTED_FILTERS, allFilters.map { it.tag() }.toSet().size)
    }

    private fun StatsPeriod.tag(): String = when (this) {
        StatsPeriod.All -> "all"
        StatsPeriod.ThisWeek -> "this-week"
        StatsPeriod.Last7Days -> "last-7-days"
        StatsPeriod.ThisMonth -> "this-month"
        StatsPeriod.LastMonth -> "last-month"
        StatsPeriod.Last3Months -> "last-3-months"
        StatsPeriod.ThisYear -> "this-year"
        is StatsPeriod.Year -> "year"
    }

    private val allFilters = listOf(
        StatsPeriod.All,
        StatsPeriod.ThisWeek,
        StatsPeriod.Last7Days,
        StatsPeriod.ThisMonth,
        StatsPeriod.LastMonth,
        StatsPeriod.Last3Months,
        StatsPeriod.ThisYear,
        StatsPeriod.Year(2025),
    )

    private companion object {
        const val EXPECTED_FILTERS = 8
    }
}

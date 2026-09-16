@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.stats

import com.rndeveloper.paparcar.domain.entitlement.historyVisibleFromMs
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** El mes que se acaba de cerrar. [STATS-PLUS-REMEMBERS-AND-REPORTS-001] */
class ClosedMonthTest {

    private val utc = TimeZone.UTC

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12) =
        LocalDateTime(year, month, day, hour, 0).toInstant(utc).toEpochMilliseconds()

    @Test
    fun should_closeThePreviousMonth_whole() {
        val window = closedMonthWindow(at(2026, 10, 3), utc)
        assertEquals(9, window.monthNumber)
        assertEquals(2026, window.year)
        assertTrue(at(2026, 9, 1, hour = 0) in window, "el 1 de septiembre entra")
        assertTrue(at(2026, 9, 30, hour = 23) in window, "y el 30 también")
        assertFalse(at(2026, 10, 1, hour = 0) in window, "el 1 de octubre ya no")
        assertFalse(at(2026, 8, 31, hour = 23) in window, "ni el 31 de agosto")
    }

    /** ⛔ En enero el mes cerrado es DICIEMBRE DEL AÑO ANTERIOR — la razón de hacer la cuenta sobre
     *  el calendario y no restando milisegundos. */
    @Test
    fun should_crossTheYear_inJanuary() {
        val window = closedMonthWindow(at(2027, 1, 4), utc)
        assertEquals(12, window.monthNumber)
        assertEquals(2026, window.year)
        assertTrue(at(2026, 12, 25) in window)
    }

    @Test
    fun should_compareAgainstTheMonthBeforeTheClosedOne() {
        val previous = previousToClosedMonthWindow(at(2026, 10, 3), utc)
        assertEquals(8, previous.monthNumber)
        assertEquals(2026, previous.year)
        // Los dos tramos se tocan sin solaparse: el fin de agosto ES el principio de septiembre.
        assertEquals(closedMonthWindow(at(2026, 10, 3), utc).startMs, previous.endMs)
    }

    @Test
    fun should_crossTheYear_forTheComparisonToo() {
        val previous = previousToClosedMonthWindow(at(2027, 1, 4), utc)
        assertEquals(11, previous.monthNumber)
        assertEquals(2026, previous.year)
    }

    /**
     * ⛔ El resumen deja de ser noticia. Un mes cerrado leído el día 27 no es un resumen, es un
     * recuerdo — y estaría encima de todo lo que sí está pasando, todos los días del año.
     */
    @Test
    fun should_stopBeingNews_afterTheFirstWeek() {
        assertTrue(isRecapFresh(at(2026, 10, 1), utc))
        assertTrue(isRecapFresh(at(2026, 10, 7), utc))
        assertFalse(isRecapFresh(at(2026, 10, 8), utc))
        assertFalse(isRecapFresh(at(2026, 10, 27), utc))
    }

    /**
     * ⛔ **El gate de Plus que faltaba.** Durante 48 horas de código este resumen no tenía ninguno:
     * dos KDoc afirmaban que una cuenta free devolvía null «por construcción», porque su ventana son
     * 7 días y un mes cerrado no cabría en ella. Es falso justo los días en que la card existe —del
     * 1 al 7—, porque una ventana RODANTE de 7 días el 1 de octubre llega hasta el 24 de septiembre.
     * Un free habría leído cuatro días de septiembre bajo el título «El mes pasado».
     * [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]
     */
    @Test
    fun should_refuseTheRecap_whenTheWindowOnlyCoversPartOfTheClosedMonth() {
        // El día 1: la ventana free alcanza el 24 de septiembre, dentro del mes cerrado.
        val day1 = at(2026, 10, 1)
        val freeOnDay1 = historyVisibleFromMs(isPlus = false, nowMs = day1)
        assertTrue(freeOnDay1!! > closedMonthWindow(day1, utc).startMs, "la ventana empieza DENTRO")
        assertFalse(closedMonthIsFullyVisible(freeOnDay1, day1, utc))

        // Y el día 7 sigue sin cubrirlo: no hay un solo día del tramo en que un free lo vea entero.
        val day7 = at(2026, 10, 7)
        assertFalse(closedMonthIsFullyVisible(historyVisibleFromMs(false, day7), day7, utc))
    }

    @Test
    fun should_allowTheRecap_whenNothingIsHidden() {
        val day1 = at(2026, 10, 1)
        assertTrue(closedMonthIsFullyVisible(historyVisibleFromMs(isPlus = true, nowMs = day1), day1, utc))
        assertTrue(closedMonthIsFullyVisible(null, at(2026, 10, 7), utc))
    }

    /**
     * Está escrito como COBERTURA y no como `if (isPlus)`: el día que la ventana free deje de ser
     * de 7 días, el gate se mueve solo. Una ventana que alcanza el principio del mes cerrado deja
     * pasar el resumen aunque quien la tenga no sea Plus.
     */
    @Test
    fun should_followTheWindow_notThePlan() {
        val day3 = at(2026, 10, 3)
        val startOfClosed = closedMonthWindow(day3, utc).startMs
        assertTrue(closedMonthIsFullyVisible(startOfClosed, day3, utc), "justo en el borde, entra")
        assertFalse(closedMonthIsFullyVisible(startOfClosed + 1, day3, utc), "un ms más tarde, no")
    }

    /** Ningún día del tramo en que la card existe deja pasar a un free — no sólo el 1 y el 7. */
    @Test
    fun should_refuseEveryDayTheCardCanExist_forFree() {
        (1..RECAP_FRESH_DAYS).forEach { day ->
            val nowMs = at(2026, 10, day)
            assertFalse(
                closedMonthIsFullyVisible(historyVisibleFromMs(isPlus = false, nowMs = nowMs), nowMs, utc),
                "día $day: un free no puede ver septiembre entero",
            )
        }
    }

    /**
     * La ventana se decide en la zona del USUARIO: a las 00:30 del día 1, UTC sigue en el mes
     * viejo y el resumen hablaría del mes equivocado.
     */
    @Test
    fun should_answerInTheUsersZone_notInUtc() {
        // 2026-10-01 00:30 en Madrid (UTC+2) es 2026-09-30 22:30 UTC.
        val madridFirst = LocalDateTime(2026, 9, 30, 22, 30).toInstant(utc).toEpochMilliseconds()
        val madrid = TimeZone.of("Europe/Madrid")
        assertEquals(9, closedMonthWindow(madridFirst, madrid).monthNumber, "en Madrid ya es octubre")
        assertEquals(8, closedMonthWindow(madridFirst, utc).monthNumber, "en UTC todavía no")
    }
}

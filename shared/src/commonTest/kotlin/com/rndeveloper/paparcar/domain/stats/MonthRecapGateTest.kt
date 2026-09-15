@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.stats

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

/** ¿Toca contar el mes? [STATS-PLUS-THE-RECAP-KNOCKS-001] */
class MonthRecapGateTest {

    private val utc = TimeZone.UTC

    private fun at(year: Int, month: Int, day: Int) =
        LocalDateTime(year, month, day, 12, 0).toInstant(utc).toEpochMilliseconds()

    @Test
    fun should_tellTheClosedMonth_when_thereIsSomethingToSay() {
        assertEquals(
            MonthRecapGate.Tell(202609),
            monthRecapGate(at(2026, 10, 3), lastAnnouncedYearMonth = null, parkingsInClosedMonth = 12, timeZone = utc),
        )
    }

    /** ⛔ Siete despertares posibles dentro de la ventana y UNA sola llamada a la puerta. */
    @Test
    fun should_holdOnTheSecondTick_ofTheSameMonth() {
        assertEquals(
            MonthRecapGate.Hold("said"),
            monthRecapGate(at(2026, 10, 5), lastAnnouncedYearMonth = 202609, parkingsInClosedMonth = 12, timeZone = utc),
        )
    }

    /** Un mes nuevo vuelve a contarse: lo apuntado es un `yyyymm`, no un booleano. */
    @Test
    fun should_tellAgain_when_anotherMonthCloses() {
        assertEquals(
            MonthRecapGate.Tell(202610),
            monthRecapGate(at(2026, 11, 2), lastAnnouncedYearMonth = 202609, parkingsInClosedMonth = 8, timeZone = utc),
        )
    }

    @Test
    fun should_hold_outsideTheWindow() {
        assertEquals(
            MonthRecapGate.Hold("stale"),
            monthRecapGate(at(2026, 10, 12), lastAnnouncedYearMonth = null, parkingsInClosedMonth = 12, timeZone = utc),
        )
    }

    @Test
    fun should_hold_when_theClosedMonthHasNothing() {
        assertEquals(
            MonthRecapGate.Hold("nothing"),
            monthRecapGate(at(2026, 10, 3), lastAnnouncedYearMonth = null, parkingsInClosedMonth = 0, timeZone = utc),
        )
    }

    /** En enero se cuenta DICIEMBRE, y se apunta con su propio año. */
    @Test
    fun should_crossTheYear() {
        assertEquals(
            MonthRecapGate.Tell(202612),
            monthRecapGate(at(2027, 1, 4), lastAnnouncedYearMonth = 202611, parkingsInClosedMonth = 5, timeZone = utc),
        )
    }
}

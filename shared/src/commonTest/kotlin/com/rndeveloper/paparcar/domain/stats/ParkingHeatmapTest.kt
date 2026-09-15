@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.stats

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * When you park, and when it costs you. [STATS-THE-EXPENSIVE-HOUR-IS-MEASURED-001]
 *
 * Every fixture is written as a LOCAL date-time, because that is what the grid claims to show: a
 * parking has to land on the day and in the band the user lived, not the one UTC happened to be in.
 */
class ParkingHeatmapTest {

    private val utc = TimeZone.UTC

    /** 2026-09-15 is a Tuesday — isoDayNumber 2, so row index 1. */
    private fun at(
        year: Int = 2026,
        month: Int = 9,
        day: Int = 15,
        hour: Int,
        searchTimeMs: Long? = null,
    ) = UserParking(
        id = "$day-$hour-$searchTimeMs",
        location = GpsPoint(
            40.4, -3.7, 8f,
            LocalDateTime(year, month, day, hour, 0).toInstant(utc).toEpochMilliseconds(),
            0f,
        ),
        isActive = false,
        searchTimeMs = searchTimeMs,
    )

    @Test
    fun should_landEachParking_onItsOwnDayAndBand() {
        val grid = buildParkingHeatmap(
            listOf(
                at(hour = 18),          // Tuesday, band 4 (16-20)
                at(day = 19, hour = 20), // Saturday, band 5 (20-24)
            ),
            HeatMeasure.PARKINGS,
            utc,
        )
        assertEquals(1.0, grid.cell(day = 1, band = 4)?.value)
        assertEquals(1.0, grid.cell(day = 5, band = 5)?.value)
        // ⛔ THE reason this grid exists: "Tuesday" and "20 h" are two separate answers, and taken
        // together they describe a slot that never happened.
        assertNull(grid.cell(day = 1, band = 5), "no hubo ningún martes a las 20 h")
    }

    @Test
    fun should_countEveryParking_when_theMeasureIsParkings() {
        val grid = buildParkingHeatmap(
            listOf(at(hour = 9), at(day = 22, hour = 10), at(day = 29, hour = 11)),
            HeatMeasure.PARKINGS,
            utc,
        )
        // Three Tuesdays, all in the 08-12 band.
        assertEquals(3.0, grid.cell(day = 1, band = 2)?.value)
        assertEquals(3, grid.cell(day = 1, band = 2)?.samples)
        assertEquals(3.0, grid.peak)
    }

    /** A count of one is a fact: you parked there. Nothing to threshold. */
    @Test
    fun should_keepACellOfOne_when_theMeasureIsParkings() {
        val grid = buildParkingHeatmap(listOf(at(hour = 7)), HeatMeasure.PARKINGS, utc)
        assertEquals(1.0, grid.cell(day = 1, band = 1)?.value)
        assertTrue(!grid.isEmpty)
    }

    /**
     * ⛔ The MEAN, and this is the whole point of the second measure: a sum per cell would grow
     * with how often you park there and simply redraw the parkings grid.
     */
    @Test
    fun should_averageTheSearchTime_notSumIt() {
        val grid = buildParkingHeatmap(
            listOf(
                at(hour = 9, searchTimeMs = 600_000L),
                at(day = 22, hour = 9, searchTimeMs = 120_000L),
            ),
            HeatMeasure.SEARCH_TIME,
            utc,
        )
        assertEquals(360_000.0, grid.cell(day = 1, band = 2)?.value, "media, no suma")
        assertEquals(2, grid.cell(day = 1, band = 2)?.samples)
    }

    @Test
    fun should_claimNoCell_when_onlyOneHuntWasMeasured() {
        val grid = buildParkingHeatmap(
            listOf(at(hour = 9, searchTimeMs = 600_000L), at(day = 22, hour = 9)),
            HeatMeasure.SEARCH_TIME,
            utc,
        )
        assertNull(grid.cell(day = 1, band = 2), "una media de una muestra no es una media")
        assertTrue(grid.isEmpty)
        assertNull(grid.peak)
    }

    /** An unmeasured parking contributes nothing to the hunting grid — absent, never a zero. */
    @Test
    fun should_ignoreUnmeasuredParkings_inTheSearchGrid() {
        val grid = buildParkingHeatmap(
            listOf(
                at(hour = 9, searchTimeMs = 300_000L),
                at(day = 22, hour = 9, searchTimeMs = 300_000L),
                at(day = 29, hour = 9),
            ),
            HeatMeasure.SEARCH_TIME,
            utc,
        )
        assertEquals(300_000.0, grid.cell(day = 1, band = 2)?.value)
        assertEquals(2, grid.cell(day = 1, band = 2)?.samples, "sólo cuentan las medidas")
    }

    @Test
    fun should_beEmpty_when_thereIsNothingToShow() {
        val grid = buildParkingHeatmap(emptyList(), HeatMeasure.PARKINGS, utc)
        assertEquals(ParkingHeatmap.DAYS * ParkingHeatmap.BANDS, grid.cells.size)
        assertTrue(grid.isEmpty)
        assertNull(grid.peak)
    }

    /** Midnight and 23:59 are the two ends of the grid, not one band bleeding into the other. */
    @Test
    fun should_putTheEdgesOfTheDay_inTheFirstAndLastBand() {
        val grid = buildParkingHeatmap(
            listOf(at(hour = 0), at(hour = 23)),
            HeatMeasure.PARKINGS,
            utc,
        )
        assertEquals(1.0, grid.cell(day = 1, band = 0)?.value)
        assertEquals(1.0, grid.cell(day = 1, band = ParkingHeatmap.BANDS - 1)?.value)
    }
}

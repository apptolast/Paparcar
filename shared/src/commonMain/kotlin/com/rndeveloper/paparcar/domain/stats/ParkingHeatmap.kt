@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.stats

import com.rndeveloper.paparcar.domain.model.UserParking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * When you park, and when it costs you. [STATS-THE-EXPENSIVE-HOUR-IS-MEASURED-001]
 *
 * A grid of [DAYS] days × [BANDS] four-hour bands. Everything the stats knew about time until now
 * was ONE dimension at a time — the period's peak hour and its usual day — and two separate
 * answers cannot describe a week: a Tuesday at 09:00 and a Saturday at 20:00 produce "Tuesday" and
 * "20 h" without there ever having been a Tuesday at 20:00.
 *
 * ⛔ Six bands rather than twenty-four hours: 168 cells leave almost none above its threshold, and
 * a grid that never fills is a grid that says nothing. Four hours is coarse enough to have mass and
 * fine enough to separate a morning commute from an evening one.
 */
data class ParkingHeatmap(
    /** Row-major, [DAYS] × [BANDS]. A null cell has NO data — never a zero. */
    val cells: List<HeatCell?>,
    val measure: HeatMeasure,
) {
    fun cell(day: Int, band: Int): HeatCell? = cells.getOrNull(day * BANDS + band)

    /** The busiest/costliest cell, which is what the colours are scaled against. Null when empty. */
    val peak: Double? get() = cells.filterNotNull().maxOfOrNull { it.value }

    val isEmpty: Boolean get() = cells.all { it == null }

    companion object {
        /** Monday…Sunday, the week the rest of the app already speaks. */
        const val DAYS = 7
        /** 00–04, 04–08, 08–12, 12–16, 16–20, 20–24. */
        const val BANDS = 6
        const val BAND_HOURS = 24 / BANDS
    }
}

/** One cell: what it is worth, and how many parkings back that up. */
data class HeatCell(val value: Double, val samples: Int)

/**
 * What a cell of the grid measures — and each one brings its own STATISTIC and its own threshold,
 * which is the whole reason this is not one more series. [STATS-THE-EXPENSIVE-HOUR-IS-MEASURED-001]
 */
enum class HeatMeasure {
    /**
     * How many times you parked in that slot. A count of one IS a fact — you did park there — so
     * there is nothing to threshold.
     */
    PARKINGS,

    /**
     * How long you spent hunting a spot there, on AVERAGE.
     *
     * ⛔ The mean, not the sum. A sum per cell would grow with how often you park there, so it
     * would redraw [PARKINGS] with different numbers on it — and the question the user asked ("the
     * days it takes me longest to park") is about the typical hunt, not the total. And a mean over
     * one measurement is not a mean: a cell needs [MIN_SAMPLES_FOR_MEAN] before it exists at all.
     */
    SEARCH_TIME,
}

/**
 * The grid for [sessions], in the device's own time zone — the same one every other time-shaped
 * figure uses, so a parking cannot sit on Tuesday here and Wednesday in the tile above.
 */
fun buildParkingHeatmap(
    sessions: List<UserParking>,
    measure: HeatMeasure,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ParkingHeatmap {
    val buckets = List(ParkingHeatmap.DAYS * ParkingHeatmap.BANDS) { mutableListOf<UserParking>() }
    sessions.forEach { session ->
        val local = Instant.fromEpochMilliseconds(session.location.timestamp).toLocalDateTime(timeZone)
        val day = local.date.dayOfWeek.isoDayNumber - 1
        val band = (local.hour / ParkingHeatmap.BAND_HOURS).coerceIn(0, ParkingHeatmap.BANDS - 1)
        buckets[day * ParkingHeatmap.BANDS + band].add(session)
    }
    return ParkingHeatmap(
        cells = buckets.map { it.toCell(measure) },
        measure = measure,
    )
}

private fun List<UserParking>.toCell(measure: HeatMeasure): HeatCell? = when (measure) {
    HeatMeasure.PARKINGS -> takeIf { it.isNotEmpty() }?.let { HeatCell(size.toDouble(), size) }
    HeatMeasure.SEARCH_TIME -> {
        val measured = mapNotNull { it.searchTimeMs }
        measured
            .takeIf { it.size >= MIN_SAMPLES_FOR_MEAN }
            ?.let { HeatCell(it.sum().toDouble() / it.size, it.size) }
    }
}

/** Two measurements are the fewest that can average to anything but themselves. */
const val MIN_SAMPLES_FOR_MEAN = 2

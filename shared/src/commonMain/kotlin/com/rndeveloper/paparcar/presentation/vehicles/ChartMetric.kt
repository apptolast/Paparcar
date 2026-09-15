package com.rndeveloper.paparcar.presentation.vehicles

import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.stats.sumMeasuredDistance
import com.rndeveloper.paparcar.domain.stats.sumMeasuredSearchTime

/**
 * What the activity chart's bars MEASURE. [STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001]
 *
 * ⛔ **Three, and not one per tile.** Looking at ONE car, litres = km × consumption/100, CO₂ =
 * litres × factor and spend = litres × price — every one of them a constant multiple of the
 * kilometres, so their bars would have exactly the same SHAPE and only the number printed on top
 * would differ. A chip that does not change the drawing is not a view, it is decoration; the same
 * trap [STATS-A-COMPUTED-LITRE-WITH-NO-READER-001] documented when it refused to show "average
 * consumption" (a constant wearing a measurement's clothes). Spend loses nothing: its figure is in
 * its tile, with its price and its origin.
 *
 * These three are the only series a period can move independently.
 */
enum class ChartMetric {
    /** How many times you parked. The chart as it has always been, and the fallback. */
    PARKINGS,

    /** Measured driving distance, in METERS (the reducer's unit; the UI formats it). */
    DISTANCE,

    /** Time lost hunting a spot, in MILLISECONDS. [TRIP-THE-SEARCH-FOR-A-SPOT-IS-MEASURED-001] */
    SEARCH_TIME,
    ;

    /**
     * What one bucket of [sessions] is worth on this metric.
     *
     * The two measured ones go through the F0 readers — never a hand-written `mapNotNull{}.sum()`,
     * which is how the search time ended up as a bare total in the first place.
     * [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001]
     *
     * A bucket with nothing measured is worth 0 HERE and only here: a bar can draw a zero but has
     * nowhere to say "unmeasured", so the honesty is paid one level up — the card's title carries
     * the period's coverage, exactly as it already did for kilometres.
     */
    fun valueOf(sessions: List<UserParking>): Float = when (this) {
        PARKINGS -> sessions.size.toFloat()
        DISTANCE -> sumMeasuredDistance(sessions)?.value ?: 0f
        SEARCH_TIME -> sumMeasuredSearchTime(sessions)?.value?.toFloat() ?: 0f
    }

    /**
     * The smallest "tallest bar" this metric will scale against, so one lonely reading does not
     * render as a full-height column.
     *
     * ⛔ It belongs to the METRIC, not to the canvas. It used to be `MIN_SCALE_MAX = 3` sitting in
     * the drawing code, which is a floor of three PARKINGS — a number with no meaning at all once
     * the same bar can hold metres or milliseconds. A floor travels with its unit, like every other
     * magnitude in this app.
     */
    val scaleFloor: Float
        get() = when (this) {
            PARKINGS -> MIN_PARKINGS_SCALE
            DISTANCE -> MIN_DISTANCE_SCALE_METERS
            SEARCH_TIME -> MIN_SEARCH_SCALE_MS
        }
}

/**
 * The metrics this period can actually draw, in reading order — a metric with no data is not
 * offered, the same rule the figures grid lives by (a tile exists only when its figure does).
 *
 * PARKINGS is always there: it is the chart that already existed, and a period being shown at all
 * means there is something to count.
 */
fun availableChartMetrics(sessions: List<UserParking>): List<ChartMetric> = buildList {
    add(ChartMetric.PARKINGS)
    if (sessions.any { it.routeDistanceMeters != null }) add(ChartMetric.DISTANCE)
    if (sessions.any { it.searchTimeMs != null }) add(ChartMetric.SEARCH_TIME)
}

/**
 * The metric to draw when [selected] no longer exists in this period — changing the filter can take
 * the data away from under a choice, and an empty chart with a chip still lit reads as a bug.
 * Falling back to PARKINGS keeps the card answering something true.
 */
fun resolveChartMetric(selected: ChartMetric, sessions: List<UserParking>): ChartMetric =
    if (selected in availableChartMetrics(sessions)) selected else ChartMetric.PARKINGS

/** Three parkings — the floor the chart has used since the bars were only ever counts. */
private const val MIN_PARKINGS_SCALE = 3f
/** Ten kilometres: below that, one commute would fill the chart on its own. */
private const val MIN_DISTANCE_SCALE_METERS = 10_000f
/** Ten minutes of hunting — the scale a period of easy parking is compared against. */
private const val MIN_SEARCH_SCALE_MS = 600_000f

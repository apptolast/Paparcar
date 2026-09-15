package com.rndeveloper.paparcar.domain.stats.insights

import com.rndeveloper.paparcar.domain.stats.ParkingCluster
import com.rndeveloper.paparcar.domain.stats.ScopedMeasure

/**
 * The two sentences that come from somewhere other than the clock: the place that charges you the
 * most, and how the hunt moved against the period before.
 * [STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001]
 */

/**
 * The place where finding a spot costs clearly more than at your other places — or null.
 *
 * ⛔ Compared against THE OTHER PLACES, not against itself: "you take 9 minutes at work" is a
 * figure the places card already shows. The finding is the CONTRAST, and a contrast needs at least
 * two measured places to exist. A place with no name never speaks: a sentence that cannot say where
 * helps nobody [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001].
 */
fun costliestPlace(places: List<ParkingCluster>): StatsInsight? {
    val measured = places.filter { it.avgSearchTimeMs != null }
    if (measured.size < MIN_PLACES_TO_COMPARE) return null
    val worst = measured.maxByOrNull { it.avgSearchTimeMs!! } ?: return null
    if (worst.name.isNullOrBlank()) return null
    val rest = measured.filter { it.id != worst.id }
    val restSamples = rest.sumOf { it.searchTime?.fromParkings ?: 0 }
    if (restSamples <= 0) return null
    // Weighted by visits, for the same reason the grid's baseline is: a place you used twice must
    // not define "the rest" as loudly as one you used twelve times.
    val restMean = rest.sumOf { (it.avgSearchTimeMs ?: 0L) * (it.searchTime?.fromParkings ?: 0) }
        .toDouble() / restSamples
    val worstMean = worst.avgSearchTimeMs!!.toDouble()
    if (restMean <= 0.0 || worstMean < restMean * PLACE_RATIO) return null
    return StatsInsight(
        kind = InsightKind.COSTLIEST_PLACE,
        values = InsightValues(
            placeName = worst.name,
            valueMs = worst.avgSearchTimeMs,
            baselineMs = restMean.toLong(),
        ),
        samples = worst.searchTime?.fromParkings ?: 0,
        effect = worstMean / restMean,
    )
}

/**
 * How the time lost hunting moved against the equivalent period before — or null when the two
 * cannot honestly be compared.
 *
 * ⛔ It inherits the comparability rule instead of writing a second one
 * [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001]: between a period that measured 7 of 12 trips and one
 * that measured 3 of 10, a change says how many trips got measured, not how the hunting moved. The
 * tiles already refuse that comparison; an insight that made it anyway would be the same lie with
 * more words.
 *
 * And it compares the cost PER TRIP, not the totals: a month with twice the trips spends twice the
 * time without anything having got worse.
 */
fun searchTimeTrend(
    current: ScopedMeasure<Long>?,
    previous: ScopedMeasure<Long>?,
    comparable: Boolean,
): StatsInsight? {
    if (!comparable || current == null || previous == null) return null
    if (current.fromParkings <= 0 || previous.fromParkings <= 0) return null
    val now = current.value.toDouble() / current.fromParkings
    val before = previous.value.toDouble() / previous.fromParkings
    if (before <= 0.0) return null
    val ratio = now / before
    val change = kotlin.math.abs(ratio - 1.0)
    if (change < MIN_TREND_CHANGE) return null
    return StatsInsight(
        kind = InsightKind.SEARCH_TIME_TREND,
        values = InsightValues(valueMs = now.toLong(), baselineMs = before.toLong()),
        samples = current.fromParkings,
        // Growing and shrinking are equally worth saying, so the effect is the size of the move
        // either way — not the ratio, which would rank "half as long" far below "twice as long".
        effect = 1.0 + change,
    )
}

/** A contrast needs something to contrast with. */
private const val MIN_PLACES_TO_COMPARE = 2
/** Half again as much as your other places. */
private const val PLACE_RATIO = 1.5
/** A fifth either way. Below that the sentence is noise with a percentage on it. */
private const val MIN_TREND_CHANGE = 0.2

package com.rndeveloper.paparcar.domain.stats.insights

import com.rndeveloper.paparcar.domain.stats.HeatCell
import com.rndeveloper.paparcar.domain.stats.ParkingHeatmap

/**
 * The two sentences the day × band grid can support on its own: when parking costs you the most,
 * and when it costs you the least. [STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001]
 *
 * Predicates, not use cases — they only feed the one verdict
 * ([DeriveStatsInsightsUseCase]), which is the rule for where logic like this lives
 * [DET-VERDICT-NOT-PREDICATE-001].
 */

/**
 * The slot that costs the most, or null when the grid holds no OUTLIER.
 *
 * ⛔ "The worst cell" is not a finding — every grid has a maximum, including one where every slot
 * costs the same. It only becomes a finding when it stands [WORST_RATIO] above the period's own
 * average, which is what makes the sentence worth interrupting someone with.
 */
fun worstTimeToPark(grid: ParkingHeatmap): StatsInsight? {
    val mean = grid.weightedMean() ?: return null
    val (index, cell) = grid.cells.withIndex()
        .mapNotNull { (i, c) -> c?.let { i to it } }
        .maxByOrNull { it.second.value }
        ?: return null
    if (cell.value < mean * WORST_RATIO) return null
    return StatsInsight(
        kind = InsightKind.WORST_TIME_TO_PARK,
        values = grid.valuesAt(index, cell, baseline = mean),
        samples = cell.samples,
        effect = cell.value / mean,
    )
}

/**
 * The slot that costs the least — and never the same one as [worstTimeToPark], which is why the
 * worst is passed in rather than recomputed. A grid with a single measured cell would otherwise
 * produce "you park worst AND best on Tuesday at six", which is true and useless.
 */
fun easiestTimeToPark(grid: ParkingHeatmap, worst: StatsInsight?): StatsInsight? {
    val mean = grid.weightedMean() ?: return null
    val (index, cell) = grid.cells.withIndex()
        .mapNotNull { (i, c) -> c?.let { i to it } }
        .minByOrNull { it.second.value }
        ?: return null
    if (cell.value > mean * EASIEST_RATIO) return null
    val values = grid.valuesAt(index, cell, baseline = mean)
    if (worst != null &&
        worst.values.dayIndex == values.dayIndex &&
        worst.values.bandStartHour == values.bandStartHour
    ) {
        return null
    }
    return StatsInsight(
        kind = InsightKind.EASIEST_TIME_TO_PARK,
        values = values,
        samples = cell.samples,
        // An easy slot's effect is how much it SAVES, so the ratio is inverted: a cell at a third
        // of the average is as big a finding as one at three times it.
        effect = if (cell.value > 0) mean / cell.value else mean,
    )
}

/**
 * The period's typical cost, weighted by how many parkings each cell holds — NOT the mean of the
 * cell means. Averaging averages lets a slot you used twice weigh the same as one you used twelve
 * times, and the comparison an insight makes has to be against what usually happens.
 */
private fun ParkingHeatmap.weightedMean(): Double? {
    val cells = cells.filterNotNull()
    val samples = cells.sumOf { it.samples }
    if (samples <= 0) return null
    return cells.sumOf { it.value * it.samples } / samples
}

private fun ParkingHeatmap.valuesAt(index: Int, cell: HeatCell, baseline: Double) = InsightValues(
    dayIndex = index / ParkingHeatmap.BANDS,
    bandStartHour = (index % ParkingHeatmap.BANDS) * ParkingHeatmap.BAND_HOURS,
    valueMs = cell.value.toLong(),
    baselineMs = baseline.toLong(),
)

/** Half again as much as usual — below that, a slot is just a slot. */
private const val WORST_RATIO = 1.5
/** And a slot has to be clearly cheaper than usual before it is worth recommending. */
private const val EASIEST_RATIO = 0.6

package com.rndeveloper.paparcar.domain.usecase.stats

import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.stats.HeatMeasure
import com.rndeveloper.paparcar.domain.stats.PeriodStats
import com.rndeveloper.paparcar.domain.stats.buildParkingHeatmap
import com.rndeveloper.paparcar.domain.stats.coverageAllowsComparison
import com.rndeveloper.paparcar.domain.stats.insights.MAX_INSIGHTS
import com.rndeveloper.paparcar.domain.stats.insights.StatsInsight
import com.rndeveloper.paparcar.domain.stats.insights.costliestPlace
import com.rndeveloper.paparcar.domain.stats.insights.easiestTimeToPark
import com.rndeveloper.paparcar.domain.stats.insights.placeTrend
import com.rndeveloper.paparcar.domain.stats.insights.searchTimeTrend
import com.rndeveloper.paparcar.domain.stats.insights.worstTimeToPark

/**
 * THE verdict of the insight engine: *which sentences this period supports, in the order worth
 * reading*. [STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001]
 *
 * One use case and four predicates, not four use cases: the rules only feed this answer, and this
 * answer is the one a person sees [DET-VERDICT-NOT-PREDICATE-001]. Adding a fifth rule is a
 * function plus a line here.
 *
 * Pure and clockless, like every other stats computation: the period arrives already sliced by
 * `VehicleHistoryCalculator` and already gated by the entitlement, so the engine cannot conclude
 * anything about data the user may not see.
 */
class DeriveStatsInsightsUseCase {

    operator fun invoke(
        /** The period's sessions — the same list the figures came from. */
        sessions: List<UserParking>,
        stats: PeriodStats?,
    ): List<StatsInsight> {
        if (stats == null) return emptyList()
        val grid = buildParkingHeatmap(sessions, HeatMeasure.SEARCH_TIME)
        val worst = worstTimeToPark(grid)
        return listOfNotNull(
            worst,
            easiestTimeToPark(grid, worst),
            costliestPlace(stats.places),
            // The watch: a place compared with ITSELF a period ago, which is the only sentence here
            // that can notice roadworks. [STATS-A-PLACE-CAN-GET-WORSE-001]
            placeTrend(stats.places, stats.previous?.places.orEmpty()),
            searchTimeTrend(
                current = stats.searchTime,
                previous = stats.previous?.searchTime,
                // ⛔ The rule is APPLIED here, not received: a caller that could pass `true` is a
                // caller that can make the engine dishonest. [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001]
                comparable = coverageAllowsComparison(stats.searchTime, stats.previous?.searchTime),
            ),
        )
            // Biggest finding first, evidence breaking the ties — see `StatsInsight.score`.
            .sortedWith(compareByDescending<StatsInsight> { it.score }.thenByDescending { it.samples })
            .take(MAX_INSIGHTS)
    }
}

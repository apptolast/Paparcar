package com.rndeveloper.paparcar.presentation.stats

import androidx.compose.runtime.Immutable
import com.rndeveloper.paparcar.domain.detection.DetectionPause
import com.rndeveloper.paparcar.domain.fuel.FuelPrice
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.VehicleWithStats
import com.rndeveloper.paparcar.domain.stats.HeatMeasure
import com.rndeveloper.paparcar.domain.stats.PeriodStats
import com.rndeveloper.paparcar.domain.stats.insights.StatsInsight
import com.rndeveloper.paparcar.presentation.vehicles.ChartMetric
import com.rndeveloper.paparcar.presentation.vehicles.StatsPeriod

/**
 * The stats panel — the whole picture for one car and one period. [STATS-PANEL-001]
 *
 * It answers to the same two authorities as the ficha it is opened from: the period comes from
 * `VehicleHistoryCalculator.windowStartMs` (this screen decides no dates of its own) and the
 * figures come from `ComputePeriodStatsUseCase` over what the entitlement lets the user SEE.
 */
@Immutable
data class StatsState(
    /** Null while the vehicles and their sessions are still arriving — never "no hay coches". */
    val vehicles: List<VehicleWithStats>? = null,
    val selectedVehicleIndex: Int = 0,
    /** ⛔ The SHARED opening period, not this screen's own. It was `ThisMonth` here and `All` in the
     *  ficha, so the same car opened on a different window depending on the door.
     *  [STATS-FIRST-CHIP-ANSWERS-NOW-001] */
    val filter: StatsPeriod = StatsPeriod.Default,
    /** What the chart's bars measure. [STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001] */
    val chartMetric: ChartMetric = ChartMetric.PARKINGS,
    /** What the day x band grid measures. A SECOND choice and not the chart's, because the two
     *  answer different questions: the chart is "how much, over the period", the grid is "when".
     *  [STATS-THE-EXPENSIVE-HOUR-IS-MEASURED-001] */
    val heatMeasure: HeatMeasure = HeatMeasure.PARKINGS,
    /**
     * The selected car's figures for the selected period, with its previous period attached.
     * Null once resolved means the period holds no session — an empty state, not a zero.
     */
    val periodStats: PeriodStats? = null,
    /**
     * The period [periodStats] actually covers — [filter] for Plus, the free window for everyone
     * else. See `HistoryTimeline.Resolved.statsPeriod`: anything doing ARITHMETIC with the period
     * has to read this and not [filter], or it measures a window nobody computed.
     * [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]
     */
    val statsPeriod: StatsPeriod = StatsPeriod.Default,
    /** The period's sessions, for the chart's buckets — the same list the figures came from. */
    val filteredSessions: List<UserParking> = emptyList(),
    /**
     * The periods this car's history can answer, derived from its sessions.
     * [STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001]
     *
     * ⛔ Derived from the FULL list, never the gated one. A free account with a year of driving must
     * still SEE "this year" on the row and get the paywall when it taps it — hiding the chip would
     * be a hard gate on a button, which is exactly what [MONEY-001] forbids.
     */
    val periodFilters: List<StatsPeriod> = listOf(
        StatsPeriod.Last7Days,
        StatsPeriod.ThisWeek,
        StatsPeriod.ThisMonth,
        StatsPeriod.All,
    ),
    /** Where the spend figure's price came from — the panel owes the same caveat the ficha gives,
     *  or the same number carries a warning on one screen and none on the other.
     *  [FUEL-PRICE-RESOLVER-001] */
    val costPrice: FuelPrice? = null,
    /**
     * The month that just closed, with the month before it attached for the ▲/▼ — null except
     * during the first days of a new month, and for a free account always.
     * [STATS-PLUS-REMEMBERS-AND-REPORTS-001]
     */
    val closedMonth: PeriodStats? = null,
    /**
     * Where [closedMonth]'s spend came from. Its OWN price and not [costPrice]: the two cards span
     * different windows, so the country the recap was priced in need not be the country the
     * selected period was. A figure that cannot describe itself gets described by whoever reads it.
     * [FUEL-AN-AVERAGE-MUST-SAY-WHEN-IT-WAS-MEASURED-001]
     */
    val closedMonthPrice: FuelPrice? = null,
    /**
     * What the app concluded about this period, biggest finding first — empty when the period
     * supports nothing, which below the thresholds is most of the time.
     * [STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001]
     */
    val insights: List<StatsInsight> = emptyList(),
    /** Ended parkings and shared spots over the car's WHOLE life, gate or no gate. */
    val lifetimeParkings: Int = 0,
    val lifetimeSpotsReleased: Int = 0,
    val isPlus: Boolean = false,
    /** Why the next trip will not be measured, or null when it will. See `VehiclesState`.
     *  [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001] */
    val detectionPause: DetectionPause? = null,
) {
    val isLoading: Boolean get() = vehicles == null

    val selectedVehicle: VehicleWithStats?
        get() = vehicles?.getOrNull(selectedVehicleIndex)

    /** More than one car is what makes a selector worth its space. */
    val showsVehicleSelector: Boolean get() = (vehicles?.size ?: 0) > 1
}

package com.rndeveloper.paparcar.presentation.stats

import com.rndeveloper.paparcar.domain.stats.HeatMeasure
import com.rndeveloper.paparcar.presentation.vehicles.ChartMetric
import com.rndeveloper.paparcar.presentation.vehicles.HistoryFilter

sealed class StatsIntent {
    data class SelectVehicle(val index: Int) : StatsIntent()
    data class SetFilter(val filter: HistoryFilter) : StatsIntent()

    /** Which series the chart draws — the panel offers the SAME choice as the ficha, or it would
     *  be a second interpretation of one card. [STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001] */
    data class SetChartMetric(val metric: ChartMetric) : StatsIntent()

    /** Which measure the day x band grid shows. [STATS-THE-EXPENSIVE-HOUR-IS-MEASURED-001] */
    data class SetHeatMeasure(val measure: HeatMeasure) : StatsIntent()

    /** The Plus row under the panel — choosing any period. [STATS-PANEL-001] */
    data object OpenPaywall : StatsIntent()
}

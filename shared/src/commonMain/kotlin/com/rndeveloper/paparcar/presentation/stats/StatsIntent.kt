package com.rndeveloper.paparcar.presentation.stats

import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.domain.stats.HeatMeasure
import com.rndeveloper.paparcar.presentation.vehicles.ChartMetric
import com.rndeveloper.paparcar.presentation.vehicles.StatsPeriod

sealed class StatsIntent {
    data class SelectVehicle(val index: Int) : StatsIntent()
    data class SetFilter(val filter: StatsPeriod) : StatsIntent()

    /** Which series the chart draws — the panel offers the SAME choice as the ficha, or it would
     *  be a second interpretation of one card. [STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001] */
    data class SetChartMetric(val metric: ChartMetric) : StatsIntent()

    /** Which measure the day x band grid shows. [STATS-THE-EXPENSIVE-HOUR-IS-MEASURED-001] */
    data class SetHeatMeasure(val measure: HeatMeasure) : StatsIntent()

    /**
     * A Plus touchpoint on this screen: the row under the figures, or any locked card.
     * [STATS-PANEL-001]
     *
     * ⛔ It carries its own [reason] because this screen now has more than one, and they are not
     * the same wish: wanting to be TOLD how the month went and wanting to CHOOSE a period are two
     * desires, and telling them apart is the whole job of the funnel — the same asymmetry
     * `SettingsViewModel` keeps between its recap row and its neighbours.
     * [MONEY-A-BLUR-IS-ONLY-HONEST-WHEN-THERE-IS-SOMETHING-BEHIND-IT-001]
     */
    data class OpenPaywall(val reason: PaywallReason) : StatsIntent()

    /** The CTA of the paused-detection notice. ONE action whatever is in the way: bank the intent
     *  and open the flow that grants what is missing. [DET-TOGGLE-001]
     *  [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001] */
    data object EnableAutoDetection : StatsIntent()
}

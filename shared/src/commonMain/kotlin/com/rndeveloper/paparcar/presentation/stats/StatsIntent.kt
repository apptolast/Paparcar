package com.rndeveloper.paparcar.presentation.stats

import com.rndeveloper.paparcar.presentation.vehicles.HistoryFilter

sealed class StatsIntent {
    data class SelectVehicle(val index: Int) : StatsIntent()
    data class SetFilter(val filter: HistoryFilter) : StatsIntent()

    /** The Plus row under the panel — choosing any period. [STATS-PANEL-001] */
    data object OpenPaywall : StatsIntent()
}

package com.rndeveloper.paparcar.presentation.stats

import com.rndeveloper.paparcar.presentation.vehicles.HistoryFilter

sealed class StatsIntent {
    data class SelectVehicle(val index: Int) : StatsIntent()
    data class SetFilter(val filter: HistoryFilter) : StatsIntent()

    /** The Plus row under a veiled panel. [STATS-PANEL-001] */
    data object OpenPaywall : StatsIntent()
}

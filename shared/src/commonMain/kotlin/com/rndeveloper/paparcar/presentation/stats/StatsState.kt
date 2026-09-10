package com.rndeveloper.paparcar.presentation.stats

import androidx.compose.runtime.Immutable
import com.rndeveloper.paparcar.domain.fuel.FuelPrice
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.VehicleWithStats
import com.rndeveloper.paparcar.domain.stats.PeriodStats
import com.rndeveloper.paparcar.presentation.vehicles.HistoryFilter

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
    val filter: HistoryFilter = HistoryFilter.ThisMonth,
    /**
     * The selected car's figures for the selected period, with its previous period attached.
     * Null once resolved means the period holds no session — an empty state, not a zero.
     */
    val periodStats: PeriodStats? = null,
    /** The period's sessions, for the chart's buckets — the same list the figures came from. */
    val filteredSessions: List<UserParking> = emptyList(),
    /** Where the spend figure's price came from — the panel owes the same caveat the ficha gives,
     *  or the same number carries a warning on one screen and none on the other.
     *  [FUEL-PRICE-RESOLVER-001] */
    val costPrice: FuelPrice? = null,
    /** Ended parkings and shared spots over the car's WHOLE life, gate or no gate. */
    val lifetimeParkings: Int = 0,
    val lifetimeSpotsReleased: Int = 0,
    val isPlus: Boolean = false,
) {
    val isLoading: Boolean get() = vehicles == null

    val selectedVehicle: VehicleWithStats?
        get() = vehicles?.getOrNull(selectedVehicleIndex)

    /** More than one car is what makes a selector worth its space. */
    val showsVehicleSelector: Boolean get() = (vehicles?.size ?: 0) > 1
}

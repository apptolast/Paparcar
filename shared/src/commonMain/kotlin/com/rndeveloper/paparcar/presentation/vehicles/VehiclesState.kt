package com.rndeveloper.paparcar.presentation.vehicles

import androidx.compose.runtime.Immutable
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleWithStats

@Immutable
data class VehiclesState(
    val vehicles: List<VehicleWithStats> = emptyList(),
    val isLoading: Boolean = true,
    /** Id of the vehicle whose isActive flag is being switched. Null when idle. */
    val settingActiveVehicleId: String? = null,
    val selectedVehicleIndex: Int = 0,
    /** A car asked for from outside the screen whose page could not be resolved yet — the list had
     *  not arrived. Resolved (and cleared) on the first emission that contains it.
     *  [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001] */
    val pendingFocusVehicleId: String? = null,
    val bluetoothConnectedVehicleId: String? = null,
    val historyCache: Map<String, HistoryState> = emptyMap(),
    /** Live Plus entitlement — what SetHistoryFilter re-resolves the timeline with. [MONEY-001] */
    val isPlus: Boolean = false,
    /** Automatic detection switched on in Settings — the empty figures card asks for it otherwise.
     *  [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001] */
    val isAutoDetectionOn: Boolean = true,
) {
    val currentVehicleId: String?
        get() = vehicles.getOrNull(selectedVehicleIndex)?.vehicle?.id

    /**
     * No cache entry means this vehicle's history has NOT arrived yet — never that it has none.
     * [UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001]
     */
    val historyState: HistoryState
        get() = currentVehicleId?.let { historyCache[it] }
            ?: HistoryState(timeline = HistoryTimeline.Unresolved)

    val activeVehicle: Vehicle?
        get() = vehicles
            .map { it.vehicle }
            .let { list ->
                list.firstOrNull { it.id == bluetoothConnectedVehicleId }
                    ?: list.firstOrNull { it.isActive }
            }
}

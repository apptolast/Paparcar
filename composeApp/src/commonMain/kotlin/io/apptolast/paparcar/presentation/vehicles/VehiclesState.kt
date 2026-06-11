package io.apptolast.paparcar.presentation.vehicles

import androidx.compose.runtime.Immutable
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleWithStats

@Immutable
data class VehiclesState(
    val vehicles: List<VehicleWithStats> = emptyList(),
    val isLoading: Boolean = true,
    /** Id of the vehicle whose isActive flag is being switched. Null when idle. */
    val settingActiveVehicleId: String? = null,
    val selectedVehicleIndex: Int = 0,
    val bluetoothConnectedVehicleId: String? = null,
    val historyCache: Map<String, HistoryState> = emptyMap(),
) {
    val currentVehicleId: String?
        get() = vehicles.getOrNull(selectedVehicleIndex)?.vehicle?.id

    val historyState: HistoryState
        get() = currentVehicleId?.let { historyCache[it] } ?: HistoryState(isLoading = false)

    val activeVehicle: Vehicle?
        get() = vehicles
            .map { it.vehicle }
            .let { list ->
                list.firstOrNull { it.id == bluetoothConnectedVehicleId }
                    ?: list.firstOrNull { it.isActive }
            }
}

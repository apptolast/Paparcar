package io.apptolast.paparcar.presentation.vehicles

import androidx.compose.runtime.Immutable
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleWithStats

@Immutable
data class VehiclesState(
    val vehicles: List<VehicleWithStats> = emptyList(),
    val isLoading: Boolean = true,
    val selectedVehicleIndex: Int = 0,
    val pendingDeleteVehicleId: String? = null,
    val bluetoothConnectedVehicleId: String? = null,
    val historyState: HistoryState = HistoryState(),
) {
    val activeVehicle: Vehicle?
        get() = vehicles
            .map { it.vehicle }
            .let { list ->
                list.firstOrNull { it.id == bluetoothConnectedVehicleId }
                    ?: list.firstOrNull { it.isDefault }
            }
}

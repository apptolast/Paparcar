package io.apptolast.paparcar.presentation.vehicles

import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleWithStats

data class VehiclesState(
    val vehicles: List<VehicleWithStats> = emptyList(),
    val isLoading: Boolean = true,
    val pendingDeleteVehicleId: String? = null,
    val bluetoothConnectedVehicleId: String? = null,
) {
    val activeVehicle: Vehicle?
        get() = vehicles
            .map { it.vehicle }
            .let { list ->
                list.firstOrNull { it.id == bluetoothConnectedVehicleId }
                    ?: list.firstOrNull { it.isDefault }
            }
}

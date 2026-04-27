package io.apptolast.paparcar.presentation.vehicles

import io.apptolast.paparcar.domain.model.VehicleWithStats

data class VehiclesState(
    val vehicles: List<VehicleWithStats> = emptyList(),
    val isLoading: Boolean = true,
    val pendingDeleteVehicleId: String? = null,
)

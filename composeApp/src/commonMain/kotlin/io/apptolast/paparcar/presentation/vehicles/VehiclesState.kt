package io.apptolast.paparcar.presentation.vehicles

import io.apptolast.paparcar.domain.model.Vehicle

data class VehiclesState(
    val vehicles: List<Vehicle> = emptyList(),
    val isLoading: Boolean = true,
    val pendingDeleteVehicleId: String? = null,
)

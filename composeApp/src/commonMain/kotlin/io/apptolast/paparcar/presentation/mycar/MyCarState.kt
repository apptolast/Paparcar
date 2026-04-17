package io.apptolast.paparcar.presentation.mycar

import io.apptolast.paparcar.domain.model.Vehicle

data class MyCarState(
    val vehicles: List<Vehicle> = emptyList(),
    val isLoading: Boolean = true,
    val pendingDeleteVehicleId: String? = null,
)
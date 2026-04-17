package io.apptolast.paparcar.presentation.vehicle

import io.apptolast.paparcar.domain.model.VehicleSize

data class VehicleRegistrationState(
    val brand: String = "",
    val model: String = "",
    val sizeCategory: VehicleSize? = null,
    val showBrandModelOnSpot: Boolean = false,
    val isSaving: Boolean = false,
    val editingVehicleId: String? = null,
)
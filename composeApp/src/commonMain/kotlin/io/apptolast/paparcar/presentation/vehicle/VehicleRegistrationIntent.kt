package io.apptolast.paparcar.presentation.vehicle

import io.apptolast.paparcar.domain.model.VehicleSize

sealed class VehicleRegistrationIntent {
    data class SetBrand(val value: String) : VehicleRegistrationIntent()
    data class SetModel(val value: String) : VehicleRegistrationIntent()
    data class SetSize(val size: VehicleSize) : VehicleRegistrationIntent()
    data class SetShowOnSpot(val enabled: Boolean) : VehicleRegistrationIntent()
    data class LoadVehicle(val vehicleId: String) : VehicleRegistrationIntent()
    data object Save : VehicleRegistrationIntent()
    data object NavigateBack : VehicleRegistrationIntent()
}
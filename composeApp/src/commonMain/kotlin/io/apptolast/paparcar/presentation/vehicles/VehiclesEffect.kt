package io.apptolast.paparcar.presentation.vehicles

import io.apptolast.paparcar.domain.error.PaparcarError

sealed class VehiclesEffect {
    data object NavigateToAddVehicle : VehiclesEffect()
    data class NavigateToEditVehicle(val vehicleId: String) : VehiclesEffect()
    data class NavigateToHistory(val vehicleId: String) : VehiclesEffect()
    data class ShowError(val error: PaparcarError) : VehiclesEffect()
}

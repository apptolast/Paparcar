package com.rndeveloper.paparcar.presentation.vehicles

import com.rndeveloper.paparcar.domain.error.PaparcarError

sealed class VehiclesEffect {
    data object NavigateToAddVehicle : VehiclesEffect()
    data class NavigateToEditVehicle(val vehicleId: String) : VehiclesEffect()
    data class NavigateToMap(val lat: Double, val lon: Double, val sessionId: String = "") : VehiclesEffect()
    data class ShowError(val error: PaparcarError) : VehiclesEffect()
}

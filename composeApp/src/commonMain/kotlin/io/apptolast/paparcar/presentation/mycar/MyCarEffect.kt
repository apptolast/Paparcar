package io.apptolast.paparcar.presentation.mycar

import io.apptolast.paparcar.domain.error.PaparcarError

sealed class MyCarEffect {
    data object NavigateToAddVehicle : MyCarEffect()
    data class NavigateToEditVehicle(val vehicleId: String) : MyCarEffect()
    data class ShowError(val error: PaparcarError) : MyCarEffect()
}
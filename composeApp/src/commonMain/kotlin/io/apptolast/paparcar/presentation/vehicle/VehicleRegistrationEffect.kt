package io.apptolast.paparcar.presentation.vehicle

import io.apptolast.paparcar.domain.error.PaparcarError

sealed class VehicleRegistrationEffect {
    data object NavigateBack : VehicleRegistrationEffect()
    data object SavedSuccessfully : VehicleRegistrationEffect()
    data class ShowError(val error: PaparcarError) : VehicleRegistrationEffect()
}
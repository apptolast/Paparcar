package io.apptolast.paparcar.presentation.map

import io.apptolast.paparcar.domain.error.PaparcarError

sealed class ParkingLocationEffect {
    data class NavigateToSpotDetails(val spotId: String) : ParkingLocationEffect()
    data class ShowError(val error: PaparcarError) : ParkingLocationEffect()
}

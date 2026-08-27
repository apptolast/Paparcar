package io.apptolast.paparcar.presentation.map

import io.apptolast.paparcar.domain.error.PaparcarError

sealed class ParkingHistoryEffect {
    data class NavigateToSpotDetails(val spotId: String) : ParkingHistoryEffect()
    data class ShowError(val error: PaparcarError) : ParkingHistoryEffect()
}

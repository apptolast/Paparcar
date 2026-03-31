package io.apptolast.paparcar.presentation.map

import io.apptolast.paparcar.domain.error.PaparcarError

sealed class MapEffect {
    data class NavigateToSpotDetails(val spotId: String) : MapEffect()
    data class ShowError(val error: PaparcarError) : MapEffect()
}
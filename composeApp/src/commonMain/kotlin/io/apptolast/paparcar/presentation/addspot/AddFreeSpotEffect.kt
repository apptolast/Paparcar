package io.apptolast.paparcar.presentation.addspot

import io.apptolast.paparcar.domain.error.PaparcarError

sealed class AddFreeSpotEffect {
    data object SpotReported : AddFreeSpotEffect()
    data class ShowError(val error: PaparcarError) : AddFreeSpotEffect()
}

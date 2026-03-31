package io.apptolast.paparcar.presentation.history

import io.apptolast.paparcar.domain.error.PaparcarError

sealed class HistoryEffect {
    data class ShowError(val error: PaparcarError) : HistoryEffect()
    data class NavigateToMap(val lat: Double, val lon: Double) : HistoryEffect()
}
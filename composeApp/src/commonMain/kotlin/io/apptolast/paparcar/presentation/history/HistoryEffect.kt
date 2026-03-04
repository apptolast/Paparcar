package io.apptolast.paparcar.presentation.history

sealed class HistoryEffect {
    data class ShowError(val message: String) : HistoryEffect()
    data object NavigateBack : HistoryEffect()
    data class NavigateToMap(val lat: Double, val lon: Double) : HistoryEffect()
}
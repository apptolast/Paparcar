package io.apptolast.paparcar.presentation.history

sealed interface HistoryEffect {
    data class ShowError(val message: String) : HistoryEffect
    object NavigateBack : HistoryEffect
}

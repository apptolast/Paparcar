package io.apptolast.paparcar.presentation.history

sealed interface HistoryIntent {
    data object LoadHistory : HistoryIntent
    data class SpotSelected(val spotId: String) : HistoryIntent
}

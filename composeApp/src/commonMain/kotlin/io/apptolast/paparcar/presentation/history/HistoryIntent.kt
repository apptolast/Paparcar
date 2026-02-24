package io.apptolast.paparcar.presentation.history

sealed class HistoryIntent {
    data object LoadHistory : HistoryIntent()
    data class SessionSelected(val sessionId: String) : HistoryIntent()
}

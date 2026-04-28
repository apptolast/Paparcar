package io.apptolast.paparcar.presentation.history

sealed class HistoryIntent {
    data class ViewOnMap(val lat: Double, val lon: Double) : HistoryIntent()
    data class SetFilter(val filter: HistoryFilter) : HistoryIntent()
}

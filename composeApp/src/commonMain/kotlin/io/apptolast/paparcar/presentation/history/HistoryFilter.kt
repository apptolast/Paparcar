package io.apptolast.paparcar.presentation.history

sealed class HistoryFilter {
    data object All : HistoryFilter()
    data object ThisWeek : HistoryFilter()
    data object ThisMonth : HistoryFilter()
    data object Last3Months : HistoryFilter()
}

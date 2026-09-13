package com.rndeveloper.paparcar.presentation.vehicles

sealed class HistoryFilter {
    data object All : HistoryFilter()
    data object ThisWeek : HistoryFilter()

    /** The last seven days, today included — rolling, unlike [ThisWeek]'s calendar week. It is also
     *  the free plan's whole stats window. [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001] */
    data object Last7Days : HistoryFilter()
    data object ThisMonth : HistoryFilter()
    data object Last3Months : HistoryFilter()
}

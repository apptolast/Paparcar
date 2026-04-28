package io.apptolast.paparcar.presentation.history

data class HistoryStatsData(
    val avgSessionsPerWeek: Float?,    // null if fewer than 2 weeks of data
    val mostActiveDayOfWeek: Int?,     // isoDayNumber 1=Mon..7=Sun, null if < 5 sessions
    val favoriteStreet: String?,       // null if no address data
    val avgReliabilityPct: Int?,       // 0-100, null if no reliability data
)

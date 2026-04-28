package io.apptolast.paparcar.presentation.history

import io.apptolast.paparcar.domain.model.UserParking

data class HistoryState(
    val isLoading: Boolean = true,
    val sessions: List<UserParking> = emptyList(),
    val activeFilter: HistoryFilter = HistoryFilter.All,
    val filteredSessions: List<UserParking> = emptyList(),
    val statsData: HistoryStatsData? = null,
)

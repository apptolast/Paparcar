package com.rndeveloper.paparcar.presentation.vehicles

import androidx.compose.runtime.Immutable
import com.rndeveloper.paparcar.domain.model.UserParking

@Immutable
data class HistoryState(
    val isLoading: Boolean = false,
    val sessions: List<UserParking> = emptyList(),
    val activeFilter: HistoryFilter = HistoryFilter.All,
    val filteredSessions: List<UserParking> = emptyList(),
    val statsData: HistoryStatsData? = null,
)

package io.apptolast.paparcar.presentation.vehicles

import androidx.compose.runtime.Immutable
import io.apptolast.paparcar.domain.model.UserParking

@Immutable
data class HistoryState(
    val isLoading: Boolean = true,
    val isLoadingNextPage: Boolean = false,
    val sessions: List<UserParking> = emptyList(),
    val activeFilter: HistoryFilter = HistoryFilter.All,
    val filteredSessions: List<UserParking> = emptyList(),
    val statsData: HistoryStatsData? = null,
    val hasMorePages: Boolean = true,
    val currentPage: Int = 0,
)

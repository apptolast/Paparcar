package io.apptolast.paparcar.presentation.history

import io.apptolast.paparcar.domain.model.UserParkingSession

data class HistoryState(
    val isLoading: Boolean = false,
    val sessions: List<UserParkingSession> = emptyList(),
    val error: String? = null,
)

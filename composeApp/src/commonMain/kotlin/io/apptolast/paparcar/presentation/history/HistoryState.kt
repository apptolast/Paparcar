package io.apptolast.paparcar.presentation.history

import io.apptolast.paparcar.domain.model.ParkingSession

data class HistoryState(
    val isLoading: Boolean = false,
    val sessions: List<ParkingSession> = emptyList(),
    val error: String? = null,
)

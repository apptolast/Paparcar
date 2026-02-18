package io.apptolast.paparcar.presentation.history

import io.apptolast.paparcar.domain.model.Spot

data class HistoryState(
    val isLoading: Boolean = false,
    val history: List<Spot> = emptyList(),
    val error: String? = null
)

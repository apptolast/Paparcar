package io.apptolast.paparcar.presentation.history

import io.apptolast.paparcar.domain.usecase.parking.GetAllUserParkingsUseCase
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val getAllSessions: GetAllUserParkingsUseCase,
) : BaseViewModel<HistoryState, HistoryIntent, HistoryEffect>() {

    init {
        loadHistory()
    }

    override fun initState(): HistoryState = HistoryState()

    override fun handleIntent(intent: HistoryIntent) {
        when (intent) {
            is HistoryIntent.LoadHistory -> loadHistory()
            is HistoryIntent.ViewOnMap -> sendEffect(
                HistoryEffect.NavigateToMap(intent.lat, intent.lon)
            )
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            runCatching { getAllSessions() }
                .onSuccess { sessions ->
                    updateState { copy(isLoading = false, sessions = sessions) }
                }
                .onFailure { t ->
                    updateState { copy(isLoading = false, error = t.message) }
                    sendEffect(HistoryEffect.ShowError(t.message ?: "Error al cargar historial"))
                }
        }
    }
}

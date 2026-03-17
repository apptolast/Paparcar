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
            is HistoryIntent.LoadHistory -> loadHistory(isRefresh = true)
            is HistoryIntent.ViewOnMap -> sendEffect(
                HistoryEffect.NavigateToMap(intent.lat, intent.lon)
            )
        }
    }

    private fun loadHistory(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                updateState { copy(isRefreshing = true) }
            } else {
                updateState { copy(isLoading = true) }
            }
            runCatching { getAllSessions() }
                .onSuccess { sessions ->
                    updateState { copy(isLoading = false, isRefreshing = false, sessions = sessions) }
                }
                .onFailure { t ->
                    updateState { copy(isLoading = false, isRefreshing = false) }
                    sendEffect(HistoryEffect.ShowError(t.message ?: "Error al cargar historial"))
                }
        }
    }
}

package io.apptolast.paparcar.presentation.history

import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val userParkingRepository: UserParkingRepository,
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
            updateState { copy(isLoading = true) }
            runCatching { userParkingRepository.getAllSessions() }
                .onSuccess { sessions ->
                    updateState { copy(isLoading = false, sessions = sessions) }
                }
                .onFailure { t ->
                    updateState { copy(isLoading = false) }
                    sendEffect(HistoryEffect.ShowError(t.message ?: "Error al cargar historial"))
                }
        }
    }
}
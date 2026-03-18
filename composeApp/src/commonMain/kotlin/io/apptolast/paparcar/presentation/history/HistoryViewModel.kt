package io.apptolast.paparcar.presentation.history

import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class HistoryViewModel(
    private val userParkingRepository: UserParkingRepository,
) : BaseViewModel<HistoryState, HistoryIntent, HistoryEffect>() {

    init {
        userParkingRepository.observeAllSessions()
            .onEach { sessions -> updateState { copy(isLoading = false, sessions = sessions) } }
            .catch { e ->
                updateState { copy(isLoading = false) }
                sendEffect(HistoryEffect.ShowError(e.message ?: "Error al cargar historial"))
            }
            .launchIn(viewModelScope)
    }

    override fun initState(): HistoryState = HistoryState()

    override fun handleIntent(intent: HistoryIntent) {
        when (intent) {
            is HistoryIntent.ViewOnMap -> sendEffect(
                HistoryEffect.NavigateToMap(intent.lat, intent.lon)
            )
        }
    }
}
package io.apptolast.paparcar.presentation.map

import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class ParkingLocationViewModel(
    observeAdaptiveLocation: ObserveAdaptiveLocationUseCase,
    private val userParkingRepository: UserParkingRepository,
) : BaseViewModel<ParkingLocationState, ParkingLocationIntent, ParkingLocationEffect>() {

    init {
        userParkingRepository.observeActiveSessions()
            .map { it.firstOrNull() }
            .onEach { session -> updateState { copy(userParking = session) } }
            .catch { e ->
                sendEffect(ParkingLocationEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
            }
            .launchIn(viewModelScope)

        observeAdaptiveLocation()
            .onEach { location ->
                updateState { copy(isLoading = false, userLocation = location) }
            }
            .catch { e ->
                updateState { copy(isLoading = false) }
                sendEffect(ParkingLocationEffect.ShowError(PaparcarError.Location.Unknown(e.message ?: "")))
            }
            .launchIn(viewModelScope)
    }

    override fun initState(): ParkingLocationState = ParkingLocationState()

    override fun handleIntent(intent: ParkingLocationIntent) {
        when (intent) {
            is ParkingLocationIntent.OnSpotSelected ->
                sendEffect(ParkingLocationEffect.NavigateToSpotDetails(intent.spotId))

            is ParkingLocationIntent.SetFocusedSession -> {
                userParkingRepository.observeAllSessions()
                    .map { sessions -> sessions.find { it.id == intent.sessionId } }
                    .onEach { session -> updateState { copy(focusedSession = session) } }
                    .catch { /* silently ignore — initialFocus coordinates are still shown on map */ }
                    .launchIn(viewModelScope)
            }
        }
    }
}

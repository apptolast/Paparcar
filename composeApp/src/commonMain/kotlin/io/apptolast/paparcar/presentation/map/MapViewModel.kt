package io.apptolast.paparcar.presentation.map

import io.apptolast.paparcar.domain.usecase.location.ObserveLocationUpdatesUseCase
import io.apptolast.paparcar.domain.usecase.parking.ObserveUserParkingUseCase
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(
    observeLocationUpdates: ObserveLocationUpdatesUseCase,
    observeNearbySpots: ObserveNearbySpotsUseCase,
    observeUserParking: ObserveUserParkingUseCase,
) : BaseViewModel<MapState, MapIntent, MapEffect>() {

    init {
        observeUserParking()
            .onEach { session -> updateState { copy(userParking = session) } }
            .catch { e ->
                sendEffect(MapEffect.ShowError(e.message ?: "Error desconocido"))
            }
            .launchIn(viewModelScope)

        observeLocationUpdates()
            .onEach { location ->
                updateState { copy(isLoading = false, userLocation = location) }
            }
            .catch { e ->
                // GPS / location chain error
                updateState { copy(isLoading = false) }
                sendEffect(MapEffect.ShowError(e.message ?: "Error desconocido"))
            }
            .launchIn(viewModelScope)
    }

    override fun initState(): MapState = MapState()

    override fun handleIntent(intent: MapIntent) {
        when (intent) {
            is MapIntent.OnSpotSelected -> sendEffect(MapEffect.NavigateToSpotDetails(intent.spotId))
        }
    }
}

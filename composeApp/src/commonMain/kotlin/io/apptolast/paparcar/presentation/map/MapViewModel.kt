package io.apptolast.paparcar.presentation.map

import io.apptolast.paparcar.domain.usecase.location.ObserveLocationUpdatesUseCase
import io.apptolast.paparcar.domain.usecase.parking.ObserveUserParkingUseCase
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
            .launchIn(viewModelScope)

        observeLocationUpdates()
            .onEach { location ->
                updateState { copy(isLoading = false, userLocation = location) }
            }
            .flatMapLatest { userLocation ->
                observeNearbySpots(userLocation, 1000.0)
            }
            .onEach { spots ->
                updateState { copy(spots = spots) }
            }
            .launchIn(viewModelScope)
    }

    override fun initState(): MapState = MapState()

    override fun handleIntent(intent: MapIntent) {
        when (intent) {
            is MapIntent.LoadSpots -> { /* flow already running from init */ }
            is MapIntent.OnSpotSelected -> sendEffect(MapEffect.NavigateToSpotDetails(intent.spotId))
        }
    }
}

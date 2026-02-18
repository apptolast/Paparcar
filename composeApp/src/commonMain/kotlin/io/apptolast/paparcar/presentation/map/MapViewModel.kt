package io.apptolast.paparcar.presentation.map

import io.apptolast.paparcar.domain.usecase.location.ObserveLocationUpdatesUseCase
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(
    observeLocationUpdates: ObserveLocationUpdatesUseCase,
    observeNearbySpots: ObserveNearbySpotsUseCase
) : BaseViewModel<MapState, MapIntent, MapEffect>() {

    init {
        observeLocationUpdates()
            .onEach { location ->
                updateState { copy(isLoading = false, userLocation = location) }
            }
            .flatMapLatest { userLocation ->
                // Una vez que tenemos la ubicación, observamos los spots cercanos
                observeNearbySpots(userLocation, 1000.0) // Usamos un radio de 1km
            }
            .onEach { spots ->
                updateState { copy(spots = spots) }
            }
            .launchIn(viewModelScope)
    }

    override fun initState(): MapState = MapState()

    override fun handleIntent(intent: MapIntent) {
        when (intent) {
            is MapIntent.LoadSpots -> { /* El flujo ya se carga en el init */ }
            is MapIntent.OnSpotSelected -> sendEffect(MapEffect.NavigateToSpotDetails(intent.spotId))
        }
    }
}

package io.apptolast.paparcar.presentation.map

import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(
    private val locationDataSource: LocationDataSource,
    observeNearbySpots: ObserveNearbySpotsUseCase,
    private val userParkingRepository: UserParkingRepository,
) : BaseViewModel<MapState, MapIntent, MapEffect>() {

    init {
        userParkingRepository.observeActiveSession()
            .onEach { session -> updateState { copy(userParking = session) } }
            .catch { e ->
                sendEffect(MapEffect.ShowError(e.message ?: "Unknown error"))
            }
            .launchIn(viewModelScope)

        locationDataSource.observeBalancedLocation()
            .onEach { location ->
                updateState { copy(isLoading = false, userLocation = location) }
            }
            .catch { e ->
                // GPS / location chain error
                updateState { copy(isLoading = false) }
                sendEffect(MapEffect.ShowError(e.message ?: "Unknown error"))
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

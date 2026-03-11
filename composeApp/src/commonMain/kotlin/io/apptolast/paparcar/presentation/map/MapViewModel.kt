package io.apptolast.paparcar.presentation.map

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.usecase.location.ObserveLocationUpdatesUseCase
import io.apptolast.paparcar.domain.usecase.parking.ObserveUserParkingUseCase
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MapViewModel(
    observeLocationUpdates: ObserveLocationUpdatesUseCase,
    observeNearbySpots: ObserveNearbySpotsUseCase,
    observeUserParking: ObserveUserParkingUseCase,
) : BaseViewModel<MapState, MapIntent, MapEffect>() {

    init {
        observeUserParking()
            .onEach { session -> updateState { copy(userParking = session) } }
            .catch { e ->
                updateState { copy(error = e.message) }
                sendEffect(MapEffect.ShowError(e.message ?: "Error desconocido"))
            }
            .launchIn(viewModelScope)

        observeLocationUpdates()
            .onEach { location ->
                updateState { copy(isLoading = false, userLocation = location) }
            }
            .catch { e ->
                updateState { copy(isLoading = false, error = e.message) }
                sendEffect(MapEffect.ShowError(e.message ?: "Error desconocido"))
            }
            .launchIn(viewModelScope)

        // Spots are observed independently of user location so that they load immediately
        // without waiting for a GPS fix. FirebaseDataSourceImpl fetches all spots from the
        // collection anyway; geographic filtering will be added via Geohash queries later.
        observeNearbySpots(
            location = GpsPoint(latitude = 0.0, longitude = 0.0, accuracy = 0f, timestamp = 0L, speed = 0f),
            radiusMeters = ObserveNearbySpotsUseCase.DEFAULT_SEARCH_RADIUS_METERS,
        )
            .onEach { spots -> updateState { copy(spots = spots) } }
            .catch { e ->
                updateState { copy(error = e.message) }
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

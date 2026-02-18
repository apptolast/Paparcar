package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.usecase.location.ObserveLocationUpdatesUseCase
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val permissionManager: PermissionManager,
    private val observeLocationUpdates: ObserveLocationUpdatesUseCase,
    private val observeNearbySpots: ObserveNearbySpotsUseCase
) : BaseViewModel<HomeState, HomeIntent, HomeEffect>() {

    init {
        // Cadena reactiva que gestiona todo el flujo de datos.
        permissionManager.permissionState
            .flatMapLatest { permissionState ->
                if (permissionState.allPermissionsGranted) {
                    observeLocationUpdates()
                } else {
                    updateState { copy(userLocation = null, nearbySpots = emptyList()) }
                    emptyFlow()
                }
            }
            .flatMapLatest { userLocation ->
                updateState { copy(isLoading = false, userLocation = Pair(userLocation.latitude, userLocation.longitude)) }
                observeNearbySpots(userLocation, 1000.0) // 1km de radio
            }
            .onEach { spots ->
                updateState { copy(nearbySpots = spots) }
            }
            .launchIn(viewModelScope)
    }

    override fun initState(): HomeState = HomeState()

    override fun handleIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.LoadNearbySpots -> {
                if (!permissionManager.permissionState.value.allPermissionsGranted) {
                    sendEffect(HomeEffect.RequestLocationPermission)
                }
            }
            is HomeIntent.OpenMap -> sendEffect(HomeEffect.NavigateToMap(null))
            is HomeIntent.SpotSelected -> sendEffect(HomeEffect.NavigateToMap(intent.spotId))
            else -> { /* Otros intents no se manejan aquí */ }
        }
    }
}

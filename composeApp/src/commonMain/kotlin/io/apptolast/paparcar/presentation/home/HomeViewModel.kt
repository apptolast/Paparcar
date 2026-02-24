package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotLocation
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.usecase.location.ObserveLocationUpdatesUseCase
import io.apptolast.paparcar.domain.usecase.parking.ObserveUserParkingUseCase
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)
class HomeViewModel(
    private val permissionManager: PermissionManager,
    private val observeLocationUpdates: ObserveLocationUpdatesUseCase,
    private val observeNearbySpots: ObserveNearbySpotsUseCase,
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val activityRecognitionManager: ActivityRecognitionManager,
    private val observeUserParking: ObserveUserParkingUseCase,
) : BaseViewModel<HomeState, HomeIntent, HomeEffect>() {

    private val simulatedUserId = "user-123"

    init {
        // Observe active parking session.
        observeUserParking()
            .onEach { session -> updateState { copy(userParking = session) } }
            .launchIn(viewModelScope)

        // Reactive chain: permissions → location → nearby spots.
        permissionManager.permissionState
            .flatMapLatest { permissionState ->
                if (permissionState.allPermissionsGranted) {
                    activityRecognitionManager.registerTransitions()
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
            is HomeIntent.OpenHistory -> sendEffect(HomeEffect.NavigateToHistory)
            is HomeIntent.SpotSelected -> sendEffect(HomeEffect.NavigateToMap(intent.spotId))
            is HomeIntent.ReportTestSpot -> reportTestSpot()
            else -> { /* Otros intents no se manejan aquí */ }
        }
    }

    private fun reportTestSpot() {
        viewModelScope.launch {
            val timestamp = Clock.System.now().toEpochMilliseconds()
            val spotId = "${simulatedUserId}_$timestamp"

            val testSpot = Spot(
                id = spotId,
                location = SpotLocation(
                    latitude = 40.416775,
                    longitude = -3.703790,
                    accuracy = 10f,
                    timestamp = timestamp,
                    speed = 0f // Añadido
                ),
                reportedBy = simulatedUserId,
                isActive = true
            )

            reportSpotReleased(testSpot)
                .onSuccess {
                    sendEffect(HomeEffect.ShowSuccess("Spot de prueba reportado con éxito"))
                }
                .onFailure {
                    sendEffect(HomeEffect.ShowError("Error al reportar spot de prueba: ${it.message}"))
                }
        }
    }
}

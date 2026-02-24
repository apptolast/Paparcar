package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotLocation
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.usecase.location.GetAddressUseCase
import io.apptolast.paparcar.domain.usecase.location.ObserveLocationUpdatesUseCase
import io.apptolast.paparcar.domain.usecase.parking.ObserveUserParkingUseCase
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
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
    private val getAddress: GetAddressUseCase,
) : BaseViewModel<HomeState, HomeIntent, HomeEffect>() {

    init {
        // Observe active parking session.
        observeUserParking()
            .onEach { session -> updateState { copy(userParking = session) } }
            .catch { e ->
                updateState { copy(error = e.message) }
                sendEffect(HomeEffect.ShowError(e.message ?: "Error desconocido"))
            }
            .launchIn(viewModelScope)

        // Reactive chain: permissions → location → nearby spots.
        permissionManager.permissionState
            .flatMapLatest { permissionState ->
                updateState { copy(allPermissionsGranted = permissionState.allPermissionsGranted) }
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
                observeNearbySpots(userLocation, ObserveNearbySpotsUseCase.DEFAULT_SEARCH_RADIUS_METERS)
            }
            .onEach { spots ->
                updateState { copy(nearbySpots = spots) }
                fetchAddressesForNewSpots(spots)
            }
            .catch { e ->
                updateState { copy(error = e.message) }
                sendEffect(HomeEffect.ShowError(e.message ?: "Error desconocido"))
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
        }
    }

    private fun reportTestSpot() {
        viewModelScope.launch {
            val timestamp = Clock.System.now().toEpochMilliseconds()
            val spotId = "${DEBUG_USER_ID}_$timestamp"

            val testSpot = Spot(
                id = spotId,
                location = SpotLocation(
                    latitude = DEBUG_LATITUDE,
                    longitude = DEBUG_LONGITUDE,
                    accuracy = 10f,
                    timestamp = timestamp,
                    speed = 0f
                ),
                reportedBy = DEBUG_USER_ID,
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

    private fun fetchAddressesForNewSpots(spots: List<Spot>) {
        val knownIds = state.value.spotAddresses.keys
        spots.filter { it.id !in knownIds }.forEach { spot ->
            viewModelScope.launch {
                getAddress(spot.location.latitude, spot.location.longitude)
                    .onSuccess { address ->
                        updateState { copy(spotAddresses = spotAddresses + (spot.id to address)) }
                    }
            }
        }
    }

    companion object {
        private const val DEBUG_USER_ID = "user-123"
        private const val DEBUG_LATITUDE = 40.416775
        private const val DEBUG_LONGITUDE = -3.703790
    }
}

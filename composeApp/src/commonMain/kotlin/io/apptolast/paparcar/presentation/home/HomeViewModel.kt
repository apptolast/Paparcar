package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.isDebugBuild
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.usecase.location.GetLocationInfoUseCase
import io.apptolast.paparcar.domain.usecase.location.ObserveLocationUpdatesUseCase
import io.apptolast.paparcar.domain.usecase.parking.ClearUserParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.ObserveUserParkingUseCase
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)
class HomeViewModel(
    private val permissionManager: PermissionManager,
    private val observeLocationUpdates: ObserveLocationUpdatesUseCase,
    private val observeNearbySpots: ObserveNearbySpotsUseCase,
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val activityRecognitionManager: ActivityRecognitionManager,
    observeUserParking: ObserveUserParkingUseCase,
    private val clearUserParking: ClearUserParkingUseCase,
    private val getLocationInfo: GetLocationInfoUseCase,
    private val confirmParking: ConfirmParkingUseCase,
) : BaseViewModel<HomeState, HomeIntent, HomeEffect>() {

    init {
        observeUserParking()
            .onEach { session -> updateState { copy(userParking = session) } }
            .catch { e ->

                sendEffect(HomeEffect.ShowError(e.message ?: "Error desconocido"))
            }
            .launchIn(viewModelScope)

        permissionManager.permissionState
            .flatMapLatest { permissionState ->
                updateState { copy(allPermissionsGranted = permissionState.allPermissionsGranted) }
                if (permissionState.allPermissionsGranted) {
                    activityRecognitionManager.registerTransitions()
                    observeLocationUpdates()
                } else {
                    updateState { copy(nearbySpots = emptyList()) }
                    emptyFlow()
                }
            }
            .onEach { userLocation ->
                updateState { copy(isLoading = false, userGpsPoint = userLocation) }
                geocodeUserLocation(userLocation.latitude, userLocation.longitude)
                if (state.value.cameraLocationInfo == null) {
                    geocodeCameraLocation(userLocation.latitude, userLocation.longitude)
                }
            }
            .flatMapLatest { userLocation ->
                observeNearbySpots(
                    userLocation,
                    ObserveNearbySpotsUseCase.DEFAULT_SEARCH_RADIUS_METERS,
                ).catch { e ->
                    // Firebase error (permissions, network, format) — show it but keep GPS chain alive
    
                    sendEffect(HomeEffect.ShowError(e.message ?: "Error cargando spots cercanos"))
                    emit(emptyList())
                }
            }
            .onEach { spots ->
                updateState {
                    val cur = selectedItemId
                    copy(
                        nearbySpots = spots,
                        // Keep parking selection or a spot that still exists; clear otherwise.
                        selectedItemId = if (cur != PARKING_ITEM_ID && spots.none { it.id == cur }) null else cur,
                    )
                }
            }
            .catch { e ->
                // GPS/permissions chain error

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

            is HomeIntent.OpenMap -> sendEffect(HomeEffect.NavigateToMap)
            is HomeIntent.OpenHistory -> sendEffect(HomeEffect.NavigateToHistory)
            is HomeIntent.ReportTestSpot -> reportTestSpot()
            is HomeIntent.ReleaseParking -> releaseParking(intent.lat, intent.lon)
            is HomeIntent.SelectItem -> updateState { copy(selectedItemId = intent.itemId) }
            is HomeIntent.ManualPark -> manualPark()
            is HomeIntent.CameraPositionChanged -> geocodeCameraLocation(intent.lat, intent.lon)
        }
    }

    private fun releaseParking(lat: Double, lon: Double) {
        viewModelScope.launch {
            val spotId = state.value.userParking?.id
                ?: "manual_${Clock.System.now().toEpochMilliseconds()}"
            clearUserParking().onFailure { e ->
                sendEffect(HomeEffect.ShowError(e.message ?: "Error al liberar el parking"))
                return@launch
            }
            updateState { copy(selectedItemId = null) }
            sendEffect(HomeEffect.ShowSuccess("Spot reported — uploading…"))
            reportSpotReleased(lat, lon, spotId)
        }
    }

    private fun reportTestSpot() {
        if (!isDebugBuild) return
        viewModelScope.launch {
            val spotId = "${DEBUG_USER_ID}_${Clock.System.now().toEpochMilliseconds()}"
            reportSpotReleased(DEBUG_LATITUDE, DEBUG_LONGITUDE, spotId)
            sendEffect(HomeEffect.ShowSuccess("Spot de prueba enviado"))
        }
    }

    private fun manualPark() {
        val gps = state.value.userGpsPoint
        if (gps == null) {
            sendEffect(HomeEffect.ShowError("Waiting for GPS fix…"))
            return
        }
        viewModelScope.launch {
            confirmParking(gps)
        }
    }

    private var geocodeJob: Job? = null

    private fun geocodeUserLocation(lat: Double, lon: Double) {
        geocodeJob?.cancel()
        geocodeJob = viewModelScope.launch {
            getLocationInfo(lat, lon)
                .catch { /* best-effort; ignore errors */ }
                .collect { info -> updateState { copy(userLocationInfo = info) } }
        }
    }

    private var geocodeCameraJob: Job? = null

    private fun geocodeCameraLocation(lat: Double, lon: Double) {
        geocodeCameraJob?.cancel()
        geocodeCameraJob = viewModelScope.launch {
            delay(600L)
            getLocationInfo(lat, lon)
                .catch { /* best-effort; ignore errors */ }
                .collect { info -> updateState { copy(cameraLocationInfo = info) } }
        }
    }

    companion object {
        private const val DEBUG_USER_ID = "user-123"
        private const val DEBUG_LATITUDE = 40.416775
        private const val DEBUG_LONGITUDE = -3.703790
    }
}

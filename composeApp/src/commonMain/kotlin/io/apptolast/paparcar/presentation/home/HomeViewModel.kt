package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.BuildConfig
import io.apptolast.paparcar.data.mapper.toSpot
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.usecase.location.GetLocationInfoUseCase
import io.apptolast.paparcar.domain.usecase.location.ObserveLocationUpdatesUseCase
import io.apptolast.paparcar.domain.usecase.parking.ClearUserParkingUseCase
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
) : BaseViewModel<HomeState, HomeIntent, HomeEffect>() {

    init {
        observeUserParking()
            .onEach { session -> updateState { copy(userParking = session) } }
            .catch { e ->
                updateState { copy(error = e.message) }
                sendEffect(HomeEffect.ShowError(e.message ?: "Error desconocido"))
            }
            .launchIn(viewModelScope)

        // Chain 1: permission → location → geocoding.
        // Spots are NOT chained here so that they load without waiting for a GPS fix.
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
            }
            .catch { e ->
                updateState { copy(error = e.message) }
                sendEffect(HomeEffect.ShowError(e.message ?: "Error desconocido"))
            }
            .launchIn(viewModelScope)

        // Chain 2: spots — observed independently so they appear immediately without
        // waiting for a GPS fix. FirebaseDataSourceImpl fetches all spots anyway;
        // geographic filtering will be added via Geohash queries later.
        observeNearbySpots(
            location = GpsPoint(latitude = 0.0, longitude = 0.0, accuracy = 0f, timestamp = 0L, speed = 0f),
            radiusMeters = ObserveNearbySpotsUseCase.DEFAULT_SEARCH_RADIUS_METERS,
        )
            .onEach { spots -> updateState { copy(nearbySpots = spots) } }
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
            is HomeIntent.ReleaseParking -> releaseParking()
        }
    }

    private fun releaseParking() {
        val session = state.value.userParking ?: return
        viewModelScope.launch {
            clearUserParking()
            reportSpotReleased(session.toSpot()).onFailure { e ->
                sendEffect(HomeEffect.ShowError(e.message ?: "Error al liberar la plaza"))
            }
        }
    }

    private fun reportTestSpot() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            val timestamp = Clock.System.now().toEpochMilliseconds()
            val spotId = "${DEBUG_USER_ID}_$timestamp"
            val testSpot = Spot(
                id = spotId,
                location = GpsPoint(
                    latitude = DEBUG_LATITUDE,
                    longitude = DEBUG_LONGITUDE,
                    accuracy = 10f,
                    timestamp = timestamp,
                    speed = 0f,
                ),
                reportedBy = DEBUG_USER_ID,
                isActive = true,
            )
            reportSpotReleased(testSpot)
                .onSuccess { sendEffect(HomeEffect.ShowSuccess("Spot de prueba reportado con éxito")) }
                .onFailure { sendEffect(HomeEffect.ShowError("Error al reportar spot de prueba: ${it.message}")) }
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

    companion object {
        private const val DEBUG_USER_ID = "user-123"
        private const val DEBUG_LATITUDE = 40.416775
        private const val DEBUG_LONGITUDE = -3.703790
    }
}

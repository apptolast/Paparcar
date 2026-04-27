package io.apptolast.paparcar.presentation.home

import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.isDebugBuild
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.connectivity.ConnectivityObserver
import io.apptolast.paparcar.domain.connectivity.ConnectivityStatus
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.domain.util.haversineMeters
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.usecase.location.GetLocationInfoUseCase
import io.apptolast.paparcar.domain.usecase.location.SearchAddressUseCase
import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.ReleaseActiveParkingSessionUseCase
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.usecase.spot.SendSpotSignalUseCase
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)
class HomeViewModel(
    private val permissionManager: PermissionManager,
    private val locationDataSource: LocationDataSource,
    private val observeNearbySpots: ObserveNearbySpotsUseCase,
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val activityRecognitionManager: ActivityRecognitionManager,
    private val userParkingRepository: UserParkingRepository,
    private val releaseSession: ReleaseActiveParkingSessionUseCase,
    private val getLocationInfo: GetLocationInfoUseCase,
    private val confirmParking: ConfirmParkingUseCase,
    private val searchAddress: SearchAddressUseCase,
    private val appPreferences: AppPreferences,
    private val sendSpotSignal: SendSpotSignalUseCase,
    private val connectivityObserver: ConnectivityObserver,
) : BaseViewModel<HomeState, HomeIntent, HomeEffect>() {

    private val searchQueryFlow = MutableStateFlow("")

    // Increments on each Offline → Online transition so combine() with the
    // permission stream forces flatMapLatest to rebuild the Firestore listener
    // — a fresh `observeNearbySpots` call after the network drops cached data.
    private val reconnectTick = MutableStateFlow(0)

    init {
        updateState { copy(mapType = appPreferences.defaultMapType.toMapType()) }

        var previousConnectivity = connectivityObserver.status.value
        connectivityObserver.status
            .onEach { current ->
                if (previousConnectivity == ConnectivityStatus.Offline &&
                    current == ConnectivityStatus.Online
                ) {
                    reconnectTick.value = reconnectTick.value + 1
                }
                previousConnectivity = current
            }
            .launchIn(viewModelScope)

        searchQueryFlow
            .debounce(SEARCH_DEBOUNCE_MS)
            .filter { it.isNotBlank() }
            .onEach { query ->
                updateState { copy(isSearching = true) }
                searchAddress(query)
                    .onSuccess { results -> updateState { copy(searchResults = results, isSearching = false) } }
                    .onFailure { updateState { copy(searchResults = emptyList(), isSearching = false) } }
            }
            .catch { /* best-effort; debounce errors don't affect the rest of the chain */ }
            .launchIn(viewModelScope)

        userParkingRepository.observeActiveSession()
            .onEach { session -> updateState { copy(userParking = session) } }
            .catch { e ->
                sendEffect(HomeEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
            }
            .launchIn(viewModelScope)

        combine(permissionManager.permissionState, reconnectTick) { perm, _ -> perm }
            .flatMapLatest { permissionState ->
                updateState { copy(allPermissionsGranted = permissionState.allPermissionsGranted) }
                if (permissionState.allPermissionsGranted) {
                    // registerTransitions() is best-effort: if Play Services are unavailable or
                    // the permission is revoked between recompositions, the GPS + spots chain
                    // must not die. Parking detection degrades gracefully to GPS-only mode.
                    runCatching { activityRecognitionManager.registerTransitions() }
                        .onFailure { e -> PaparcarLogger.w(TAG, "AR registration failed — GPS-only mode", e) }
                    locationDataSource.observeBalancedLocation()
                } else {
                    updateState { copy(nearbySpots = emptyList()) }
                    emptyFlow()
                }
            }
            .onStart { updateState { copy(isLoading = true) } }
            .onEach { userLocation ->
                updateState { copy(isLoading = false, userGpsPoint = userLocation) }
                geocodeUserLocation(userLocation.latitude, userLocation.longitude)
                if (state.value.cameraLocationInfo == null) {
                    geocodeCameraLocation(userLocation.latitude, userLocation.longitude)
                }
            }
            // Re-subscribe to Firestore only when the user moves more than the threshold.
            // Without this, every GPS fix (every ~3–5 s) opens a new Firestore listener.
            .distinctUntilChanged { old, new ->
                haversineMeters(
                    old.latitude, old.longitude,
                    new.latitude, new.longitude,
                ) < SPOT_RESUBSCRIBE_THRESHOLD_METERS
            }
            .flatMapLatest { userLocation ->
                observeNearbySpots(
                    userLocation,
                    ObserveNearbySpotsUseCase.DEFAULT_SEARCH_RADIUS_METERS,
                ).catch { e ->
                    // Firebase error (permissions, network, format) — show it but keep GPS chain alive
                    sendEffect(HomeEffect.ShowError(PaparcarError.Network.Unknown(e.message ?: "")))
                    emit(emptyList())
                }
            }
            .onEach { spots ->
                updateState {
                    val cur = selectedItemId
                    copy(
                        nearbySpots = spots,
                        // Keep parking selection or a spot that still exists; clear otherwise.
                        selectedItemId = if (cur != HomeState.PARKING_ITEM_ID && spots.none { it.id == cur }) null else cur,
                    )
                }
            }
            .catch { e ->
                // GPS/permissions chain error
                sendEffect(HomeEffect.ShowError(PaparcarError.Location.Unknown(e.message ?: "")))
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

            is HomeIntent.OpenHistory -> sendEffect(HomeEffect.NavigateToHistory)
            is HomeIntent.ReportTestSpot -> reportTestSpot()
            is HomeIntent.ReleaseParking -> releaseParking(intent.lat, intent.lon)
            is HomeIntent.SelectItem -> updateState { copy(selectedItemId = intent.itemId) }
            is HomeIntent.ManualPark -> manualPark()
            is HomeIntent.CameraPositionChanged -> geocodeCameraLocation(intent.lat, intent.lon)
            is HomeIntent.SearchQueryChanged -> handleSearchQueryChanged(intent.query)
            is HomeIntent.SelectSearchResult -> {
                updateState { copy(searchQuery = "", searchResults = emptyList(), isSearchActive = false, isSearching = false) }
                geocodeCameraLocation(intent.result.lat, intent.result.lon)
            }
            is HomeIntent.ClearSearch -> updateState { copy(searchQuery = "", searchResults = emptyList(), isSearchActive = false, isSearching = false) }
            is HomeIntent.SetMapType -> setMapType(intent.type)
            is HomeIntent.ShowParkingConfirmation -> updateState { copy(pendingParkingGps = intent.gps) }
            is HomeIntent.ConfirmDetectedParking -> confirmDetectedParking()
            is HomeIntent.DismissConfirmation -> updateState { copy(pendingParkingGps = null) }
            is HomeIntent.SetSizeFilter -> updateState { copy(sizeFilter = intent.size) }
            is HomeIntent.SendSpotSignal -> handleSpotSignal(intent.spotId, intent.accepted)
        }
    }

    private fun releaseParking(lat: Double, lon: Double) {
        viewModelScope.launch {
            releaseSession(lat, lon, state.value.userParking)
                .onFailure { e ->
                    sendEffect(HomeEffect.ShowError(PaparcarError.Database.WriteError(e.message ?: "")))
                    return@launch
                }
            updateState { copy(selectedItemId = null) }
            sendEffect(HomeEffect.SpotReported)
        }
    }

    private fun setMapType(type: MapType) {
        if (state.value.mapType == type) return
        appPreferences.setDefaultMapType(type.toPreferenceString())
        updateState { copy(mapType = type) }
    }

    private fun reportTestSpot() {
        if (!isDebugBuild) return
        viewModelScope.launch {
            val spotId = "${DEBUG_USER_ID}_${Clock.System.now().toEpochMilliseconds()}"
            reportSpotReleased(DEBUG_LATITUDE, DEBUG_LONGITUDE, spotId)
            sendEffect(HomeEffect.TestSpotSent)
        }
    }

    private fun manualPark() {
        val gps = state.value.userGpsPoint
        if (gps == null) {
            sendEffect(HomeEffect.ShowError(PaparcarError.Location.ProviderDisabled))
            return
        }
        if (connectivityObserver.status.value == ConnectivityStatus.Offline) {
            sendEffect(HomeEffect.OfflineActionBlocked)
            return
        }
        viewModelScope.launch {
            confirmParking(gps, 1.0f, SpotType.MANUAL_REPORT)
                .onFailure { sendEffect(HomeEffect.ShowError(PaparcarError.Parking.SaveFailed)) }
        }
    }

    private fun confirmDetectedParking() {
        val gps = state.value.pendingParkingGps ?: return
        updateState { copy(pendingParkingGps = null) }
        viewModelScope.launch {
            confirmParking(gps, 1.0f, SpotType.AUTO_DETECTED)
                .onFailure { sendEffect(HomeEffect.ShowError(PaparcarError.Parking.SaveFailed)) }
        }
    }

    private fun handleSearchQueryChanged(query: String) {
        updateState { copy(searchQuery = query, isSearchActive = true) }
        if (query.isBlank()) {
            updateState { copy(searchResults = emptyList(), isSearching = false) }
        }
        searchQueryFlow.value = query
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
            delay(GEOCODE_DEBOUNCE_MS)
            getLocationInfo(lat, lon)
                .onStart { updateState { copy(isCameraGeocoding = true) } }
                .onCompletion { cause ->
                    if (cause !is CancellationException) updateState { copy(isCameraGeocoding = false) }
                }
                .catch { /* best-effort; ignore errors */ }
                .collect { info -> updateState { copy(cameraLocationInfo = info) } }
        }
    }

    private fun handleSpotSignal(spotId: String, accepted: Boolean) {
        viewModelScope.launch {
            sendSpotSignal(spotId, accepted)
                .onSuccess { sendEffect(HomeEffect.SpotSignalSent) }
                .onFailure { sendEffect(HomeEffect.ShowError(PaparcarError.Network.Unknown(it.message ?: ""))) }
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
        const val SEARCH_DEBOUNCE_MS = 300L
        const val GEOCODE_DEBOUNCE_MS = 600L
        /** Minimum displacement (metres) to trigger a new Firestore spot subscription. */
        const val SPOT_RESUBSCRIBE_THRESHOLD_METERS = 100.0
        const val DEBUG_USER_ID = "user-123"
        const val DEBUG_LATITUDE = 40.416775
        const val DEBUG_LONGITUDE = -3.703790
        const val MAP_TYPE_NORMAL = "NORMAL"
        const val MAP_TYPE_SATELLITE = "SATELLITE"
        const val MAP_TYPE_TERRAIN = "TERRAIN"
    }

    private fun String.toMapType(): MapType = when (this) {
        MAP_TYPE_SATELLITE -> MapType.SATELLITE
        MAP_TYPE_TERRAIN -> MapType.TERRAIN
        else -> MapType.NORMAL
    }

    private fun MapType.toPreferenceString(): String = when (this) {
        MapType.SATELLITE -> MAP_TYPE_SATELLITE
        MapType.TERRAIN -> MAP_TYPE_TERRAIN
        else -> MAP_TYPE_NORMAL
    }
}
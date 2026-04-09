package io.apptolast.paparcar.presentation.home

import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.isDebugBuild
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.usecase.location.GetLocationInfoUseCase
import io.apptolast.paparcar.domain.usecase.location.SearchAddressUseCase
import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
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
    private val getLocationInfo: GetLocationInfoUseCase,
    private val confirmParking: ConfirmParkingUseCase,
    private val searchAddress: SearchAddressUseCase,
) : BaseViewModel<HomeState, HomeIntent, HomeEffect>() {

    init {
        userParkingRepository.observeActiveSession()
            .onEach { session -> updateState { copy(userParking = session) } }
            .catch { e ->
                sendEffect(HomeEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
            }
            .launchIn(viewModelScope)

        permissionManager.permissionState
            .flatMapLatest { permissionState ->
                updateState { copy(allPermissionsGranted = permissionState.allPermissionsGranted) }
                if (permissionState.allPermissionsGranted) {
                    activityRecognitionManager.registerTransitions()
                    locationDataSource.observeBalancedLocation()
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
            is HomeIntent.ReportManualSpot -> reportManualSpot(intent.lat, intent.lon)
            is HomeIntent.CycleMapType -> cycleMapType()
            is HomeIntent.ShowParkingConfirmation -> updateState { copy(pendingParkingGps = intent.gps) }
            is HomeIntent.ConfirmDetectedParking -> confirmDetectedParking()
            is HomeIntent.DismissConfirmation -> updateState { copy(pendingParkingGps = null) }
            is HomeIntent.SetSizeFilter -> updateState { copy(sizeFilter = intent.size) }
        }
    }

    private fun releaseParking(lat: Double, lon: Double) {
        viewModelScope.launch {
            val parking = state.value.userParking
            val spotId = parking?.id ?: "manual_${Clock.System.now().toEpochMilliseconds()}"
            val spotType = parking?.spotType ?: SpotType.AUTO_DETECTED
            val confidence = parking?.detectionReliability ?: 1f
            val sizeCategory = parking?.sizeCategory
            // Schedule the WorkManager job BEFORE clearing so the report is durably
            // enqueued even if the ViewModel scope is cancelled mid-flight.
            // ReportSpotReleasedUseCase caps geocoding at 5 s, so this is fast.
            reportSpotReleased(lat, lon, spotId, spotType, confidence, sizeCategory)
            userParkingRepository.clearActive().onFailure { e ->
                sendEffect(HomeEffect.ShowError(PaparcarError.Database.WriteError(e.message ?: "")))
                return@launch
            }
            updateState { copy(selectedItemId = null) }
            sendEffect(HomeEffect.SpotReported)
        }
    }

    private fun cycleMapType() {
        val next = when (state.value.mapType) {
            MapType.NORMAL -> MapType.SATELLITE
            MapType.SATELLITE -> MapType.TERRAIN
            else -> MapType.NORMAL
        }
        updateState { copy(mapType = next) }
    }

    private fun reportManualSpot(lat: Double, lon: Double) {
        viewModelScope.launch {
            val spotId = "manual_${Clock.System.now().toEpochMilliseconds()}"
            reportSpotReleased(lat, lon, spotId, SpotType.MANUAL_REPORT, confidence = 1f)
            sendEffect(HomeEffect.ManualSpotReported)
        }
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
        viewModelScope.launch {
            confirmParking(gps, 1.0f, SpotType.MANUAL_REPORT)
        }
    }

    private fun confirmDetectedParking() {
        val gps = state.value.pendingParkingGps ?: return
        updateState { copy(pendingParkingGps = null) }
        viewModelScope.launch {
            confirmParking(gps, 1.0f, SpotType.AUTO_DETECTED)
        }
    }

    private var searchJob: Job? = null

    private fun handleSearchQueryChanged(query: String) {
        updateState { copy(searchQuery = query, isSearchActive = true) }
        searchJob?.cancel()
        if (query.isBlank()) {
            updateState { copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            updateState { copy(isSearching = true) }
            searchAddress(query)
                .onSuccess { results -> updateState { copy(searchResults = results, isSearching = false) } }
                .onFailure { updateState { copy(searchResults = emptyList(), isSearching = false) } }
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

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val GEOCODE_DEBOUNCE_MS = 600L
        const val DEBUG_USER_ID = "user-123"
        const val DEBUG_LATITUDE = 40.416775
        const val DEBUG_LONGITUDE = -3.703790
    }
}
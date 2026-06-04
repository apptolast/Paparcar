package io.apptolast.paparcar.presentation.home

import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.isDebugBuild
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.connectivity.ConnectivityObserver
import io.apptolast.paparcar.domain.connectivity.ConnectivityStatus
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.ZoneIcon
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.repository.ZoneRepository
import io.apptolast.paparcar.domain.usecase.location.GetLocationInfoUseCase
import io.apptolast.paparcar.domain.usecase.location.SearchAddressUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.ObserveParkedVehiclesUseCase
import io.apptolast.paparcar.domain.usecase.parking.ReleaseActiveParkingSessionUseCase
import io.apptolast.paparcar.domain.usecase.parking.UpdateParkingLocationUseCase
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.domain.usecase.spot.SendSpotSignalUseCase
import io.apptolast.paparcar.domain.event.MapFocusEventBus
import io.apptolast.paparcar.domain.usecase.zone.SaveZoneUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.domain.util.haversineMeters
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.launch
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)
class HomeViewModel(
    private val permissionManager: PermissionManager,
    private val locationDataSource: LocationDataSource,
    private val activityRecognitionManager: ActivityRecognitionManager,
    private val connectivityObserver: ConnectivityObserver,
    private val observeNearbySpots: ObserveNearbySpotsUseCase,
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val sendSpotSignal: SendSpotSignalUseCase,
    private val userParkingRepository: UserParkingRepository,
    private val confirmParking: ConfirmParkingUseCase,
    private val releaseSession: ReleaseActiveParkingSessionUseCase,
    private val observeParkedVehicles: ObserveParkedVehiclesUseCase,
    private val updateParkingLocation: UpdateParkingLocationUseCase,
    private val vehicleRepository: VehicleRepository,
    private val getLocationInfo: GetLocationInfoUseCase,
    private val searchAddress: SearchAddressUseCase,
    private val zoneRepository: ZoneRepository,
    private val saveZone: SaveZoneUseCase,
    private val appPreferences: AppPreferences,
    private val mapFocusEventBus: MapFocusEventBus,
) : BaseViewModel<HomeState, HomeIntent, HomeEffect>() {

    // ── Private flows ─────────────────────────────────────────────────────────

    private val searchQueryFlow = MutableStateFlow("")

    // Incremented on Offline → Online so the spot subscription rebuilds immediately
    // after connectivity is restored, even if the GPS position hasn't changed.
    private val reconnectTick = MutableStateFlow(0)

    // Centre used for spot queries. Seeded from GPS on first fix; updated when the
    // user pans the map past SPOT_CAMERA_PAN_THRESHOLD_METERS in Browse mode.
    private val spotQueryCenter = MutableStateFlow<GpsPoint?>(null)

    // ── Geocode jobs ──────────────────────────────────────────────────────────
    // cameraDebounceJob: cancelled on every camera move (drops rapid-pan spam).
    // cameraGeocoderJob: only cancelled when the debounce fires for a NEW location,
    //   so Phase 2 (slow Overpass call) survives brief camera animations.

    private var userGeocoderJob: Job? = null
    private var cameraDebounceJob: Job? = null
    private var cameraGeocoderJob: Job? = null
    private var cameraSettledJob: Job? = null

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        updateState { copy(mapType = appPreferences.defaultMapType.toMapType()) }
        subscribeConnectivity()
        subscribeSearchQuery()
        subscribeActiveSessions()
        subscribeParkedVehicles()
        subscribeZones()
        subscribeVehicles()
        subscribeGpsLocation()
        subscribeNearbySpots()
        subscribeMapFocusEvents()
    }

    override fun initState(): HomeState = HomeState()

    // ── Intent dispatch ───────────────────────────────────────────────────────

    override fun handleIntent(intent: HomeIntent) {
        when (intent) {
            // Map & navigation
            is HomeIntent.CameraPositionChanged -> onCameraPositionChanged(intent.lat, intent.lon)
            is HomeIntent.RecenterSpots -> onRecenterSpots()
            is HomeIntent.SetMapType -> setMapType(intent.type)

            // Spot interactions
            is HomeIntent.LoadNearbySpots -> {
                if (!permissionManager.permissionState.value.allPermissionsGranted) {
                    sendEffect(HomeEffect.RequestLocationPermission)
                }
            }
            is HomeIntent.SelectItem -> updateState { copy(selectedItemId = intent.itemId, selectedZoneId = null) }
            is HomeIntent.SetSizeFilter -> updateState { copy(sizeFilter = intent.size) }
            is HomeIntent.SendSpotSignal -> submitSpotSignal(intent.spotId, intent.accepted)

            // Reporting mode
            is HomeIntent.EnterReportMode -> updateState {
                copy(mode = HomeMode.Reporting, selectedItemId = null, pinCameraLat = intent.lat, pinCameraLon = intent.lon)
            }
            is HomeIntent.ExitReportMode -> updateState {
                copy(mode = HomeMode.Browse, pinCameraLat = null, pinCameraLon = null, isCameraMoving = false, reportingSize = null)
            }
            is HomeIntent.ConfirmReportSpot -> confirmReportSpot()
            is HomeIntent.SetReportingSize -> updateState {
                copy(reportingSize = if (reportingSize == intent.size) null else intent.size)
            }

            // Detection confirmation
            is HomeIntent.ShowParkingConfirmation -> updateState { copy(pendingParkingGps = intent.gps) }
            is HomeIntent.ConfirmDetectedParking -> confirmDetectedParking()
            is HomeIntent.DismissConfirmation -> updateState { copy(pendingParkingGps = null) }

            // Parking lifecycle
            is HomeIntent.ReleaseParking -> releaseParking(intent.lat, intent.lon, intent.publishSpot)
            is HomeIntent.EnterAddParkingMode -> updateState {
                copy(
                    mode = HomeMode.AddingParking,
                    selectedItemId = null,
                    pinCameraLat = intent.initialGps?.latitude,
                    pinCameraLon = intent.initialGps?.longitude,
                    editingParkingId = intent.editingParkingId,
                    addingParkingVehicleId = intent.targetVehicleId,
                )
            }
            is HomeIntent.ExitAddParkingMode -> updateState {
                copy(
                    mode = HomeMode.Browse,
                    pinCameraLat = null,
                    pinCameraLon = null,
                    isCameraMoving = false,
                    editingParkingId = null,
                    addingParkingVehicleId = null,
                )
            }
            is HomeIntent.ConfirmAddParking -> confirmAddParking()

            // Zone management
            is HomeIntent.EnterAddZoneMode -> updateState {
                copy(
                    mode = HomeMode.AddingZone,
                    selectedItemId = null,
                    selectedZoneId = null,
                    pinCameraLat = intent.lat,
                    pinCameraLon = intent.lon,
                    isCameraMoving = false,
                    addingZoneName = "",
                    addingZoneIconKey = ZoneIcon.DEFAULT,
                    editingZoneId = null,
                )
            }
            is HomeIntent.ExitAddZoneMode -> updateState {
                copy(
                    mode = HomeMode.Browse,
                    selectedZoneId = null,
                    pinCameraLat = null,
                    pinCameraLon = null,
                    isCameraMoving = false,
                    addingZoneName = "",
                    addingZoneIconKey = ZoneIcon.DEFAULT,
                    editingZoneId = null,
                )
            }
            is HomeIntent.ConfirmAddZone -> confirmAddZone()
            is HomeIntent.UpdateAddingZoneName -> updateState { copy(addingZoneName = intent.name) }
            is HomeIntent.UpdateAddingZoneIcon -> updateState { copy(addingZoneIconKey = intent.iconKey) }
            is HomeIntent.SetZoneRadius -> updateState { copy(addingZoneRadius = intent.radius) }
            is HomeIntent.SetZoneIsPrivate -> updateState { copy(addingZoneIsPrivate = intent.isPrivate) }
            is HomeIntent.SelectZone -> selectZone(intent.zoneId)
            is HomeIntent.DismissZone -> updateState { copy(selectedZoneId = null) }
            is HomeIntent.DeleteZone -> viewModelScope.launch {
                zoneRepository.deleteZone(intent.zoneId)
                    .onFailure { e -> sendEffect(HomeEffect.ShowError(PaparcarError.Database.WriteError(e.message ?: ""))) }
            }
            is HomeIntent.EnterEditZoneMode -> enterEditZoneMode(intent.zoneId)

            // Search
            is HomeIntent.SearchQueryChanged -> onSearchQueryChanged(intent.query)
            is HomeIntent.SelectSearchResult -> {
                updateState { copy(searchQuery = "", searchResults = emptyList(), isSearchActive = false, isSearching = false) }
                geocodeCameraLocation(intent.result.lat, intent.result.lon)
            }
            is HomeIntent.ClearSearch -> updateState {
                copy(searchQuery = "", searchResults = emptyList(), isSearchActive = false, isSearching = false)
            }

            // Debug
            is HomeIntent.ReportTestSpot -> reportTestSpot()
        }
    }

    // ── Subscriptions (launched in init) ─────────────────────────────────────

    private fun subscribeConnectivity() {
        var previous = connectivityObserver.status.value
        connectivityObserver.status
            .onEach { current ->
                if (previous == ConnectivityStatus.Offline && current == ConnectivityStatus.Online) {
                    reconnectTick.value++
                }
                previous = current
            }
            .launchIn(viewModelScope)
    }

    private fun subscribeSearchQuery() {
        searchQueryFlow
            .debounce(SEARCH_DEBOUNCE_MS)
            .filter { it.isNotBlank() }
            .onEach { query ->
                updateState { copy(isSearching = true) }
                searchAddress(query)
                    .onSuccess { results -> updateState { copy(searchResults = results, isSearching = false) } }
                    .onFailure { updateState { copy(searchResults = emptyList(), isSearching = false) } }
            }
            .catch { e -> PaparcarLogger.w(TAG, "Search query flow error", e) }
            .launchIn(viewModelScope)
    }

    private fun subscribeActiveSessions() {
        userParkingRepository.observeActiveSessions()
            .onEach { sessions -> updateState { copy(activeSessions = sessions) } }
            .catch { e -> sendEffect(HomeEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: ""))) }
            .launchIn(viewModelScope)
    }

    private fun subscribeParkedVehicles() {
        observeParkedVehicles()
            .onEach { views -> updateState { copy(parkedVehicles = views) } }
            .catch { e -> PaparcarLogger.e(TAG, "subscribeParkedVehicles error", e) }
            .launchIn(viewModelScope)
    }

    private fun subscribeZones() {
        zoneRepository.observeZones()
            .onEach { zones -> updateState { copy(zones = zones) } }
            .catch { e -> PaparcarLogger.e(TAG, "subscribeZones error", e) }
            .launchIn(viewModelScope)
    }

    private fun subscribeVehicles() {
        vehicleRepository.observeVehicles()
            .onEach { vehicles -> updateState { copy(vehicles = vehicles) } }
            .catch { e -> PaparcarLogger.e(TAG, "subscribeVehicles error", e) }
            .launchIn(viewModelScope)
    }

    private fun subscribeGpsLocation() {
        combine(permissionManager.permissionState, reconnectTick) { perm, _ -> perm }
            .flatMapLatest { perm ->
                updateState { copy(allPermissionsGranted = perm.allPermissionsGranted) }
                if (perm.allPermissionsGranted) {
                    // Best-effort: AR failure degrades gracefully to GPS-only detection.
                    runCatching { activityRecognitionManager.registerTransitions() }
                        .onFailure { e -> PaparcarLogger.w(TAG, "AR registration failed — GPS-only mode", e) }
                    locationDataSource.observeBalancedLocation()
                } else {
                    emptyFlow()
                }
            }
            .onStart { updateState { copy(isLoading = true) } }
            .onEach { location ->
                updateState { copy(isLoading = false, userGpsPoint = location) }
                geocodeUserLocation(location.latitude, location.longitude)
                if (state.value.cameraLocationInfo == null) {
                    geocodeCameraLocation(location.latitude, location.longitude)
                }
                // Keep query center in sync with GPS while user hasn't panned away.
                if (state.value.isSpotQueryCenteredOnUser) {
                    spotQueryCenter.value = location
                }
            }
            .catch { e -> sendEffect(HomeEffect.ShowError(PaparcarError.Location.Unknown(e.message ?: ""))) }
            .launchIn(viewModelScope)
    }

    private fun subscribeNearbySpots() {
        combine(permissionManager.permissionState, spotQueryCenter, reconnectTick) { perm, center, _ ->
            if (perm.allPermissionsGranted) center else null
        }
            .distinctUntilChanged { old, new -> old.closeEnoughTo(new) }
            .flatMapLatest { center ->
                if (center == null) {
                    updateState { copy(nearbySpots = emptyList()) }
                    emptyFlow()
                } else {
                    observeNearbySpots(center, ObserveNearbySpotsUseCase.DEFAULT_SEARCH_RADIUS_METERS)
                        .catch { e ->
                            sendEffect(HomeEffect.ShowError(PaparcarError.Network.Unknown(e.message ?: "")))
                            emit(emptyList())
                        }
                }
            }
            .onEach { spots ->
                updateState {
                    val cur = selectedItemId
                    copy(
                        nearbySpots = spots,
                        selectedItemId = if (cur == null ||
                            activeSessions.any { it.id == cur } ||
                            spots.any { it.id == cur }) cur else null,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    // ── Intent handlers ───────────────────────────────────────────────────────

    private fun onCameraPositionChanged(lat: Double, lon: Double) {
        if (state.value.mode !is HomeMode.Browse) {
            updateState { copy(pinCameraLat = lat, pinCameraLon = lon, isCameraMoving = true) }
            cameraSettledJob?.cancel()
            cameraSettledJob = viewModelScope.launch {
                kotlinx.coroutines.delay(CAMERA_SETTLED_MS)
                updateState { copy(isCameraMoving = false) }
            }
        } else {
            val current = spotQueryCenter.value
            if (current != null && haversineMeters(current.latitude, current.longitude, lat, lon) > SPOT_CAMERA_PAN_THRESHOLD_METERS) {
                spotQueryCenter.value = GpsPoint(
                    latitude = lat,
                    longitude = lon,
                    accuracy = 0f,
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                    speed = 0f,
                )
                updateState { copy(isSpotQueryCenteredOnUser = false) }
            }
        }
        geocodeCameraLocation(lat, lon)
    }

    private fun onRecenterSpots() {
        val gps = state.value.userGpsPoint ?: return
        spotQueryCenter.value = gps
        updateState { copy(isSpotQueryCenteredOnUser = true) }
        sendEffect(HomeEffect.MoveCameraTo(gps.latitude, gps.longitude))
    }

    private fun onSearchQueryChanged(query: String) {
        updateState { copy(searchQuery = query, isSearchActive = true) }
        if (query.isBlank()) updateState { copy(searchResults = emptyList(), isSearching = false) }
        searchQueryFlow.value = query
    }

    private fun confirmReportSpot() {
        val current = state.value
        if (current.mode !is HomeMode.Reporting || current.isReporting) return
        val lat = current.pinCameraLat ?: run {
            sendEffect(HomeEffect.ShowError(PaparcarError.Location.ProviderDisabled)); return
        }
        val lon = current.pinCameraLon ?: run {
            sendEffect(HomeEffect.ShowError(PaparcarError.Location.ProviderDisabled)); return
        }
        updateState { copy(isReporting = true) }
        viewModelScope.launch {
            val spotId = "manual_${Clock.System.now().toEpochMilliseconds()}"
            reportSpotReleased(lat, lon, spotId, SpotType.MANUAL_REPORT, confidence = 1f, sizeCategory = current.reportingSize)
            updateState { copy(isReporting = false, mode = HomeMode.Browse, pinCameraLat = null, pinCameraLon = null, reportingSize = null) }
            sendEffect(HomeEffect.SpotReported)
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

    private fun releaseParking(lat: Double, lon: Double, publishSpot: Boolean) {
        val target = state.value.selectedSession ?: state.value.userParking
        updateState { copy(isReleasingParking = true) }
        viewModelScope.launch {
            releaseSession(lat, lon, target, publishSpot)
                .onFailure { e ->
                    updateState { copy(isReleasingParking = false) }
                    sendEffect(HomeEffect.ShowError(PaparcarError.Database.WriteError(e.message ?: "")))
                    return@launch
                }
            updateState { copy(selectedItemId = null, isReleasingParking = false) }
            if (publishSpot) sendEffect(HomeEffect.SpotReported)
        }
    }

    private fun confirmAddParking() {
        val current = state.value
        if (current.mode !is HomeMode.AddingParking || current.isSavingParking) return
        val lat = current.pinCameraLat ?: run {
            sendEffect(HomeEffect.ShowError(PaparcarError.Location.ProviderDisabled)); return
        }
        val lon = current.pinCameraLon ?: run {
            sendEffect(HomeEffect.ShowError(PaparcarError.Location.ProviderDisabled)); return
        }
        if (connectivityObserver.status.value == ConnectivityStatus.Offline) {
            sendEffect(HomeEffect.OfflineActionBlocked); return
        }
        val newGps = GpsPoint(
            latitude = lat,
            longitude = lon,
            accuracy = current.userGpsPoint?.accuracy ?: 0f,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            speed = 0f,
        )
        updateState { copy(isSavingParking = true) }
        viewModelScope.launch {
            val editingId = current.editingParkingId
            val result = if (editingId != null) {
                updateParkingLocation(editingId, newGps).map { Unit }
            } else {
                confirmParking(newGps, 1.0f, SpotType.MANUAL_REPORT, vehicleId = current.addingParkingVehicleId).map { Unit }
            }
            result.onFailure { sendEffect(HomeEffect.ShowError(PaparcarError.Parking.SaveFailed)) }
            updateState {
                copy(
                    isSavingParking = false,
                    mode = HomeMode.Browse,
                    pinCameraLat = null,
                    pinCameraLon = null,
                    editingParkingId = null,
                    addingParkingVehicleId = null,
                )
            }
        }
    }

    private fun confirmAddZone() {
        val current = state.value
        if (current.mode !is HomeMode.AddingZone || current.isSavingZone) return
        val lat = current.pinCameraLat ?: run {
            sendEffect(HomeEffect.ShowError(PaparcarError.Location.ProviderDisabled)); return
        }
        val lon = current.pinCameraLon ?: run {
            sendEffect(HomeEffect.ShowError(PaparcarError.Location.ProviderDisabled)); return
        }
        val name = current.addingZoneName.trim().ifEmpty { return }
        updateState { copy(isSavingZone = true) }
        viewModelScope.launch {
            val editingId = current.editingZoneId
            if (editingId != null) {
                current.zones.find { it.id == editingId }?.let { existing ->
                    zoneRepository.saveZone(existing.copy(name = name.trim(), lat = lat, lon = lon, iconKey = current.addingZoneIconKey))
                        .onFailure { e -> sendEffect(HomeEffect.ShowError(PaparcarError.Database.WriteError(e.message ?: ""))) }
                }
            } else {
                saveZone(name = name, lat = lat, lon = lon, iconKey = current.addingZoneIconKey)
                    .onFailure { e -> sendEffect(HomeEffect.ShowError(PaparcarError.Database.WriteError(e.message ?: ""))) }
            }
            updateState {
                copy(
                    isSavingZone = false,
                    mode = HomeMode.Browse,
                    pinCameraLat = null,
                    pinCameraLon = null,
                    addingZoneName = "",
                    addingZoneIconKey = ZoneIcon.DEFAULT,
                    editingZoneId = null,
                )
            }
            sendEffect(HomeEffect.ZoneSaved)
        }
    }

    private fun enterEditZoneMode(zoneId: String) {
        val zone = state.value.zones.find { it.id == zoneId } ?: return
        updateState {
            copy(
                mode = HomeMode.AddingZone,
                selectedItemId = null,
                selectedZoneId = null,
                pinCameraLat = zone.lat,
                pinCameraLon = zone.lon,
                addingZoneName = zone.name,
                addingZoneIconKey = zone.iconKey,
                editingZoneId = zoneId,
            )
        }
        sendEffect(HomeEffect.MoveCameraTo(zone.lat, zone.lon))
    }

    private fun selectZone(zoneId: String) {
        val zone = state.value.zones.find { it.id == zoneId } ?: return
        updateState { copy(selectedZoneId = zoneId, selectedItemId = null) }
        sendEffect(HomeEffect.MoveCameraTo(zone.lat, zone.lon))
    }

    private fun submitSpotSignal(spotId: String, accepted: Boolean) {
        viewModelScope.launch {
            sendSpotSignal(spotId, accepted)
                .onSuccess { sendEffect(HomeEffect.SpotSignalSent) }
                .onFailure { sendEffect(HomeEffect.ShowError(PaparcarError.Network.Unknown(it.message ?: ""))) }
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
            val spotId = "user-123_${Clock.System.now().toEpochMilliseconds()}"
            reportSpotReleased(40.416775, -3.703790, spotId)
            sendEffect(HomeEffect.TestSpotSent)
        }
    }

    // ── Geocoding ─────────────────────────────────────────────────────────────

    private fun geocodeUserLocation(lat: Double, lon: Double) {
        userGeocoderJob?.cancel()
        userGeocoderJob = viewModelScope.launch {
            getLocationInfo(lat, lon)
                .catch { e -> PaparcarLogger.w(TAG, "geocodeUserLocation error", e) }
                .collect { info -> updateState { copy(userLocationInfo = info) } }
        }
    }

    private fun geocodeCameraLocation(lat: Double, lon: Double) {
        // Cancel only the debounce — NOT the geocoding job — so Phase 2 (Overpass)
        // survives rapid camera animations and is only killed when the camera truly
        // settles at a different location.
        cameraDebounceJob?.cancel()
        updateState { copy(isCameraGeocoding = true) }
        cameraDebounceJob = viewModelScope.launch {
            delay(GEOCODE_DEBOUNCE_MS)
            cameraGeocoderJob?.cancel()
            cameraGeocoderJob = viewModelScope.launch {
                var addressReceived = false
                getLocationInfo(lat, lon)
                    .onCompletion { cause -> if (cause !is CancellationException) updateState { copy(isCameraGeocoding = false) } }
                    .catch { updateState { copy(isCameraGeocoding = false) } }
                    .collect { info ->
                        updateState { copy(cameraLocationInfo = info) }
                        // Stop shimmer as soon as Phase 1 address arrives — Phase 2 (POI)
                        // will quietly update cameraLocationInfo when it finishes.
                        if (!addressReceived) {
                            addressReceived = true
                            updateState { copy(isCameraGeocoding = false) }
                        }
                    }
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCleared() {
        searchQueryFlow.value = ""
        reconnectTick.value = 0
        spotQueryCenter.value = null
        super.onCleared()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // Two nullable GpsPoints are "close enough" if both are null, or both non-null
    // and within SPOT_RESUBSCRIBE_THRESHOLD_METERS of each other.
    private fun GpsPoint?.closeEnoughTo(other: GpsPoint?): Boolean {
        if (this == null && other == null) return true
        if (this == null || other == null) return false
        return haversineMeters(latitude, longitude, other.latitude, other.longitude) < SPOT_RESUBSCRIBE_THRESHOLD_METERS
    }

    private fun String.toMapType(): MapType = when (this) {
        MAP_TYPE_SATELLITE -> MapType.SATELLITE
        MAP_TYPE_HYBRID -> MapType.HYBRID
        MAP_TYPE_TERRAIN -> MapType.TERRAIN
        else -> MapType.TERRAIN
    }

    private fun MapType.toPreferenceString(): String = when (this) {
        MapType.SATELLITE -> MAP_TYPE_SATELLITE
        MapType.HYBRID -> MAP_TYPE_HYBRID
        else -> MAP_TYPE_TERRAIN
    }

    // ── Notification focus ────────────────────────────────────────────────────

    private fun subscribeMapFocusEvents() {
        mapFocusEventBus.events
            .onEach { (lat, lon) -> sendEffect(HomeEffect.MoveCameraTo(lat, lon)) }
            .launchIn(viewModelScope)
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private companion object {
        const val TAG = "HomeViewModel"

        // Timing
        const val SEARCH_DEBOUNCE_MS = 300L
        const val GEOCODE_DEBOUNCE_MS = 600L
        const val CAMERA_SETTLED_MS = 280L

        // Distance thresholds
        // Both at 300m so GPS drift alone never triggers a Firestore reconnect —
        // only a genuine camera pan or location jump does.
        const val SPOT_RESUBSCRIBE_THRESHOLD_METERS = 300.0
        const val SPOT_CAMERA_PAN_THRESHOLD_METERS = 300.0

        // Map type preference strings
        const val MAP_TYPE_TERRAIN = "TERRAIN"
        const val MAP_TYPE_SATELLITE = "SATELLITE"
        const val MAP_TYPE_HYBRID = "HYBRID"

    }
}

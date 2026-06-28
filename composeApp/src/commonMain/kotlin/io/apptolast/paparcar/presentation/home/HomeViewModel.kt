package io.apptolast.paparcar.presentation.home

import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.isDebugBuild
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.connectivity.ConnectivityObserver
import io.apptolast.paparcar.domain.connectivity.ConnectivityStatus
import io.apptolast.paparcar.domain.detection.ManualParkingDetection
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.detection.ParkingStrategy
import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.location.UserLocationUi
import io.apptolast.paparcar.domain.model.DetectionReadiness
import io.apptolast.paparcar.domain.model.DrivingPuck
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.model.ZoneIcon
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.repository.ZoneRepository
import io.apptolast.paparcar.domain.usecase.detection.ObserveDetectionReadinessUseCase
import io.apptolast.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class, kotlin.time.ExperimentalTime::class)
class HomeViewModel(
    private val permissionManager: PermissionManager,
    private val locationDataSource: LocationDataSource,
    private val activityRecognitionManager: ActivityRecognitionManager,
    private val connectivityObserver: ConnectivityObserver,
    private val observeNearbySpots: ObserveNearbySpotsUseCase,
    private val observeDetectionReadiness: ObserveDetectionReadinessUseCase,
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val sendSpotSignal: SendSpotSignalUseCase,
    private val userParkingRepository: UserParkingRepository,
    private val confirmParking: ConfirmParkingUseCase,
    private val releaseSession: ReleaseActiveParkingSessionUseCase,
    private val observeParkedVehicles: ObserveParkedVehiclesUseCase,
    private val updateParkingLocation: UpdateParkingLocationUseCase,
    private val vehicleRepository: VehicleRepository,
    private val getAddressAndPlace: GetAddressAndPlaceUseCase,
    private val searchAddress: SearchAddressUseCase,
    private val zoneRepository: ZoneRepository,
    private val saveZone: SaveZoneUseCase,
    private val appPreferences: AppPreferences,
    private val mapFocusEventBus: MapFocusEventBus,
    private val notificationPort: AppNotificationManager,
    private val manualParkingDetection: ManualParkingDetection,
) : BaseViewModel<HomeState, HomeIntent, HomeEffect>() {

    // ── Private flows ─────────────────────────────────────────────────────────

    private val searchQueryFlow = MutableStateFlow("")

    // Incremented on Offline → Online so the spot subscription rebuilds immediately
    // after connectivity is restored, even if the GPS position hasn't changed.
    private val reconnectTick = MutableStateFlow(0)

    // Centre used for spot queries. Seeded from GPS on first fix; updated when the
    // user pans the map past SPOT_CAMERA_PAN_THRESHOLD_METERS in Browse mode.
    private val spotQueryCenter = MutableStateFlow<GpsPoint?>(null)

    // ── Geocode controller ────────────────────────────────────────────────────
    // Encapsulates user + camera geocoding flows (debounce, Phase-2 survival
    // semantics, in-flight flag invariants). [F3] [BUG-GEOCODE-STUCK-001]

    private val geocoder = HomeGeocodingController(
        scope = viewModelScope,
        getAddressAndPlace = getAddressAndPlace,
        onUserAddress = { info -> updateState { copy(userAddressAndPlace = info) } },
        onCameraAddress = { info -> updateState { copy(cameraAddressAndPlace = info) } },
        onCameraGeocodingChange = { active -> updateState { copy(isCameraGeocoding = active) } },
        debounceMs = GEOCODE_DEBOUNCE_MS,
    )

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
        subscribeDetectionReadiness()
        subscribeDrivingPuck()
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
                if (!permissionManager.permissionState.value.hasCorePermissions) {
                    sendEffect(HomeEffect.RequestLocationPermission)
                }
            }
            is HomeIntent.SelectItem -> updateState {
                clearedModeFields().copy(selectedItemId = intent.itemId)
            }
            is HomeIntent.SetSizeFilter -> updateState { copy(sizeFilter = intent.size) }
            is HomeIntent.SendSpotSignal -> submitSpotSignal(intent.spotId, intent.accepted)

            // Reporting mode
            is HomeIntent.EnterReportMode -> updateState {
                clearedModeFields().copy(
                    mode = HomeMode.Reporting,
                    pinCameraLat = intent.lat,
                    pinCameraLon = intent.lon,
                )
            }
            is HomeIntent.ExitReportMode -> updateState { clearedModeFields() }
            is HomeIntent.ConfirmReportSpot -> confirmReportSpot()
            is HomeIntent.SetReportingSize -> updateState {
                copy(reportingSize = if (reportingSize == intent.size) null else intent.size)
            }

            // Detection confirmation
            is HomeIntent.ShowParkingConfirmation -> updateState { copy(pendingParkingGps = intent.gps) }
            is HomeIntent.ConfirmDetectedParking -> confirmDetectedParking()
            is HomeIntent.DismissConfirmation -> updateState { copy(pendingParkingGps = null) }
            // [DET-G-01b] "I'm driving" — start tracking now; the readiness flow flips to Monitoring
            // (the ephemeral pill) as the service marks itself running, which is the user's feedback.
            is HomeIntent.StartDrivingDetection -> manualParkingDetection.start()

            // Parking lifecycle
            is HomeIntent.ReleaseParking -> releaseParking(intent.lat, intent.lon, intent.publishSpot)
            is HomeIntent.EnterAddParkingMode -> updateState {
                clearedModeFields().copy(
                    mode = HomeMode.AddingParking,
                    pinCameraLat = intent.initialGps?.latitude,
                    pinCameraLon = intent.initialGps?.longitude,
                    editingParkingId = intent.editingParkingId,
                    addingParkingVehicleId = intent.targetVehicleId,
                )
            }
            is HomeIntent.ExitAddParkingMode -> updateState { clearedModeFields() }
            is HomeIntent.ConfirmAddParking -> confirmAddParking()

            // Zone management
            is HomeIntent.EnterAddZoneMode -> updateState {
                clearedModeFields().copy(
                    mode = HomeMode.AddingZone,
                    pinCameraLat = intent.lat,
                    pinCameraLon = intent.lon,
                )
            }
            is HomeIntent.ExitAddZoneMode -> updateState { clearedModeFields() }
            is HomeIntent.ConfirmAddZone -> confirmAddZone()
            is HomeIntent.UpdateAddingZoneName -> updateState { copy(addingZoneName = intent.name) }
            is HomeIntent.UpdateAddingZoneIcon -> updateState { copy(addingZoneIconKey = intent.iconKey) }
            is HomeIntent.SetZoneRadius -> updateState { copy(addingZoneRadius = intent.radius) }
            is HomeIntent.SetZoneIsPrivate -> updateState { copy(addingZoneIsPrivate = intent.isPrivate) }
            is HomeIntent.SelectZone -> selectZone(intent.zoneId)
            is HomeIntent.DeleteZone -> {
                if (intent.zoneId in state.value.deletingZoneIds) return@handleIntent
                updateState { copy(deletingZoneIds = deletingZoneIds + intent.zoneId) }
                viewModelScope.launch {
                    zoneRepository.deleteZone(intent.zoneId)
                        .onFailure { e -> sendEffect(HomeEffect.ShowError(PaparcarError.Database.WriteError(e.message ?: ""))) }
                    updateState { copy(deletingZoneIds = deletingZoneIds - intent.zoneId) }
                }
            }
            is HomeIntent.EnterEditZoneMode -> enterEditZoneMode(intent.zoneId)

            // Search
            is HomeIntent.SearchQueryChanged -> onSearchQueryChanged(intent.query)
            is HomeIntent.SelectSearchResult -> {
                updateState { resetSearch() }
                geocodeCameraLocation(intent.result.lat, intent.result.lon)
            }
            is HomeIntent.ClearSearch -> updateState { resetSearch() }

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

    @OptIn(FlowPreview::class)
    private fun subscribeSearchQuery() {
        searchQueryFlow
            .debounce(SEARCH_DEBOUNCE_MS.milliseconds)
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
        // [BUG-7] AR registration must fire only on the actual false→true flip of
        // permissionsGranted, NOT on every reconnect tick. Track permission changes
        // in a dedicated stream that mirrors the granted state into HomeState and
        // registers transitions once. The location stream below depends on the
        // (permState, reconnectTick) pair so reconnects still rebuild the spot
        // subscription, but AR is left alone.
        permissionManager.permissionState
            .distinctUntilChanged { old, new ->
                old.hasCorePermissions == new.hasCorePermissions &&
                    old.hasProducerPermissions == new.hasProducerPermissions
            }
            .onEach { perm ->
                // Consumer UI (map, spots) is driven by CORE. [DET-READY-001d]
                updateState { copy(hasCorePermissions = perm.hasCorePermissions) }
                // AR registration needs the PRODUCER tier (activity recognition); register only
                // when it is granted. Best-effort: AR failure degrades gracefully to GPS-only.
                if (perm.hasProducerPermissions) {
                    runCatching { activityRecognitionManager.registerTransitions() }
                        .onFailure { e -> PaparcarLogger.w(TAG, "AR registration failed — GPS-only mode", e) }
                }
            }
            .launchIn(viewModelScope)

        combine(permissionManager.permissionState, reconnectTick) { perm, _ -> perm }
            .flatMapLatest { perm ->
                if (perm.hasCorePermissions) locationDataSource.observeBalancedLocation() else emptyFlow()
            }
            .onStart { updateState { copy(isLoading = true) } }
            .onEach { location ->
                updateState { copy(isLoading = false, userGpsPoint = location) }
                geocodeUserLocation(location.latitude, location.longitude)
                if (state.value.cameraAddressAndPlace == null) {
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

    private fun subscribeMapFocusEvents() {
        mapFocusEventBus.events
            .onEach { (lat, lon) -> sendEffect(HomeEffect.MoveCameraTo(lat, lon)) }
            .launchIn(viewModelScope)
    }

    /** Drives the persistent detection-readiness banner. [DET-READY-001g] */
    private fun subscribeDetectionReadiness() {
        observeDetectionReadiness()
            .distinctUntilChanged()
            .onEach { readiness -> updateState { copy(detectionReadiness = readiness) } }
            .launchIn(viewModelScope)
    }

    /**
     * Drives the live driving puck (own car, top-down, heading-rotated) — only while detection is
     * actively monitoring a trip. Subscribes the heading-aware high-accuracy stream just for that
     * window (battery-bounded), tagging it with the active vehicle's body shape. Null otherwise →
     * the map falls back to the native location dot. [MAP-ICONS-V2]
     */
    private fun subscribeDrivingPuck() {
        observeDetectionReadiness()
            .map { (it as? DetectionReadiness.Monitoring)?.strategy }
            .distinctUntilChanged()
            .flatMapLatest { strategy ->
                if (strategy != null && state.value.hasCorePermissions) {
                    locationDataSource.observeUiLocation().map { strategy to it }
                } else {
                    flowOf<Pair<ParkingStrategy, UserLocationUi>?>(null)
                }
            }
            .onEach { pair ->
                val puck = pair?.let { (strategy, loc) ->
                    val vehicle = monitoredVehicle(state.value.vehicles, strategy)
                    DrivingPuck(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        bearingDegrees = loc.bearingDegrees,
                        accuracy = loc.accuracy,
                        carbodyType = vehicle?.carbodyType,
                        sizeCategory = vehicle?.sizeCategory,
                    )
                }
                updateState { copy(drivingPuck = puck) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * The vehicle the active detection strategy is following — so the puck shows the right car.
     * Under [ParkingStrategy.BLUETOOTH] that's the BT-paired vehicle (detected regardless of which
     * is primary), otherwise the primary/active one. Mirrors [ParkingStrategyResolver.strategyFor].
     * [MAP-ICONS-V2]
     */
    private fun monitoredVehicle(vehicles: List<Vehicle>, strategy: ParkingStrategy): Vehicle? =
        when (strategy) {
            ParkingStrategy.BLUETOOTH ->
                vehicles.firstOrNull { it.bluetoothDeviceId != null } ?: vehicles.firstOrNull { it.isActive }
            else ->
                vehicles.firstOrNull { it.isActive } ?: vehicles.firstOrNull()
        }

    private fun subscribeNearbySpots() {
        combine(permissionManager.permissionState, spotQueryCenter, reconnectTick) { perm, center, _ ->
            if (perm.hasCorePermissions) center else null
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
            .onEach { spots -> updateState { applyNewSpots(spots) } }
            .launchIn(viewModelScope)
    }

    /**
     * Applies the freshly-fetched nearby spots and prunes the selection if the
     * selected item is no longer either an active session or one of the visible
     * spots. Keeps the selection logic adjacent to the data update without
     * inlining it inside the flow operator. [A1]
     */
    private fun HomeState.applyNewSpots(spots: List<io.apptolast.paparcar.domain.model.Spot>): HomeState {
        val cur = selectedItemId
        val selectionStillValid = cur == null ||
            activeSessions.any { it.id == cur } ||
            spots.any { it.id == cur }
        return copy(
            nearbySpots = spots,
            selectedItemId = if (selectionStillValid) cur else null,
        )
    }

    // ── Intent handlers ───────────────────────────────────────────────────────

    private fun onCameraPositionChanged(lat: Double, lon: Double) {
        if (state.value.mode !is HomeMode.Browse) {
            updatePinDuringMode(lat, lon)
        } else {
            maybeRecenterSpotsOnPan(lat, lon)
        }
        geocodeCameraLocation(lat, lon)
    }

    /**
     * Pin-mode handler: track the camera centre as the pending pin coordinate
     * and flip [HomeState.isCameraMoving] off after [CAMERA_SETTLED_MS] without
     * a new frame so confirm buttons are only enabled once the camera settles.
     */
    private fun updatePinDuringMode(lat: Double, lon: Double) {
        updateState { copy(pinCameraLat = lat, pinCameraLon = lon, isCameraMoving = true) }
        cameraSettledJob?.cancel()
        cameraSettledJob = viewModelScope.launch {
            delay(CAMERA_SETTLED_MS)
            updateState { copy(isCameraMoving = false) }
        }
    }

    /**
     * Browse-mode handler: if the user has panned more than
     * [SPOT_CAMERA_PAN_THRESHOLD_METERS] from the current spot query centre,
     * move the centre to the new camera position so the nearby spots query
     * rebuilds against where the user is actually looking. Only relevant once
     * the centre has been seeded by the first GPS fix.
     */
    private fun maybeRecenterSpotsOnPan(lat: Double, lon: Double) {
        val current = spotQueryCenter.value ?: return
        if (haversineMeters(current.latitude, current.longitude, lat, lon) <= SPOT_CAMERA_PAN_THRESHOLD_METERS) return
        spotQueryCenter.value = GpsPoint(
            latitude = lat,
            longitude = lon,
            accuracy = 0f,
            timestamp = Clock.System.now().toEpochMilliseconds(),
            speed = 0f,
        )
        updateState { copy(isSpotQueryCenteredOnUser = false) }
    }

    private fun onRecenterSpots() {
        val gps = state.value.userGpsPoint ?: return
        spotQueryCenter.value = gps
        updateState { copy(isSpotQueryCenteredOnUser = true) }
        sendEffect(HomeEffect.MoveCameraTo(gps.latitude, gps.longitude))
    }

    private fun onSearchQueryChanged(query: String) {
        val blank = query.isBlank()
        updateState {
            copy(
                searchQuery = query,
                isSearchActive = true,
                searchResults = if (blank) emptyList() else searchResults,
                isSearching = if (blank) false else isSearching,
            )
        }
        searchQueryFlow.value = query
    }

    /** Wipes every search-related field. Used by SelectSearchResult + ClearSearch. */
    private fun HomeState.resetSearch(): HomeState =
        copy(searchQuery = "", searchResults = emptyList(), isSearchActive = false, isSearching = false)

    private fun confirmReportSpot() {
        val current = state.value
        if (current.mode !is HomeMode.Reporting || current.isReporting) return
        val lat = current.pinCameraLat ?: run {
            sendEffect(HomeEffect.ShowError(PaparcarError.Location.ProviderDisabled)); return
        }
        val lon = current.pinCameraLon ?: run {
            sendEffect(HomeEffect.ShowError(PaparcarError.Location.ProviderDisabled)); return
        }
        // Carbody hygiene [F1-bis]: when the user reports a freed spot manually they pick
        // size but no carbody (no picker in the report flow). Fall back to the active
        // vehicle's carbody so the public Spot carries it and the "Left by …" subline
        // renders for other users. Size is taken verbatim from the picker — null means
        // the user explicitly selected "Indefinido", so we don't infer it from the vehicle.
        val activeVehicle = current.vehicles.firstOrNull { it.isActive }
        val resolvedSize = current.reportingSize
        val resolvedCarbody = activeVehicle?.carbodyType
        updateState { copy(isReporting = true) }
        viewModelScope.launch {
            val spotId = "manual_${Clock.System.now().toEpochMilliseconds()}"
            runCatching {
                reportSpotReleased(
                    lat = lat,
                    lon = lon,
                    spotId = spotId,
                    spotType = SpotType.MANUAL_REPORT,
                    confidence = 1f,
                    sizeCategory = resolvedSize,
                    carbodyType = resolvedCarbody,
                )
            }
                .onSuccess {
                    updateState { clearedModeFields().copy(isReporting = false) }
                    sendEffect(HomeEffect.SpotReported)
                }
                .onFailure { e ->
                    updateState { copy(isReporting = false) }
                    sendEffect(HomeEffect.ShowError(PaparcarError.Network.Unknown(e.message ?: "")))
                }
        }
    }

    private fun confirmDetectedParking() {
        val gps = state.value.pendingParkingGps ?: return
        updateState { copy(pendingParkingGps = null) }
        viewModelScope.launch {
            confirmParking(gps, 1.0f, SpotType.AUTO_DETECTED)
                .onSuccess { saved ->
                    // [CONFIRM-NO-NOTIF-CLEANUP] notification responsibility moved out of use case.
                    notificationPort.showParkingSaved(saved.location.latitude, saved.location.longitude)
                }
                .onFailure { sendEffect(HomeEffect.ShowError(PaparcarError.Parking.SaveFailed)) }
        }
    }

    private fun releaseParking(lat: Double, lon: Double, publishSpot: Boolean) {
        val target = state.value.selectedSession ?: state.value.userParking
        updateState { copy(isReleasingParking = true) }
        viewModelScope.launch {
            releaseSession(lat, lon, target, publishSpot)
                .onSuccess {
                    updateState { copy(selectedItemId = null, isReleasingParking = false) }
                    if (publishSpot) sendEffect(HomeEffect.SpotReported)
                }
                .onFailure { e ->
                    updateState { copy(isReleasingParking = false) }
                    sendEffect(HomeEffect.ShowError(PaparcarError.Database.WriteError(e.message ?: "")))
                }
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
                confirmParking(newGps, 1.0f, SpotType.MANUAL_REPORT, vehicleId = current.addingParkingVehicleId)
                    .onSuccess { saved ->
                        // [CONFIRM-NO-NOTIF-CLEANUP] notification responsibility moved out of use case.
                        notificationPort.showParkingSaved(saved.location.latitude, saved.location.longitude)
                    }
                    .map { Unit }
            }
            result
                .onSuccess { updateState { clearedModeFields().copy(isSavingParking = false) } }
                .onFailure { e ->
                    // [BUG-8] Keep the user in AddingParking with the pin intact so they
                    // can retry. Only flip the spinner off. Going back to Browse on failure
                    // would force the user to re-centre the camera and reconfirm.
                    updateState { copy(isSavingParking = false) }
                    sendEffect(HomeEffect.ShowError(PaparcarError.Parking.SaveFailed))
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
            val saveResult: Result<*> = if (editingId != null) {
                val existing = current.zones.find { it.id == editingId }
                if (existing == null) {
                    Result.failure(IllegalStateException("zone $editingId vanished while editing"))
                } else {
                    zoneRepository.saveZone(
                        existing.copy(
                            name = name,
                            lat = lat,
                            lon = lon,
                            iconKey = current.addingZoneIconKey,
                            radiusMeters = current.addingZoneRadius,
                            isPrivate = current.addingZoneIsPrivate,
                        )
                    )
                }
            } else {
                saveZone(
                    name = name,
                    lat = lat,
                    lon = lon,
                    iconKey = current.addingZoneIconKey,
                    radiusMeters = current.addingZoneRadius,
                    isPrivate = current.addingZoneIsPrivate,
                )
            }
            saveResult
                .onSuccess {
                    updateState { clearedModeFields().copy(isSavingZone = false) }
                    sendEffect(HomeEffect.ZoneSaved)
                }
                .onFailure { e ->
                    // [BUG-8] Stay in AddingZone with the form intact so the user can retry.
                    updateState { copy(isSavingZone = false) }
                    sendEffect(HomeEffect.ShowError(PaparcarError.Database.WriteError(e.message ?: "")))
                }
        }
    }

    private fun enterEditZoneMode(zoneId: String) {
        val zone = state.value.zones.find { it.id == zoneId } ?: return
        updateState {
            clearedModeFields().copy(
                mode = HomeMode.AddingZone,
                pinCameraLat = zone.lat,
                pinCameraLon = zone.lon,
                addingZoneName = zone.name,
                addingZoneIconKey = zone.iconKey,
                addingZoneRadius = zone.radiusMeters,
                addingZoneIsPrivate = zone.isPrivate,
                editingZoneId = zoneId,
            )
        }
        sendEffect(HomeEffect.MoveCameraTo(zone.lat, zone.lon))
    }

    private fun selectZone(zoneId: String) {
        val zone = state.value.zones.find { it.id == zoneId } ?: return
        // A zone is a navigation shortcut, not a thing to "manage": fly the camera
        // there and stay in Browse so the spots at the zone are immediately visible.
        // No selection state / management peek — edit is long-press on the chip,
        // delete is the chip's ×. [ZONE-NOSEL-001]
        sendEffect(HomeEffect.MoveCameraTo(zone.lat, zone.lon))
    }

    // ── Mode invariant ────────────────────────────────────────────────────────
    //
    // Selection (selectedItemId) and add-modes (Reporting / AddingZone /
    // AddingParking) are mutually exclusive:
    //   mode != Browse         ⇒  selectedItemId == null
    //   selectedItemId != null ⇒  mode == Browse
    //
    // Enforcement sites:
    //   • EnterReportMode / EnterAddParkingMode / EnterAddZoneMode / EnterEditZoneMode
    //     all clear `selectedItemId` on entry.
    //   • SelectItem calls [clearedModeFields] before applying the new selection,
    //     so picking a marker silently exits any active add-mode. (selectZone only
    //     moves the camera — a zone is not a selection.)
    //
    // Use this helper for any new transition from a non-Browse mode back to Browse
    // — it wipes every field that belongs to a non-Browse mode in one place, so
    // the invariant cannot drift as new mode-scoped fields are added.

    /**
     * Returns a copy of this state reset to [HomeMode.Browse], clearing every
     * field that is owned by a non-Browse mode (pin coords, camera-moving flag,
     * report/zone/parking form fields, editing IDs) AND the selection field
     * (selectedItemId). Callers that need to set a selection or re-enter a mode
     * apply their fields via `.copy(...)` on top of this base.
     *
     * In-flight booleans (isReporting / isSavingZone / isSavingParking /
     * isReleasingParking) are intentionally left alone: they reflect a running
     * operation, not the user-facing mode.
     *
     * **Invariant enforced here:** `mode != Browse ⇒ selectedItemId == null`.
     * Every Enter*Mode / SelectItem path goes through this helper so the
     * invariant cannot drift as new mode-scoped fields are added. [BUG-5]
     */
    private fun HomeState.clearedModeFields(): HomeState = copy(
        mode = HomeMode.Browse,
        selectedItemId = null,
        pinCameraLat = null,
        pinCameraLon = null,
        isCameraMoving = false,
        reportingSize = null,
        addingZoneName = "",
        addingZoneIconKey = ZoneIcon.DEFAULT,
        addingZoneRadius = Zone.DEFAULT_RADIUS_METERS,
        addingZoneIsPrivate = false,
        editingZoneId = null,
        editingParkingId = null,
        addingParkingVehicleId = null,
    )

    private fun submitSpotSignal(spotId: String, accepted: Boolean) {
        if (spotId in state.value.inFlightSpotSignals) return
        updateState { copy(inFlightSpotSignals = inFlightSpotSignals + spotId) }
        viewModelScope.launch {
            sendSpotSignal(spotId, accepted)
                .onSuccess { sendEffect(HomeEffect.SpotSignalSent) }
                .onFailure { sendEffect(HomeEffect.ShowError(PaparcarError.Network.Unknown(it.message ?: ""))) }
            updateState { copy(inFlightSpotSignals = inFlightSpotSignals - spotId) }
        }
    }

    private fun setMapType(type: MapType) {
        if (state.value.mapType == type) return
        appPreferences.setDefaultMapType(type.toPreferenceString())
        updateState { copy(mapType = type) }
    }

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    private fun reportTestSpot() {
        if (!isDebugBuild) return
        viewModelScope.launch {
            val spotId = "test_${kotlin.uuid.Uuid.random()}"
            runCatching { reportSpotReleased(40.416775, -3.703790, spotId) }
                .onSuccess { sendEffect(HomeEffect.TestSpotSent) }
                .onFailure { e -> PaparcarLogger.w(TAG, "reportTestSpot failed", e) }
        }
    }

    // Geocoding implementations live in [HomeGeocodingController]. The VM
    // delegates via the [geocoder] field above — see its kdoc for the exact
    // cancellation policy and shimmer-flag invariants.
    private fun geocodeUserLocation(lat: Double, lon: Double) = geocoder.geocodeUserLocation(lat, lon)
    private fun geocodeCameraLocation(lat: Double, lon: Double) = geocoder.geocodeCameraLocation(lat, lon)

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

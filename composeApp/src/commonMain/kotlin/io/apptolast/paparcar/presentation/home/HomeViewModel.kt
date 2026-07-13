package io.apptolast.paparcar.presentation.home

import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.isDebugBuild
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.connectivity.ConnectivityObserver
import io.apptolast.paparcar.domain.connectivity.ConnectivityStatus
import io.apptolast.paparcar.domain.detection.ManualParkingDetection
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.model.DetectionReadiness
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.repository.ZoneRepository
import io.apptolast.paparcar.domain.usecase.detection.ObserveDetectionReadinessUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.ObserveParkedVehiclesUseCase
import io.apptolast.paparcar.domain.usecase.parking.ReleaseActiveParkingSessionUseCase
import io.apptolast.paparcar.domain.usecase.parking.UpdateParkingLocationUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.domain.usecase.spot.SendSpotSignalUseCase
import io.apptolast.paparcar.domain.event.MapFocusEventBus
import io.apptolast.paparcar.domain.event.StartAddParkingEventBus
import io.apptolast.paparcar.domain.usecase.zone.SaveZoneUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.presentation.base.BaseViewModel
import io.apptolast.paparcar.presentation.home.model.isDetectionStopped
import io.apptolast.paparcar.presentation.home.model.isDetectionWorking
import io.apptolast.paparcar.presentation.home.model.toUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
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
    private val observeDetectionReadiness: ObserveDetectionReadinessUseCase,
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val sendSpotSignal: SendSpotSignalUseCase,
    private val userParkingRepository: UserParkingRepository,
    private val confirmParking: ConfirmParkingUseCase,
    private val releaseSession: ReleaseActiveParkingSessionUseCase,
    private val observeParkedVehicles: ObserveParkedVehiclesUseCase,
    private val updateParkingLocation: UpdateParkingLocationUseCase,
    private val vehicleRepository: VehicleRepository,
    private val zoneRepository: ZoneRepository,
    private val saveZone: SaveZoneUseCase,
    private val appPreferences: AppPreferences,
    private val mapFocusEventBus: MapFocusEventBus,
    private val startAddParkingEventBus: StartAddParkingEventBus,
    private val notificationPort: AppNotificationManager,
    private val manualParkingDetection: ManualParkingDetection,
    // Feature controllers — self-contained pipeline owners built by Koin with their own use cases
    // (geocoding, live trip, debounced search, nearby spots). The VM only collects their update flows
    // into state and forwards commands; see subscribeControllerUpdates(). [HOMEVM-CTRL-002]
    private val geocoder: HomeGeocodingController,
    private val trip: HomeTripController,
    private val search: HomeSearchController,
    private val spots: HomeSpotsController,
) : BaseViewModel<HomeState, HomeIntent, HomeEffect>() {

    // ── Private flows ─────────────────────────────────────────────────────────

    // Incremented on Offline → Online so the spot subscription rebuilds immediately
    // after connectivity is restored, even if the GPS position hasn't changed.
    private val reconnectTick = MutableStateFlow(0)

    private var cameraSettledJob: Job? = null

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        updateState { copy(mapType = appPreferences.defaultMapType.toMapType()) }
        geocoder.attach(viewModelScope)
        subscribeControllerUpdates()
        subscribeConnectivity()
        // Repo streams → state slices, one line each ([collectInto] isolates stream errors so the UI
        // keeps serving the cache). Sessions escalate to a snackbar; the rest log. [HOMEVM-CTRL-003]
        userParkingRepository.observeActiveSessions()
            .collectInto(
                label = "activeSessions",
                onError = { e -> sendEffect(HomeEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: ""))) },
            ) { copy(activeSessions = it) }
        observeParkedVehicles().collectInto("parkedVehicles") { copy(parkedVehicles = it) }
        zoneRepository.observeZones().collectInto("zones") { copy(zones = it) }
        vehicleRepository.observeVehicles().collectInto("vehicles") { copy(vehicles = it) }
        subscribeGpsLocation()
        subscribeMapFocusEvents()
        subscribeStartAddParkingRequests()
        subscribeDetectionReadiness()
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

            // [DET-TOGGLE-001] Single "activate detection" action: flip the Settings flag on AND, if
            // the producer permissions are still missing, open the permissions screen — so one tap
            // brings detection fully online whatever was missing (config and/or permissions).
            is HomeIntent.EnableAutoDetection -> {
                appPreferences.setAutoDetectParking(true)
                val perms = permissionManager.permissionState.value
                if (perms.hasProducerPermissions) {
                    sendEffect(HomeEffect.DetectionEnabled)
                } else {
                    sendEffect(HomeEffect.OpenDetectionPermissions(if (perms.hasCorePermissions) "producer" else "all"))
                }
            }

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

    /**
     * The single point where every feature controller's update flow enters [HomeState]. Controllers
     * own their pipelines (and their use cases, via Koin); the VM stays the only writer of state —
     * the "one sink" invariant of [SPOT-FLICKER-001] now holds for all of them by construction.
     */
    private fun subscribeControllerUpdates() {
        geocoder.updates
            .onEach { update ->
                when (update) {
                    is GeocodeUpdate.UserAddress -> updateState { copy(userAddressAndPlace = update.info) }
                    is GeocodeUpdate.CameraAddress -> updateState { copy(cameraAddressAndPlace = update.info) }
                    is GeocodeUpdate.CameraGeocoding -> updateState { copy(isCameraGeocoding = update.active) }
                }
            }
            .launchIn(viewModelScope)

        trip.updates
            .onEach { update ->
                updateState {
                    copy(
                        drivingPuck = update.puck,
                        tripTrail = update.trail,
                        matchedTrail = update.matchedTrail,
                        departurePoint = update.departurePoint,
                    )
                }
            }
            .launchIn(viewModelScope)

        search.updates
            .onEach { update ->
                when (update) {
                    is SearchUpdate.Searching -> updateState { copy(isSearching = true) }
                    is SearchUpdate.Success -> updateState { copy(searchResults = update.results, isSearching = false) }
                    is SearchUpdate.Failure -> updateState { copy(searchResults = emptyList(), isSearching = false) }
                }
            }
            .launchIn(viewModelScope)

        spots.updates
            .onEach { update ->
                when (update) {
                    is SpotsUpdate.Data -> updateState { applyNewSpots(update.spots) }
                    is SpotsUpdate.Error ->
                        sendEffect(HomeEffect.ShowError(PaparcarError.Network.Unknown(update.message)))
                }
            }
            .launchIn(viewModelScope)
    }

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

    /**
     * Collects a repo stream into a single state write. Errors are isolated with [onError]
     * (default: log and keep serving the cache) so a failing stream never takes Home down —
     * the standard `.catch` policy for observables, applied uniformly. [HOMEVM-CTRL-003]
     */
    private fun <T> Flow<T>.collectInto(
        label: String,
        onError: (Throwable) -> Unit = { e -> PaparcarLogger.e(TAG, "$label stream error", e) },
        apply: HomeState.(T) -> HomeState,
    ) {
        onEach { value -> updateState { apply(value) } }
            .catch { e -> onError(e) }
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
                    spots.updateQueryCenter(location)
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

    /** The cold-start nudge notification ("Marcar mi plaza" / tap) asks Home to drop the user straight
     *  into manual add-parking mode, tagged with the active vehicle so the peek shows its real glyph +
     *  name. We stay in Browse until the vehicles flow emits, then enter — it is backed by local Room
     *  data already synced at splash, so on cold start this resolves near-instantly with no
     *  generic-icon flash. The nudge only fires when a Coordinator vehicle exists, so it always
     *  arrives. [DET-TOGGLE-002] */
    private fun subscribeStartAddParkingRequests() {
        startAddParkingEventBus.requests
            .onEach {
                val vehicles = state.map { it.vehicles }.first { it.isNotEmpty() }
                val markVehicleId = vehicles.firstOrNull { it.isActive }?.id
                    ?: vehicles.firstOrNull()?.id
                handleIntent(
                    HomeIntent.EnterAddParkingMode(
                        initialGps = state.value.userGpsPoint,
                        targetVehicleId = markVehicleId,
                    ),
                )
            }
            .launchIn(viewModelScope)
    }

    /** Drives the persistent detection-readiness banner [DET-READY-001g] and fires the transient
     *  "detection stopped" snackbar on a working→stopped drop. [DET-TOGGLE-002] */
    private fun subscribeDetectionReadiness() {
        observeDetectionReadiness()
            .distinctUntilChanged()
            .onEach { readiness ->
                val wasWorking = state.value.detectionReadiness.toUiState().isDetectionWorking
                updateState { copy(detectionReadiness = readiness) }
                // Detection just dropped from a working state into a stopped one (Settings flag off, or
                // a producer/core permission revoked). Surface a snackbar with one-tap re-activation —
                // the persistent banner stays; this catches the moment of the change. [DET-TOGGLE-002]
                if (wasWorking && readiness.toUiState().isDetectionStopped) {
                    sendEffect(HomeEffect.DetectionStopped)
                }
            }
            .launchIn(viewModelScope)
    }

    // ── Intent handlers ───────────────────────────────────────────────────────

    private fun onCameraPositionChanged(lat: Double, lon: Double) {
        if (state.value.mode !is HomeMode.Browse) {
            updatePinDuringMode(lat, lon)
        } else if (spots.maybeRecenterOnPan(lat, lon)) {
            updateState { copy(isSpotQueryCenteredOnUser = false) }
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

    private fun onRecenterSpots() {
        val gps = state.value.userGpsPoint ?: return
        spots.recenter(gps)
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
        search.onQueryChanged(query)
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
                    // Reuse the address/POI already geocoded for the settled pin centre
                    // instead of re-hitting the network from the use case. [SPOT-PREFETCH-001]
                    prefetched = current.cameraAddressAndPlace,
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
                    // [DET-MANUAL-CANCEL-001] The user resolved the park by hand → the trip is over;
                    // tear down any coordinator session so it can't overwrite this pin.
                    manualParkingDetection.stop()
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
                        // [DET-MANUAL-CANCEL-001] Fresh manual pin → cancel any in-progress detection
                        // so a late auto-confirm can't overwrite it (leg Oppo tras Glorieta).
                        manualParkingDetection.stop()
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

    // The pure `HomeState → HomeState` transitions of the mode machine (clearedModeFields,
    // applyNewSpots, resetSearch) live in HomeStateTransitions.kt, next to the mode↔selection
    // invariant they enforce. [HOMEVM-CTRL-004]

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
        const val CAMERA_SETTLED_MS = 280L

        // Map type preference strings
        const val MAP_TYPE_TERRAIN = "TERRAIN"
        const val MAP_TYPE_SATELLITE = "SATELLITE"
        const val MAP_TYPE_HYBRID = "HYBRID"

    }
}

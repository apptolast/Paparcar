package io.apptolast.paparcar.presentation.home

import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.isDebugBuild
import io.apptolast.paparcar.domain.ActivityRecognitionManager
import io.apptolast.paparcar.domain.connectivity.ConnectivityObserver
import io.apptolast.paparcar.domain.connectivity.ConnectivityStatus
import io.apptolast.paparcar.domain.detection.ManualParkingDetection
import io.apptolast.paparcar.domain.diagnostics.UiLocationLogger
import io.apptolast.paparcar.domain.diagnostics.UiLocationSample
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.repository.ZoneRepository
import io.apptolast.paparcar.domain.usecase.detection.ObserveDetectionReadinessUseCase
import io.apptolast.paparcar.domain.usecase.parking.ObserveParkedVehiclesUseCase
import io.apptolast.paparcar.domain.usecase.parking.ReleaseActiveParkingSessionUseCase
import io.apptolast.paparcar.domain.usecase.parking.SaveManualParkingUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportManualSpotUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.domain.usecase.spot.SendSpotSignalUseCase
import io.apptolast.paparcar.domain.event.MapFocusEventBus
import io.apptolast.paparcar.domain.event.StartAddParkingEventBus
import io.apptolast.paparcar.domain.usecase.zone.SaveOrUpdateZoneUseCase
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class HomeViewModel(
    private val permissionManager: PermissionManager,
    private val locationDataSource: LocationDataSource,
    private val activityRecognitionManager: ActivityRecognitionManager,
    private val connectivityObserver: ConnectivityObserver,
    private val observeDetectionReadiness: ObserveDetectionReadinessUseCase,
    private val reportManualSpot: ReportManualSpotUseCase,
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val sendSpotSignal: SendSpotSignalUseCase,
    private val userParkingRepository: UserParkingRepository,
    private val saveManualParking: SaveManualParkingUseCase,
    private val releaseSession: ReleaseActiveParkingSessionUseCase,
    private val observeParkedVehicles: ObserveParkedVehiclesUseCase,
    private val vehicleRepository: VehicleRepository,
    private val zoneRepository: ZoneRepository,
    private val saveOrUpdateZone: SaveOrUpdateZoneUseCase,
    private val appPreferences: AppPreferences,
    private val mapFocusEventBus: MapFocusEventBus,
    private val startAddParkingEventBus: StartAddParkingEventBus,
    private val manualParkingDetection: ManualParkingDetection,
    // Feature controllers — self-contained pipeline owners built by Koin with their own use cases
    // (geocoding, live trip, debounced search, nearby spots). The VM only collects their update flows
    // into state and forwards commands; see subscribeControllerUpdates(). [HOMEVM-CTRL-002]
    private val geocoder: HomeGeocodingController,
    private val trip: HomeTripController,
    private val search: HomeSearchController,
    private val spots: HomeSpotsController,
    // Consumer-location observability (local logcat always + gated Firestore mirror). Verifies the
    // foreground-scoped high-accuracy request actually refreshes the dot. [UI-LOC-FOREGROUND-001]
    private val uiLocationLogger: UiLocationLogger,
) : BaseViewModel<HomeState, HomeIntent, HomeEffect>() {

    /**
     * The live trip render data (driving puck + trail) as its OWN StateFlow, deliberately NOT merged
     * into [HomeState]: it changes at the GPS fix rate (~1 Hz real, faster in mock), and merging it
     * would recompose the whole Home tree — including the expensive map — on every fix. Consumers
     * collect this separately and read it only in isolated leaf scopes, so the map isolates the puck's
     * high-frequency updates. [DRIVE-PUCK-NATIVE-001]
     */
    val tripRender: StateFlow<TripUpdate> =
        trip.updates.stateIn(viewModelScope, SharingStarted.Eagerly, TripUpdate.IDLE)

    private var cameraSettledJob: Job? = null

    /** Whether the Home map is foreground (RESUMED). Drives [subscribeGpsLocation]: the high-accuracy
     *  user-location request runs ONLY while true, so a backgrounded map costs no GPS. Starts false;
     *  the screen flips it via [HomeIntent.SetMapForeground] on resume/pause. [UI-LOC-FOREGROUND-001] */
    private val mapForeground = MutableStateFlow(false)

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        updateState { copy(mapType = appPreferences.defaultMapType.toMapType()) }
        geocoder.attach(viewModelScope)
        subscribeControllerUpdates()
        // Repo streams → state slices, one line each ([collectInto] isolates stream errors so the UI
        // keeps serving the cache). Sessions escalate to a snackbar; the rest log. [HOMEVM-CTRL-003]
        userParkingRepository.observeActiveSessions()
            .collectInto("activeSessions", notify = { e -> PaparcarError.Database.Unknown(e.message ?: "") }) {
                copy(activeSessions = it)
            }
        observeParkedVehicles().collectInto("parkedVehicles") { copy(parkedVehicles = it) }
        zoneRepository.observeZones().collectInto("zones") { copy(zones = it) }
        vehicleRepository.observeVehicles().collectInto("vehicles") { copy(vehicles = it) }
        subscribePermissions()
        subscribeGpsLocation()
        subscribeMapFocusEvents()
        subscribeStartAddParkingRequests()
        subscribeDetectionReadiness()
    }

    override fun initState(): HomeState = HomeState()

    // ── Intent dispatch — one exhaustive when, one handler per DOMAIN ─────────
    // The sealed when stays (compile-time exhaustiveness is a feature, not a
    // smell); each branch delegates to its domain's handler below so this
    // dispatcher reads as a table of contents. NO registry, NO reflection.
    // [HOME-ATOMIZE-001 F4]

    override fun handleIntent(intent: HomeIntent) {
        when (intent) {
            // Map & camera
            is HomeIntent.CameraPositionChanged,
            is HomeIntent.RecenterSpots,
            is HomeIntent.SetMapType,
            is HomeIntent.SetMapForeground,
            -> handleMapIntent(intent)

            // Search
            is HomeIntent.SearchQueryChanged,
            is HomeIntent.SelectSearchResult,
            is HomeIntent.ClearSearch,
            -> handleSearchIntent(intent)

            // Community spots — selection, filter, signals, manual report
            is HomeIntent.LoadNearbySpots,
            is HomeIntent.SelectItem,
            is HomeIntent.SetSizeFilter,
            is HomeIntent.SendSpotSignal,
            is HomeIntent.EnterReportMode,
            is HomeIntent.ExitReportMode,
            is HomeIntent.ConfirmReportSpot,
            is HomeIntent.SetReportingSize,
            is HomeIntent.ReportTestSpot,
            -> handleSpotIntent(intent)

            // Own parking sessions
            is HomeIntent.ShowParkingConfirmation,
            is HomeIntent.ConfirmDetectedParking,
            is HomeIntent.DismissConfirmation,
            is HomeIntent.ReleaseParking,
            is HomeIntent.EnterAddParkingMode,
            is HomeIntent.ExitAddParkingMode,
            is HomeIntent.ConfirmAddParking,
            -> handleParkingIntent(intent)

            // Habitual zones
            is HomeIntent.EnterAddZoneMode,
            is HomeIntent.ExitAddZoneMode,
            is HomeIntent.ConfirmAddZone,
            is HomeIntent.UpdateAddingZoneName,
            is HomeIntent.UpdateAddingZoneIcon,
            is HomeIntent.SetZoneRadius,
            is HomeIntent.SetZoneIsPrivate,
            is HomeIntent.SelectZone,
            is HomeIntent.DeleteZone,
            is HomeIntent.EnterEditZoneMode,
            -> handleZoneIntent(intent)

            // Detection controls
            is HomeIntent.StartDrivingDetection,
            is HomeIntent.EnableAutoDetection,
            -> handleDetectionIntent(intent)
        }
    }

    // ── Map & camera ──────────────────────────────────────────────────────────

    private fun handleMapIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.CameraPositionChanged -> onCameraPositionChanged(intent.lat, intent.lon)
            is HomeIntent.RecenterSpots -> onRecenterSpots()
            is HomeIntent.SetMapType -> setMapType(intent.type)
            is HomeIntent.SetMapForeground -> mapForeground.value = intent.active
            else -> Unit
        }
    }

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

    private fun setMapType(type: MapType) {
        if (state.value.mapType == type) return
        appPreferences.setDefaultMapType(type.toPreferenceString())
        updateState { copy(mapType = type) }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun handleSearchIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.SearchQueryChanged -> onSearchQueryChanged(intent.query)
            is HomeIntent.SelectSearchResult -> {
                updateState { resetSearch() }
                geocodeCameraLocation(intent.result.lat, intent.result.lon)
            }
            is HomeIntent.ClearSearch -> updateState { resetSearch() }
            else -> Unit
        }
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

    // ── Community spots ───────────────────────────────────────────────────────

    private fun handleSpotIntent(intent: HomeIntent) {
        when (intent) {
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
            is HomeIntent.ReportTestSpot -> reportTestSpot()
            else -> Unit
        }
    }

    private fun confirmReportSpot() {
        val current = state.value
        if (current.mode !is HomeMode.Reporting || current.isReporting) return
        val (lat, lon) = current.pinCoordinates() ?: return reportPinMissing()
        updateState { copy(isReporting = true) }
        viewModelScope.launch {
            reportManualSpot(
                lat = lat,
                lon = lon,
                sizeCategory = current.reportingSize,
                // Reuse the address/POI already geocoded for the settled pin centre
                // instead of re-hitting the network from the use case. [SPOT-PREFETCH-001]
                prefetched = current.cameraAddressAndPlace,
            )
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

    // ── Own parking sessions ──────────────────────────────────────────────────

    private fun handleParkingIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.ShowParkingConfirmation -> updateState { copy(pendingParkingGps = intent.gps) }
            is HomeIntent.ConfirmDetectedParking -> confirmDetectedParking()
            is HomeIntent.DismissConfirmation -> updateState { copy(pendingParkingGps = null) }
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
            else -> Unit
        }
    }

    private fun confirmDetectedParking() {
        val gps = state.value.pendingParkingGps ?: return
        updateState { copy(pendingParkingGps = null) }
        viewModelScope.launch {
            saveManualParking.confirmDetected(gps)
                .onFailure { sendEffect(HomeEffect.ShowError(PaparcarError.Parking.SaveFailed)) }
        }
    }

    private fun confirmAddParking() {
        val current = state.value
        if (current.mode !is HomeMode.AddingParking || current.isSavingParking) return
        val (lat, lon) = current.pinCoordinates() ?: return reportPinMissing()
        // No connectivity gate: sessions are local-first (Room now, Firestore mirrored by
        // the WorkManager sync queue), same as the auto-detection confirm. [OFFLINE-PARK-001]
        updateState { copy(isSavingParking = true) }
        viewModelScope.launch {
            saveManualParking(
                lat = lat,
                lon = lon,
                accuracy = current.userGpsPoint?.accuracy ?: 0f,
                editingParkingId = current.editingParkingId,
                targetVehicleId = current.addingParkingVehicleId,
            )
                .onSuccess { updateState { clearedModeFields().copy(isSavingParking = false) } }
                .onFailure {
                    // [BUG-8] Keep the user in AddingParking with the pin intact so they
                    // can retry. Only flip the spinner off. Going back to Browse on failure
                    // would force the user to re-centre the camera and reconfirm.
                    updateState { copy(isSavingParking = false) }
                    sendEffect(HomeEffect.ShowError(PaparcarError.Parking.SaveFailed))
                }
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

    // ── Habitual zones ────────────────────────────────────────────────────────

    private fun handleZoneIntent(intent: HomeIntent) {
        when (intent) {
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
            is HomeIntent.DeleteZone -> deleteZone(intent.zoneId)
            is HomeIntent.EnterEditZoneMode -> enterEditZoneMode(intent.zoneId)
            else -> Unit
        }
    }

    private fun confirmAddZone() {
        val current = state.value
        if (current.mode !is HomeMode.AddingZone || current.isSavingZone) return
        if (current.addingZoneName.isBlank()) return // save button is disabled; belt-and-braces
        val (lat, lon) = current.pinCoordinates() ?: return reportPinMissing()
        updateState { copy(isSavingZone = true) }
        viewModelScope.launch {
            saveOrUpdateZone(
                editingZoneId = current.editingZoneId,
                name = current.addingZoneName,
                lat = lat,
                lon = lon,
                iconKey = current.addingZoneIconKey,
                radiusMeters = current.addingZoneRadius,
                isPrivate = current.addingZoneIsPrivate,
            )
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

    private fun deleteZone(zoneId: String) {
        if (zoneId in state.value.deletingZoneIds) return
        updateState { copy(deletingZoneIds = deletingZoneIds + zoneId) }
        viewModelScope.launch {
            zoneRepository.deleteZone(zoneId)
                .onFailure { e -> sendEffect(HomeEffect.ShowError(PaparcarError.Database.WriteError(e.message ?: ""))) }
            updateState { copy(deletingZoneIds = deletingZoneIds - zoneId) }
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

    // ── Detection controls ────────────────────────────────────────────────────

    private fun handleDetectionIntent(intent: HomeIntent) {
        when (intent) {
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
            else -> Unit
        }
    }

    // ── Subscriptions (launched in init) ─────────────────────────────────────

    /**
     * The single point where every feature controller's update flow enters [HomeState]. Controllers
     * own their pipelines (and their use cases, via Koin); the VM stays the only writer of state —
     * the "one sink" invariant of [SPOT-FLICKER-001] now holds for all of them by construction.
     */
    private fun subscribeControllerUpdates() {
        geocoder.updates.collectSafely("geocoder") { update ->
            when (update) {
                is GeocodeUpdate.UserAddress -> updateState { copy(userAddressAndPlace = update.info) }
                is GeocodeUpdate.CameraAddress -> updateState { copy(cameraAddressAndPlace = update.info) }
                is GeocodeUpdate.CameraGeocoding -> updateState { copy(isCameraGeocoding = update.active) }
            }
        }

        // trip.updates is NOT merged into HomeState — it's exposed as [tripRender] (see above) so the
        // fix-rate puck/trail don't recompose the whole Home tree. Only the trip's rarely-changing
        // METADATA (vehicle + phase) is mirrored into HomeState for the sheet/peek, deduped so a fix that
        // doesn't change it never recomposes. [DRIVE-PUCK-NATIVE-001]
        tripRender
            .map { it.puck?.let { p -> DrivingMeta(p.vehicleId, p.phase) } }
            .distinctUntilChanged()
            .collectInto("drivingMeta") { copy(drivingMeta = it) }

        search.updates.collectSafely("search") { update ->
            when (update) {
                is SearchUpdate.Searching -> updateState { copy(isSearching = true) }
                is SearchUpdate.Success -> updateState { copy(searchResults = update.results, isSearching = false) }
                is SearchUpdate.Failure -> updateState { copy(searchResults = emptyList(), isSearching = false) }
            }
        }

        spots.updates.collectSafely("spots") { update ->
            when (update) {
                is SpotsUpdate.Data -> updateState { applyNewSpots(update.spots) }
                is SpotsUpdate.Error ->
                    sendEffect(HomeEffect.ShowError(PaparcarError.Network.Unknown(update.message)))
            }
        }
    }

    /**
     * Permission mirror + Activity Recognition registration. [BUG-7] AR must fire
     * only on the actual false→true flip of the producer tier, NOT on every
     * connectivity/location rebuild — hence its own stream, next to the other
     * detection-facing subscription ([subscribeDetectionReadiness]).
     */
    private fun subscribePermissions() {
        permissionManager.permissionState
            .distinctUntilChanged { old, new ->
                old.hasCorePermissions == new.hasCorePermissions &&
                    old.hasProducerPermissions == new.hasProducerPermissions
            }
            .collectSafely("permissions") { perm ->
                // Consumer UI (map, spots) is driven by CORE. [DET-READY-001d]
                updateState { copy(hasCorePermissions = perm.hasCorePermissions) }
                // AR registration needs the PRODUCER tier (activity recognition); register only
                // when it is granted. Best-effort: AR failure degrades gracefully to GPS-only.
                if (perm.hasProducerPermissions) {
                    runCatching { activityRecognitionManager.registerTransitions() }
                        .onFailure { e -> PaparcarLogger.w(TAG, "AR registration failed — GPS-only mode", e) }
                }
            }
    }

    /**
     * GPS + geocode pipeline. The consumer dot rides a **high-accuracy** request scoped to when the
     * map is foreground: coarse ~30 s balanced fixes made the dot look frozen while the user walked on
     * battery-aggressive OEMs (it barely moved), so foreground → high accuracy (a fix every few
     * seconds), background → no request at all (battery). Permissions gate the source; reconnects
     * rebuild it. Every subscription/fix is instrumented via [uiLocationLogger] (local logcat always +
     * gated Firestore mirror) so the fix can be verified in the field. [UI-LOC-FOREGROUND-001]
     */
    private fun subscribeGpsLocation() {
        combine(permissionManager.permissionState, mapForeground, onlineAgain()) { perm, foreground, _ ->
            perm.hasCorePermissions to foreground
        }
            .flatMapLatest { (hasCore, foreground) ->
                if (hasCore && foreground) instrumentedHighAccuracyLocation() else emptyFlow()
            }
            .onStart { updateState { copy(isLoading = true) } }
            .collectSafely("gpsLocation", notify = { e -> PaparcarError.Location.Unknown(e.message ?: "") }) { location ->
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
    }

    /**
     * The high-accuracy consumer stream wrapped with observability: a SUBSCRIBED sample when the
     * request starts, a FIX sample per fix (carrying the inter-fix gap + accuracy — the numbers that
     * prove the dot now refreshes), and a STOPPED sample when the map backgrounds and the request is
     * torn down. [UI-LOC-FOREGROUND-001]
     */
    private fun instrumentedHighAccuracyLocation(): Flow<io.apptolast.paparcar.domain.model.GpsPoint> {
        var lastFixMs = 0L
        return locationDataSource.observeHighAccuracyLocation()
            .onStart { uiLocationLogger.log(lifecycleSample(UiLocationSample.Kind.SUBSCRIBED)) }
            .onEach { fix ->
                val gap = if (lastFixMs == 0L) null else fix.timestamp - lastFixMs
                lastFixMs = fix.timestamp
                uiLocationLogger.log(
                    UiLocationSample(
                        timestampMs = fix.timestamp,
                        kind = UiLocationSample.Kind.FIX,
                        foreground = true,
                        priority = LOCATION_PRIORITY_HIGH_ACCURACY,
                        accuracy = fix.accuracy,
                        sinceLastFixMs = gap,
                        speed = fix.speed,
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                    ),
                )
            }
            // onCompletion fires when flatMapLatest tears this down (map backgrounded) — the STOPPED
            // marker that closes the SUBSCRIBED..STOPPED window in the trace.
            .onCompletion { uiLocationLogger.log(lifecycleSample(UiLocationSample.Kind.STOPPED)) }
    }

    private fun lifecycleSample(kind: UiLocationSample.Kind) = UiLocationSample(
        timestampMs = Clock.System.now().toEpochMilliseconds(),
        kind = kind,
        foreground = mapForeground.value,
        priority = LOCATION_PRIORITY_HIGH_ACCURACY,
    )

    /**
     * Emits once at start (regardless of connectivity — GPS works offline) and again on
     * every Offline→Online transition, so the spot subscription rebuilds immediately after
     * connectivity is restored even if the GPS position hasn't changed. Replaces the old
     * `reconnectTick` counter hack with the observer's own Flow. [HOME-ATOMIZE-001 F4]
     */
    private fun onlineAgain(): Flow<ConnectivityStatus> =
        connectivityObserver.status
            .onStart { emit(ConnectivityStatus.Online) }
            .distinctUntilChanged()
            .filter { it == ConnectivityStatus.Online }

    private fun subscribeMapFocusEvents() {
        mapFocusEventBus.events
            .collectSafely("mapFocus") { (lat, lon) -> sendEffect(HomeEffect.MoveCameraTo(lat, lon)) }
    }

    /** The cold-start nudge notification ("Marcar mi plaza" / tap) asks Home to drop the user straight
     *  into manual add-parking mode, tagged with the active vehicle so the peek shows its real glyph +
     *  name. We stay in Browse until the vehicles flow emits, then enter — it is backed by local Room
     *  data already synced at splash, so on cold start this resolves near-instantly with no
     *  generic-icon flash. The nudge only fires when a Coordinator vehicle exists, so it always
     *  arrives. [DET-TOGGLE-002] */
    private fun subscribeStartAddParkingRequests() {
        startAddParkingEventBus.requests
            .collectSafely("startAddParking") {
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
    }

    /** Drives the persistent detection-readiness banner [DET-READY-001g] and fires the transient
     *  "detection stopped" snackbar on a working→stopped drop. [DET-TOGGLE-002] */
    private fun subscribeDetectionReadiness() {
        observeDetectionReadiness()
            .distinctUntilChanged()
            .collectSafely("detectionReadiness") { readiness ->
                val wasWorking = state.value.detectionReadiness.toUiState().isDetectionWorking
                updateState { copy(detectionReadiness = readiness) }
                // Detection just dropped from a working state into a stopped one (Settings flag off, or
                // a producer/core permission revoked). Surface a snackbar with one-tap re-activation —
                // the persistent banner stays; this catches the moment of the change. [DET-TOGGLE-002]
                if (wasWorking && readiness.toUiState().isDetectionStopped) {
                    sendEffect(HomeEffect.DetectionStopped)
                }
            }
    }

    // ── Stream error policy ───────────────────────────────────────────────────

    /**
     * The ONE way this VM collects a Flow. Errors never kill the stream's host scope
     * and follow a single policy:
     *  - default (log-only): log and keep serving the last good value — right for repo/
     *    controller streams where the UI can keep rendering the cache;
     *  - [notify] set: ALSO surface a snackbar via [HomeEffect.ShowError] — reserved for
     *    streams whose silent failure would leave the user acting on stale CRITICAL data
     *    (active sessions) or wondering why a core capability vanished (GPS).
     * Every subscription — repos, controllers, event buses, permission/readiness — goes
     * through [collectSafely] or [collectInto]; none hand-rolls its own catch. [HOMEVM-CTRL-003]
     */
    private fun <T> Flow<T>.collectSafely(
        label: String,
        notify: ((Throwable) -> PaparcarError)? = null,
        action: suspend (T) -> Unit,
    ) {
        onEach { value -> action(value) }
            .catch { e ->
                PaparcarLogger.e(TAG, "$label stream error", e)
                notify?.let { sendEffect(HomeEffect.ShowError(it(e))) }
            }
            .launchIn(viewModelScope)
    }

    /** [collectSafely] specialised for the common "stream → single state write" case. */
    private fun <T> Flow<T>.collectInto(
        label: String,
        notify: ((Throwable) -> PaparcarError)? = null,
        apply: HomeState.(T) -> HomeState,
    ) = collectSafely(label, notify) { value -> updateState { apply(value) } }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Shared guard for the pin-mode confirms: no settled pin → GPS-unavailable snackbar. */
    private fun reportPinMissing() {
        sendEffect(HomeEffect.ShowError(PaparcarError.Location.ProviderDisabled))
    }

    // Geocoding implementations live in [HomeGeocodingController]. The VM
    // delegates via the [geocoder] field above — see its kdoc for the exact
    // cancellation policy and shimmer-flag invariants.
    private fun geocodeUserLocation(lat: Double, lon: Double) = geocoder.geocodeUserLocation(lat, lon)
    private fun geocodeCameraLocation(lat: Double, lon: Double) = geocoder.geocodeCameraLocation(lat, lon)

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

    // The pure `HomeState → HomeState` transitions of the mode machine (clearedModeFields,
    // applyNewSpots, resetSearch, pinCoordinates) live in HomeStateTransitions.kt, next to
    // the mode↔selection invariant they enforce. [HOMEVM-CTRL-004]

    // ── Constants ─────────────────────────────────────────────────────────────

    private companion object {
        const val TAG = "HomeViewModel"

        // Timing
        const val CAMERA_SETTLED_MS = 280L

        // Priority label stamped on UI-location samples — mirrors the android LocationRequest priority
        // behind observeHighAccuracyLocation(). [UI-LOC-FOREGROUND-001]
        const val LOCATION_PRIORITY_HIGH_ACCURACY = "HIGH_ACCURACY"

        // Map type preference strings
        const val MAP_TYPE_TERRAIN = "TERRAIN"
        const val MAP_TYPE_SATELLITE = "SATELLITE"
        const val MAP_TYPE_HYBRID = "HYBRID"
    }
}

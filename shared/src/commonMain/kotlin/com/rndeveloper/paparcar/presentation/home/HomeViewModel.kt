package com.rndeveloper.paparcar.presentation.home

import com.swmansion.kmpmaps.core.MapType
import com.rndeveloper.paparcar.isDebugBuild
import com.rndeveloper.paparcar.domain.ActivityRecognitionManager
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityObserver
import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.domain.entitlement.canCreateZone
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityStatus
import com.rndeveloper.paparcar.domain.detection.ports.DepartureWatchResumer
import com.rndeveloper.paparcar.domain.detection.DetectionRuntimeState
import com.rndeveloper.paparcar.domain.bluetooth.BluetoothScanner
import com.rndeveloper.paparcar.domain.detection.ports.ManualParkingDetection
import com.rndeveloper.paparcar.domain.detection.fence.VehicleFenceOwnershipPolicy
import com.rndeveloper.paparcar.domain.diagnostics.UiLocationLogger
import com.rndeveloper.paparcar.domain.diagnostics.UiLocationSample
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.location.LocationDataSource
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.model.DetectionReliabilityLevel
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.presentation.home.model.ViewportRefetch
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.ParkingReleaseReason
import com.rndeveloper.paparcar.domain.usecase.detection.ObserveDetectionReadinessUseCase
import com.rndeveloper.paparcar.domain.usecase.detection.ObserveDetectionReliabilityUseCase
import com.rndeveloper.paparcar.domain.detection.isPromptWindowOpen
import com.rndeveloper.paparcar.domain.detection.shouldShowParkNudgeBanner
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.usecase.parking.ClearParkNudgeUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.ObserveParkedVehiclesUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.ReleaseActiveParkingSessionUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.SaveManualParkingUseCase
import com.rndeveloper.paparcar.domain.usecase.vehicle.DeclareActiveVehicleUseCase
import com.rndeveloper.paparcar.domain.usecase.spot.ReportManualSpotUseCase
import com.rndeveloper.paparcar.domain.usecase.user.SetFirstStepsDismissedUseCase
import com.rndeveloper.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import com.rndeveloper.paparcar.domain.model.SpotVoteOutcome
import com.rndeveloper.paparcar.domain.usecase.spot.SendSpotSignalUseCase
import com.rndeveloper.paparcar.presentation.util.distanceMeters
import com.rndeveloper.paparcar.domain.event.MapFocusEventBus
import com.rndeveloper.paparcar.domain.event.ShowParkingAskEventBus
import com.rndeveloper.paparcar.domain.event.StartAddParkingEventBus
import kotlinx.coroutines.withTimeoutOrNull
import com.rndeveloper.paparcar.domain.usecase.zone.SaveOrUpdateZoneUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.presentation.base.BaseViewModel
import com.rndeveloper.paparcar.presentation.home.model.isDetectionStopped
import com.rndeveloper.paparcar.presentation.home.model.isDetectionWorking
import com.rndeveloper.paparcar.presentation.home.model.toUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
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
import com.rndeveloper.paparcar.domain.usecase.fuel.GetNearbyFuelStationsUseCase
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
import com.rndeveloper.paparcar.domain.usecase.parkings.GetNearbyPublicParkingsUseCase

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class HomeViewModel(
    private val permissionManager: PermissionManager,
    private val locationDataSource: LocationDataSource,
    private val activityRecognitionManager: ActivityRecognitionManager,
    private val connectivityObserver: ConnectivityObserver,
    private val observeDetectionReadiness: ObserveDetectionReadinessUseCase,
    private val observeDetectionReliability: ObserveDetectionReliabilityUseCase,
    private val detectionRuntime: DetectionRuntimeState,
    private val reportManualSpot: ReportManualSpotUseCase,
    private val reportSpotReleased: ReportSpotReleasedUseCase,
    private val sendSpotSignal: SendSpotSignalUseCase,
    private val userParkingRepository: UserParkingRepository,
    private val saveManualParking: SaveManualParkingUseCase,
    private val releaseSession: ReleaseActiveParkingSessionUseCase,
    private val observeParkedVehicles: ObserveParkedVehiclesUseCase,
    private val vehicleRepository: VehicleRepository,
    // Declaring the active vehicle also swaps the OS geofences to it. [VEH-ACTIVE-FENCE-001]
    private val declareActiveVehicle: DeclareActiveVehicleUseCase,
    private val zoneRepository: ZoneRepository,
    private val saveOrUpdateZone: SaveOrUpdateZoneUseCase,
    // The Plus gate over the zone-alert surfaces: the bell and the form toggle route free
    // users to the paywall instead of opening/enabling. [MONEY-001]
    private val observeIsPlus: ObserveIsPlusUseCase,
    private val appPreferences: AppPreferences,
    // Resolves the pending "where did you leave your car?" nudge (record + tray notification) —
    // from the banner's explicit dismiss and from the reactive janitor. [DET-NUDGE-PERSIST-001]
    private val clearParkNudge: ClearParkNudgeUseCase,
    // [DET-ASK-STATE-001] Read for ONE number: how long a posted "did you park?" question stays
    // answerable. Taken from the detector's own config rather than restated here, so the row can
    // never outlive the window the coordinator is still honouring.
    private val detectionConfig: ParkingDetectionConfig,
    // "Where to refuel": only consulted when the user unfolds the section.
    // [FUEL-STATIONS-LIST-001]
    private val getNearbyFuelStations: GetNearbyFuelStationsUseCase,
    private val mapFocusEventBus: MapFocusEventBus,
    private val startAddParkingEventBus: StartAddParkingEventBus,
    private val manualParkingDetection: ManualParkingDetection,
    // Rebuilds the resident departure watcher behind the "Reactivate" CTA of the interrupted-watch
    // row — the same resumer the app uses to close a watch gap while it is foreground.
    // [DET-WATCH-REACTIVATE-001]
    private val departureWatchResumer: DepartureWatchResumer,
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
    // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] Asked ONE question, once: does this
    // phone have any bonded device? That is what decides whether the fortify step can offer to link
    // the car by Bluetooth or would be sending the user to an empty list.
    private val bluetoothScanner: BluetoothScanner,
    // [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001] Closing the checklist
    // is the one piece of tutorial state that belongs to the ACCOUNT, so it does not go straight to
    // the device preference: this writes both, and it is the same verb Settings uses to replay it.
    private val setFirstStepsDismissed: SetFirstStepsDismissedUseCase,
    // The widget's "show this parking" ask — nullable so existing test doubles need no change.
    // [WIDGET-ACTIONS-002]
    private val showParkingAskEventBus: ShowParkingAskEventBus? = null,
    // ⛔ These two go LAST, and that is load-bearing: Koin builds this VM with a positional list of
    // bare `get()`s (see PresentationModule), so a parameter inserted in the MIDDLE shifts every
    // argument after it. Adding one here cost exactly that mistake once. [PARKINGS-ON-THE-MAP-001]
    private val getNearbyPublicParkings: GetNearbyPublicParkingsUseCase,
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

    // [FUEL-LAYER-FOLLOWS-THE-CAMERA-001] Debounce of the viewport ask, and the last camera centre
    // (for the moments the layer needs a centre before any settle — toggle-on, first fix).
    private var fuelViewportJob: Job? = null
    private var parkingsViewportJob: Job? = null
    private var lastCameraLat: Double? = null
    private var lastCameraLon: Double? = null

    /** Whether the Home map is foreground (RESUMED). Drives [subscribeGpsLocation]: the high-accuracy
     *  user-location request runs ONLY while true, so a backgrounded map costs no GPS. Starts false;
     *  the screen flips it via [HomeIntent.SetMapForeground] on resume/pause. [UI-LOC-FOREGROUND-001] */
    private val mapForeground = MutableStateFlow(false)

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        updateState {
            copy(
                mapType = appPreferences.defaultMapType.toMapType(),
                showFuelStationsLayer = appPreferences.showFuelStationsLayer,
                showPublicParkingsLayer = appPreferences.showPublicParkingsLayer,
            )
        }
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
        observeIsPlus().collectInto("isPlus") { copy(isPlus = it) }
        subscribePermissions()
        subscribeGpsLocation()
        subscribeMapFocusEvents()
        subscribeStartAddParkingRequests()
        subscribeShowParkingAsks()
        subscribeDetectionReadiness()
        subscribeDetectionReliability()
        subscribeServicePresence()
        subscribeParkNudge()
        subscribePromptWindow()
        subscribeFirstSteps()
    }

    /**
     * First-run location ask, ON the map — the map behind the dialog is its own context, so there
     * is no permissions screen in the cold-start chain any more. Fired from the first
     * [HomeIntent.SetMapForeground] with the map visible. One shot for the whole install:
     * [AppPreferences.hasRequestedLocationPermission] is stamped before launching, so a denial is
     * never re-asked at every entry — after that, the Blocked·CORE row and Settings own the
     * re-ask (via the Focus.Core repair screen). [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
     */
    private fun askFineLocationOnFirstEntry() {
        val hasLocation = permissionManager.permissionState.value.hasLocationPermission
        if (hasLocation || appPreferences.hasRequestedLocationPermission) return
        appPreferences.setLocationPermissionRequested()
        // If this very ask is granted, pitch auto-detection right after (see subscribePermissions):
        // the user just granted location and is looking at the map — peak engagement for the app's
        // star feature, which no longer has a first-run screen to introduce it.
        pitchDetectionAfterGrant = true
        sendEffect(HomeEffect.RequestLocationPermission)
    }

    /** Armed by [askFineLocationOnFirstEntry]; consumed on the grant. Session-scoped on purpose:
     *  losing the pitch to process death costs nothing — the banner and checklist keep the ask. */
    private var pitchDetectionAfterGrant = false

    /**
     * [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] The guided checklist's persisted half → state,
     * plus the LATCH: whenever the live projection reports more steps done than are banked, bank
     * them.
     *
     * The banking has to happen somewhere with a coroutine scope, and it is deliberately NOT in the
     * projection: `resolveFirstSteps` stays a pure function of what it is given, which is why the
     * "when is a step done" rule can be tested without a ViewModel at all. Here we only WRITE what
     * that function already concluded.
     */
    private fun subscribeFirstSteps() {
        combine(
            appPreferences.observeFirstStepsDone(),
            appPreferences.observeFirstStepsDismissed(),
            appPreferences.observeFirstStepsDeferred(),
        ) { done, dismissed, deferred -> Triple(done, dismissed, deferred) }
            .collectSafely("firstSteps") { (done, dismissed, deferred) ->
                updateState {
                    copy(
                        firstStepsDone = done,
                        firstStepsDismissed = dismissed,
                        firstStepsDeferred = deferred,
                    )
                }
            }

        // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] Whether linking a car by Bluetooth is
        // even offerable: read ONCE, not per emission — it is an IPC call into the adapter, and the
        // answer only changes when the user pairs a device in system settings. False while the
        // permission is missing or Bluetooth is off, which is the honest answer for "can I pick a MAC
        // from a list right now".
        viewModelScope.launch {
            val paired = runCatching { bluetoothScanner.getBondedDevices().isNotEmpty() }.getOrDefault(false)
            updateState { copy(hasPairedBluetoothDevices = paired) }
        }

        // A step whose signal is live RIGHT NOW gets written to the latch, so it survives the signal
        // going away (releasing the parking) and a process death. `distinctUntilChanged` + the
        // superset check keep this from writing on every state emission.
        state
            .map { it.firstSteps.done }
            .distinctUntilChanged()
            .collectSafely("firstStepsLatch") { effective ->
                if (!state.value.firstStepsDone.containsAll(effective)) {
                    appPreferences.setFirstStepsDone(effective)
                }
            }
    }

    /**
     * [DET-ASK-STATE-001] The open "did you park?" question → state.
     *
     * The record is written and cleared by the notification adapter at the two verbs of the
     * confirmation channel, so this only has to decide whether what is stored is still ANSWERABLE.
     * No ticker: the two closes that never reach us (the user swipes the notification away; the OS
     * kills the process mid-window) both happen while the app is not in the foreground, so testing
     * the clock as the state re-emits is enough — and the ordinary closes arrive as a `null` from
     * the flow itself, which is what makes the row disappear the instant the user answers in the
     * tray.
     */
    private fun subscribePromptWindow() {
        appPreferences.observePendingPromptWindow()
            .collectSafely("pendingPromptWindow") { window ->
                val open = isPromptWindowOpen(
                    window = window,
                    nowMs = Clock.System.now().toEpochMilliseconds(),
                    timeoutMs = detectionConfig.confirmationResponseTimeoutMs,
                )
                updateState { copy(promptWindow = window.takeIf { open }) }
            }
    }

    /**
     * [DET-NUDGE-PERSIST-001] The durable "where did you leave your car?" question → state, plus
     * the reactive janitor: when an active session for the nudged vehicle (re)appears — the
     * confirm raced the store clear, or a remote sync restored the session — the question answered
     * itself, so resolve BOTH surfaces (record + tray notification) instead of leaving a ghost.
     */
    private fun subscribeParkNudge() {
        combine(
            appPreferences.observePendingParkNudge(),
            userParkingRepository.observeActiveSessions(),
        ) { nudge, sessions -> nudge to sessions }
            .collectSafely("pendingParkNudge") { (nudge, sessions) ->
                if (nudge != null && !shouldShowParkNudgeBanner(nudge, sessions)) {
                    clearParkNudge()
                    return@collectSafely
                }
                updateState { copy(pendingParkNudge = nudge) }
            }
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
            // Rides with the spots: same sheet, same CORE gate, same "what is around me" question.
            // [FUEL-STATIONS-LIST-001] [FUEL-STATIONS-MAP-AND-PEEK-001]
            is HomeIntent.ToggleFuelStations,
            is HomeIntent.ToggleFuelStationsLayer,
            is HomeIntent.TogglePublicParkings,
            is HomeIntent.TogglePublicParkingsLayer,
            is HomeIntent.SetFreeSpotsSectionExpanded,
            is HomeIntent.SendSpotSignal,
            is HomeIntent.EnterReportMode,
            is HomeIntent.ExitReportMode,
            is HomeIntent.ConfirmReportSpot,
            is HomeIntent.SetReportingSize,
            is HomeIntent.ReportTestSpot,
            -> handleSpotIntent(intent)

            // Own parking sessions
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
            is HomeIntent.SetZoneNotify,
            is HomeIntent.SelectZone,
            is HomeIntent.DeleteZone,
            is HomeIntent.EnterEditZoneMode,
            -> handleZoneIntent(intent)

            // Detection controls
            is HomeIntent.StartDrivingDetection,
            is HomeIntent.AnswerParkingPrompt,
            is HomeIntent.StopDetection,
            is HomeIntent.EnableAutoDetection,
            is HomeIntent.RequestBatteryExemption,
            is HomeIntent.ResumeWatch,
            is HomeIntent.DismissParkNudge,
            -> handleDetectionIntent(intent)

            // One-time detection pitch (post first-entry location grant).
            // [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
            is HomeIntent.AcceptDetectionPitch -> {
                updateState { copy(showDetectionPitch = false) }
                handleIntent(HomeIntent.EnableAutoDetection)
            }
            is HomeIntent.DismissDetectionPitch ->
                updateState { copy(showDetectionPitch = false) }

            // Guided first steps [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001]
            is HomeIntent.DismissFirstSteps,
            is HomeIntent.CompleteFirstStep,
            is HomeIntent.DeferFirstStep,
            is HomeIntent.ResumeFirstStep,
            -> handleFirstStepsIntent(intent)
        }
    }

    // ── Guided first steps ────────────────────────────────────────────────────

    private fun handleFirstStepsIntent(intent: HomeIntent) {
        when (intent) {
            // [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001] Device AND
            // account: "I closed this" used to live only in the DataStore, so a reinstall handed the
            // tutorial back to a user who had already finished it.
            is HomeIntent.DismissFirstSteps -> viewModelScope.launch { setFirstStepsDismissed(true) }
            is HomeIntent.CompleteFirstStep ->
                appPreferences.setFirstStepsDone(state.value.firstStepsDone + intent.step)
            // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] Written to its OWN set: the user
            // said "not yet", which is an answer, not an achievement.
            is HomeIntent.DeferFirstStep ->
                appPreferences.setFirstStepsDeferred(state.value.firstStepsDeferred + intent.step)
            // Changed their mind: the step goes back to being asked for.
            is HomeIntent.ResumeFirstStep ->
                appPreferences.setFirstStepsDeferred(state.value.firstStepsDeferred - intent.step)
            else -> Unit
        }
    }

    // ── Map & camera ──────────────────────────────────────────────────────────

    private fun handleMapIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.CameraPositionChanged -> onCameraPositionChanged(intent.lat, intent.lon)
            is HomeIntent.RecenterSpots -> onRecenterSpots()
            is HomeIntent.SetMapType -> setMapType(intent.type)
            is HomeIntent.SetMapForeground -> {
                mapForeground.value = intent.active
                // The map just came on screen — the one moment the first-run location ask belongs
                // to (the dialog opens OVER the map, its own context). NOT in init: an effect sent
                // there is emitted before the screen collects and a replay-less SharedFlow drops
                // it. [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
                if (intent.active) askFineLocationOnFirstEntry()
            }
            else -> Unit
        }
    }

    private fun onCameraPositionChanged(lat: Double, lon: Double) {
        lastCameraLat = lat
        lastCameraLon = lon
        if (state.value.mode !is HomeMode.Browse) {
            updatePinDuringMode(lat, lon)
        } else if (spots.maybeRecenterOnPan(lat, lon)) {
            updateState { copy(isSpotQueryCenteredOnUser = false) }
        }
        geocodeCameraLocation(lat, lon)
        // Browse only: while a pin is being placed the map is the pin's canvas, not a browser.
        // [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]
        if (state.value.mode is HomeMode.Browse) {
            scheduleFuelViewportRefetch(lat, lon)
            scheduleParkingsViewportRefetch(lat, lon)
        }
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
                searchNoResults = if (blank) false else searchNoResults,
            )
        }
        search.onQueryChanged(query)
    }

    // ── Community spots ───────────────────────────────────────────────────────

    /**
     * Folds the refuel section, or unfolds it and — the first time only — goes and asks.
     * [FUEL-STATIONS-LIST-001]
     *
     * Nothing is fetched while it stays folded: most sessions never ask this question, and Spain's
     * feed is downloaded a province at a time. Closing it keeps what was already fetched, so folding
     * and unfolding again costs nothing.
     */
    private fun toggleFuelStations() {
        val slice = state.value.fuel
        if (slice.isExpanded) {
            updateState { copy(fuel = fuel.copy(isExpanded = false)) }
            return
        }

        // The fuel of the car being driven, or the active one — the price shown has to be the price
        // of what THIS driver actually puts in. ⛔ Without one there is no question to ask, and that
        // is SAID as its own state, EVEN when the map layer already loaded unpriced stations:
        // rendering it as an empty (or priceless) list fabricated a claim no source ever answered.
        // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
        val fuelType = activeFuelType()
        if (fuelType == null) {
            updateState {
                copy(fuel = fuel.copy(isExpanded = true, missingFuelType = true, failed = false))
            }
            return
        }

        // Already answered FOR THIS FUEL, or already asking: unfolding again must not re-ask. An
        // answer the layer fetched fuel-less is a different question — repriced below.
        if (slice.isLoading || (slice.stations.isNotEmpty() && slice.fetchedFor == fuelType)) {
            updateState { copy(fuel = fuel.copy(isExpanded = true, missingFuelType = false)) }
            return
        }

        // No fix means the question could not be asked — the failure sentence, never the empty one.
        val gps = state.value.userGpsPoint
        if (gps == null) {
            updateState {
                copy(fuel = fuel.copy(isExpanded = true, failed = true, missingFuelType = false))
            }
            return
        }

        updateState { copy(fuel = fuel.copy(isExpanded = true)) }
        loadFuelStations(gps, fuelType)
    }

    /**
     * Show or hide the fuel-stations map layer. [FUEL-STATIONS-MAP-AND-PEEK-001]
     *
     * Turning it on asks around the CAMERA's centre (what you are looking at), fuel-less when the
     * car declares none — markers without a figure are honest; a hidden layer is not. Turning it
     * off clears a fuel-station selection so no peek keeps talking about an invisible pin.
     * [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]
     */
    private fun toggleFuelStationsLayer() {
        val show = !state.value.showFuelStationsLayer
        appPreferences.setShowFuelStationsLayer(show)
        updateState {
            copy(
                showFuelStationsLayer = show,
                selection = selection.takeUnless { !show && it is HomeSelection.FuelStation },
            )
        }
        if (show) viewportCentreOrGps()?.let { (lat, lon) -> maybeFetchFuelViewport(lat, lon) }
    }

    /**
     * The layer follows the CAMERA. [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]
     *
     * Called with the point the camera settled on (debounced by [scheduleFuelViewportRefetch]) or
     * with the best-known centre when the layer turns on / the first fix lands. The pure
     * [ViewportRefetch] decides whether the move earns a fresh ask.
     *
     * ⛔ **A failed ask puts the anchor BACK.** Advancing it on the attempt looked like sensible
     * anti-hammering until a transient Overpass 504 left Paris permanently blank: the layer had
     * "already asked here" and would not try again until the user panned kilometres away. There is
     * no hammering to prevent — asks only fire on camera events, which only exist while the user
     * moves the map. Failure logs and keeps whatever stations were showing (the layer has no error
     * sentence of its own; the section owns those).
     */
    private fun maybeFetchFuelViewport(lat: Double, lon: Double) {
        val s = state.value
        if (!s.showFuelStationsLayer || s.fuelLayer.isLoading) return
        // Not a place (0,0 before the map has a camera), or too far from the user to be a refuel
        // question. Past the limit the layer EMPTIES: showing your own city's stations over a
        // distant one is the lie this ticket exists to stop.
        if (!ViewportRefetch.isWorthAsking(lat, lon, s.userGpsPoint?.latitude, s.userGpsPoint?.longitude)) {
            if (s.fuelLayer.stations.isNotEmpty()) {
                updateState {
                    copy(
                        // The anchor goes too: coming back must ASK again, not restore a store the
                        // layer already emptied.
                        fuelLayer = fuelLayer.copy(
                            stations = emptyList(),
                            anchorLat = null,
                            anchorLon = null,
                        ),
                        selection = selection.takeUnless { it is HomeSelection.FuelStation },
                    )
                }
            }
            return
        }
        val previousAnchorLat = s.fuelLayer.anchorLat
        val previousAnchorLon = s.fuelLayer.anchorLon
        if (!ViewportRefetch.shouldRefetch(
                s.fuelLayer.anchorLat, s.fuelLayer.anchorLon,
                lat, lon,
                FuelStationsQuery.DEFAULT_RADIUS_METERS,
            )
        ) return

        updateState {
            copy(fuelLayer = fuelLayer.copy(anchorLat = lat, anchorLon = lon, isLoading = true))
        }
        viewModelScope.launch {
            getNearbyFuelStations(lat, lon, activeFuelType())
                .onSuccess { nearby ->
                    // The layer's own verdict, quotable in a diagnosis: WHERE it asked and what
                    // came back. Silence used to be ambiguous between "it worked" and "it never
                    // fired", which cost a device session. [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]
                    PaparcarLogger.d(
                        TAG,
                        "fuel viewport: ${nearby.stations.size} stations around $lat,$lon",
                    )
                    updateState {
                        copy(
                            fuelLayer = fuelLayer.copy(isLoading = false, stations = nearby.stations),
                            // A selection that survives in NEITHER store is orphaned — same pruning
                            // rule as applyNewSpots, scoped to its own kind.
                            selection = selection.takeUnless { sel ->
                                sel is HomeSelection.FuelStation &&
                                    nearby.stations.none { it.station.id == sel.id } &&
                                    fuel.stations.none { it.station.id == sel.id }
                            },
                        )
                    }
                }
                .onFailure { e ->
                    PaparcarLogger.w(TAG, "fuel viewport lookup failed", e)
                    updateState {
                        copy(
                            fuelLayer = fuelLayer.copy(
                                isLoading = false,
                                anchorLat = previousAnchorLat,
                                anchorLon = previousAnchorLon,
                            ),
                        )
                    }
                }
        }
    }

    /** Debounced camera-settle trigger for the viewport ask — a nervous pan is not a question. */
    private fun scheduleFuelViewportRefetch(lat: Double, lon: Double) {
        if (!state.value.showFuelStationsLayer) return
        fuelViewportJob?.cancel()
        fuelViewportJob = viewModelScope.launch {
            delay(FUEL_VIEWPORT_SETTLE_MS)
            maybeFetchFuelViewport(lat, lon)
        }
    }

    /** Best-known viewport centre for the moments no camera event has fired yet (cold start). */
    private fun viewportCentreOrGps(): Pair<Double, Double>? =
        lastCameraLat?.let { lat -> lastCameraLon?.let { lon -> lat to lon } }
            ?: state.value.userGpsPoint?.let { it.latitude to it.longitude }

    private fun activeFuelType() = state.value.vehicles.firstOrNull { it.isActive }?.fuelType
        ?: state.value.vehicles.firstOrNull()?.fuelType

    /** The ONE fetch both surfaces share — section rows and map markers read the same answer. */
    private fun loadFuelStations(gps: GpsPoint, fuelType: FuelType?) {
        updateState {
            copy(fuel = fuel.copy(isLoading = true, failed = false, missingFuelType = false))
        }
        viewModelScope.launch {
            getNearbyFuelStations(gps.latitude, gps.longitude, fuelType)
                .onSuccess { nearby ->
                    updateState {
                        copy(
                            fuel = fuel.copy(
                                isLoading = false,
                                failed = false,
                                stations = nearby.stations,
                                reportedAt = nearby.reportedAt,
                                fetchedFor = nearby.fuel,
                            ),
                        )
                    }
                }
                // ⛔ A failure is NOT an empty list: the section says "we could not ask", never
                // "there are none nearby", which would send the user driving on an unchecked claim.
                // And it is logged: a swallowed exception here left a real device failure
                // undiagnosable. [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
                .onFailure { e ->
                    PaparcarLogger.w(TAG, "fuel stations lookup failed", e)
                    updateState { copy(fuel = fuel.copy(isLoading = false, failed = true)) }
                }
        }
    }

    // ── Public parkings ───────────────────────────────────────────────────────
    // Same three rules as fuel, and for the same reasons: one fetch feeds the section AND the layer,
    // a failure is never rendered as "none nearby", and switching the layer off drops a selection
    // that would otherwise keep a peek talking about an invisible pin. [PARKINGS-ON-THE-MAP-001]

    private fun togglePublicParkings() {
        val slice = state.value.parkings
        if (slice.isExpanded) {
            updateState { copy(parkings = parkings.copy(isExpanded = false)) }
            return
        }
        // Already answered, or already asking: unfolding again must not re-ask. Unlike fuel there is
        // no per-vehicle question here — a car park is a car park whatever the car burns.
        if (slice.isLoading || slice.parkings.isNotEmpty()) {
            updateState { copy(parkings = parkings.copy(isExpanded = true)) }
            return
        }
        val gps = state.value.userGpsPoint
        if (gps == null) {
            updateState { copy(parkings = parkings.copy(isExpanded = true, failed = true)) }
            return
        }
        updateState { copy(parkings = parkings.copy(isExpanded = true)) }
        loadPublicParkings(gps)
    }

    private fun togglePublicParkingsLayer() {
        val show = !state.value.showPublicParkingsLayer
        appPreferences.setShowPublicParkingsLayer(show)
        updateState {
            copy(
                showPublicParkingsLayer = show,
                selection = selection.takeUnless { !show && it is HomeSelection.PublicParking },
            )
        }
        if (show) viewportCentreOrGps()?.let { (lat, lon) -> maybeFetchParkingsViewport(lat, lon) }
    }

    /**
     * The car-parks layer follows the CAMERA, exactly like the fuel one.
     * [PARKINGS-ON-THE-MAP-001] [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]
     *
     * ⛔ It does NOT re-check itself on every GPS fix, and that is a fix rather than a style choice:
     * the first version of this lane did, and a failed ask over an empty store meant the next fix
     * asked again — one request every ~5 s against a free shared service, measured on device.
     * Camera events only exist while the user moves the map, so there is no hammering left to
     * prevent.
     *
     * ⛔ **A failed ask puts the anchor BACK**, for the reason the fuel lane already paid for: an
     * anchor advanced on a transient failure means "already asked here", and the area stays blank
     * until the user pans kilometres away.
     */
    private fun maybeFetchParkingsViewport(lat: Double, lon: Double) {
        val s = state.value
        if (!s.showPublicParkingsLayer || s.parkingsLayer.isLoading) return
        // Not a place (0,0 before the map has a camera), or so far from the user that showing your
        // own city's car parks over a distant one would be the lie, not the service.
        if (!ViewportRefetch.isWorthAsking(lat, lon, s.userGpsPoint?.latitude, s.userGpsPoint?.longitude)) {
            if (s.parkingsLayer.parkings.isNotEmpty()) {
                updateState {
                    copy(
                        parkingsLayer = parkingsLayer.copy(
                            parkings = emptyList(),
                            anchorLat = null,
                            anchorLon = null,
                        ),
                        selection = selection.takeUnless { it is HomeSelection.PublicParking },
                    )
                }
            }
            return
        }
        val previousAnchorLat = s.parkingsLayer.anchorLat
        val previousAnchorLon = s.parkingsLayer.anchorLon
        if (!ViewportRefetch.shouldRefetch(
                s.parkingsLayer.anchorLat, s.parkingsLayer.anchorLon,
                lat, lon,
                PublicParkingsQuery.DEFAULT_RADIUS_METERS,
            )
        ) return

        updateState {
            copy(parkingsLayer = parkingsLayer.copy(anchorLat = lat, anchorLon = lon, isLoading = true))
        }
        viewModelScope.launch {
            getNearbyPublicParkings(lat, lon)
                .onSuccess { found ->
                    PaparcarLogger.d(TAG, "parkings viewport: ${found.size} car parks around $lat,$lon")
                    updateState {
                        copy(
                            parkingsLayer = parkingsLayer.copy(isLoading = false, parkings = found),
                            // A selection alive in NEITHER store is orphaned — same pruning rule as
                            // the fuel lane, scoped to its own kind.
                            selection = selection.takeUnless { sel ->
                                sel is HomeSelection.PublicParking &&
                                    found.none { it.parking.id == sel.id } &&
                                    parkings.parkings.none { it.parking.id == sel.id }
                            },
                        )
                    }
                }
                .onFailure { e ->
                    PaparcarLogger.w(TAG, "parkings viewport lookup failed", e)
                    updateState {
                        copy(
                            parkingsLayer = parkingsLayer.copy(
                                isLoading = false,
                                anchorLat = previousAnchorLat,
                                anchorLon = previousAnchorLon,
                            ),
                        )
                    }
                }
        }
    }

    /** Debounced camera-settle trigger, the twin of the fuel one. */
    private fun scheduleParkingsViewportRefetch(lat: Double, lon: Double) {
        if (!state.value.showPublicParkingsLayer) return
        parkingsViewportJob?.cancel()
        parkingsViewportJob = viewModelScope.launch {
            delay(FUEL_VIEWPORT_SETTLE_MS)
            maybeFetchParkingsViewport(lat, lon)
        }
    }

    /** The section's own fetch, anchored to the USER — a different question from the layer's. */
    private fun loadPublicParkings(gps: GpsPoint) {
        updateState { copy(parkings = parkings.copy(isLoading = true, failed = false)) }
        viewModelScope.launch {
            getNearbyPublicParkings(gps.latitude, gps.longitude)
                .onSuccess { found ->
                    updateState {
                        copy(parkings = parkings.copy(isLoading = false, failed = false, parkings = found))
                    }
                }
                // ⛔ A failure is NOT an empty list: the section says "we could not ask", never
                // "there are none nearby". [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
                .onFailure { e ->
                    PaparcarLogger.w(TAG, "public parkings lookup failed", e)
                    updateState { copy(parkings = parkings.copy(isLoading = false, failed = true)) }
                }
        }
    }

    private fun handleSpotIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.LoadNearbySpots -> {
                // No permission nag here: the first-entry ask lives in askFineLocationOnFirstEntry
                // and later re-asks belong to the Blocked·CORE row. (This branch used to emit
                // RequestLocationPermission into a no-op handler — asking into the void.)
                // [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
            }
            is HomeIntent.SelectItem -> updateState {
                clearedModeFields().copy(selection = intent.selection)
            }
            is HomeIntent.SetSizeFilter -> updateState { copy(sizeFilter = intent.size) }
            is HomeIntent.ToggleFuelStations -> toggleFuelStations()
            is HomeIntent.ToggleFuelStationsLayer -> toggleFuelStationsLayer()
            is HomeIntent.TogglePublicParkings -> togglePublicParkings()
            is HomeIntent.TogglePublicParkingsLayer -> togglePublicParkingsLayer()
            is HomeIntent.SetFreeSpotsSectionExpanded ->
                updateState { copy(freeSpotsExpandedOverride = intent.expanded) }
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
                    // [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] A REPORTED spot is the other
                    // way to have met the community half, and it is banked on the write succeeding —
                    // not on entering the mode. Pressing the step's button teaches nothing; getting
                    // a spot published does.
                    handleIntent(HomeIntent.CompleteFirstStep(FirstStep.FIND_SPOT))
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
        val current = state.value
        // The distance is measured HERE, from the state that owns both the fix and the spot, so the
        // gate cannot be bypassed by a call site that forgets to pass it.
        // [SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001]
        val spot = current.nearbySpots.firstOrNull { it.id == spotId }
        val distance = spot?.let { s ->
            current.userGpsPoint?.let { me ->
                distanceMeters(me.latitude, me.longitude, s.location.latitude, s.location.longitude).toDouble()
            }
        }

        updateState { copy(inFlightSpotSignals = inFlightSpotSignals + spotId) }
        viewModelScope.launch {
            sendSpotSignal(spotId, accepted, distance)
                .onSuccess { outcome ->
                    if (outcome != SpotVoteOutcome.IGNORED_TOO_FAR) {
                        updateState { copy(votedSpotIds = votedSpotIds + spotId) }
                    }
                    sendEffect(HomeEffect.SpotSignalSent(outcome))
                }
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
            is HomeIntent.ReleaseParking -> releaseParking(intent.sessionId, intent.reason)
            is HomeIntent.EnterAddParkingMode -> updateState {
                clearedModeFields().copy(
                    mode = HomeMode.AddingParking,
                    pinCameraLat = intent.initialGps?.latitude,
                    pinCameraLon = intent.initialGps?.longitude,
                    editingParkingId = intent.editingParkingId,
                    addingParkingVehicleId = intent.targetVehicleId,
                    // [DET-A-NUDGE-MUST-SAY-WHO-ASKED-001] Resolve WHO asked here and once, from the
                    // pending nudge this VM already observes for the banner — not plumbed through
                    // the notification intent and the event bus, which would be the same fact
                    // travelling a second road. Captured at ENTER, not at save: the nudge is cleared
                    // when a session confirms, and the user may sit in pin mode for minutes.
                    // Empty string = asked, source unknown (nudge already gone); null = nobody asked.
                    addingParkingNudgeSource =
                        if (intent.fromDetectionNudge) pendingParkNudge?.source.orEmpty() else null,
                )
            }
            is HomeIntent.ExitAddParkingMode -> updateState { clearedModeFields() }
            is HomeIntent.ConfirmAddParking -> confirmAddParking(intent.asNewSession)
            else -> Unit
        }
    }

    private fun confirmAddParking(asNewSession: Boolean) {
        val current = state.value
        if (current.mode !is HomeMode.AddingParking || current.isSavingParking) return
        val (lat, lon) = current.pinCoordinates() ?: return reportPinMissing()
        // Re-park ("I parked somewhere else") from edit mode: drop the edited session's id so a
        // NEW session is created for the SAME vehicle — the model supersedes the old one on save.
        // Correct-in-place keeps the id. [UX-PARKED-STATE-001]
        val editedSessionVehicleId = current.editingParkingId
            ?.let { id -> current.activeSessions.firstOrNull { it.id == id }?.vehicleId }
        val editingParkingId = if (asNewSession) null else current.editingParkingId
        val targetVehicleId = if (asNewSession) editedSessionVehicleId else current.addingParkingVehicleId
        // No connectivity gate: sessions are local-first (Room now, Firestore mirrored by
        // the WorkManager sync queue), same as the auto-detection confirm. [OFFLINE-PARK-001]
        // Record WHICH confirm is running — correct-in-place and re-park sit in the same peek, and
        // the spinner has to land on the one that was pressed.
        // [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
        updateState { copy(savingParkingAsNewSession = asNewSession) }
        viewModelScope.launch {
            saveManualParking(
                lat = lat,
                lon = lon,
                accuracy = current.userGpsPoint?.accuracy ?: 0f,
                editingParkingId = editingParkingId,
                targetVehicleId = targetVehicleId,
                // Detection-nudge entry keeps detection provenance on the confirmed pin; only
                // meaningful on CREATE — a move keeps the session's own type. [DET-NUDGE-PIN-PROVENANCE-001]
                nudgeSource = current.addingParkingNudgeSource,
            )
                .onSuccess { updateState { clearedModeFields().copy(savingParkingAsNewSession = null) } }
                .onFailure {
                    // [BUG-8] Keep the user in AddingParking with the pin intact so they
                    // can retry. Only flip the spinner off. Going back to Browse on failure
                    // would force the user to re-centre the camera and reconfirm.
                    updateState { copy(savingParkingAsNewSession = null) }
                    sendEffect(HomeEffect.ShowError(PaparcarError.Parking.SaveFailed))
                }
        }
    }

    private fun releaseParking(sessionId: String, reason: ParkingReleaseReason) {
        // Resolve the tapped card's session explicitly — no `?: userParking` fallback. With two
        // active sessions, ranking-first could release the wrong car's spot. [VEH-ACTIVE-FENCE-001]
        val target = state.value.activeSessions.firstOrNull { it.id == sessionId }
        if (target == null) {
            PaparcarLogger.w(TAG, "releaseParking: no active session for id=$sessionId — no-op")
            sendEffect(HomeEffect.ShowError(PaparcarError.Parking.ReleaseFailed))
            return
        }
        // The reason came with the intent — keep it, so the dialog's spinner can sit on the option
        // the user chose (publish the freed spot, or not).
        // [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
        updateState { copy(releasingReason = reason) }
        viewModelScope.launch {
            // Leaving in this car IS the declaration that you drive it: declare it active (idempotent
            // when it already is) so detection + fences follow the right car. Same path as "I'm
            // driving"; the swap is handled inside DeclareActiveVehicle. [VEH-ACTIVE-FENCE-001]
            //
            // ...but only when the car has no identity of its own and the session really ended in a
            // departure. Deleting a wrong record declares nothing, and a BT-paired car is already
            // identified by its MAC — declaring it active would only strip the coordinator's car of
            // its one identity signal. The policy owns both conditions; this obeys.
            // [PARK-DELETE-NO-DECLARE-001] [DET-BT-OWNERSHIP-001]
            target.vehicleId?.let { vehicleId ->
                val isBtPaired = state.value.vehicles
                    .firstOrNull { it.id == vehicleId }?.bluetoothDeviceId != null
                if (VehicleFenceOwnershipPolicy.shouldDeclareActiveOnRelease(reason, isBtPaired)) {
                    declareActiveVehicle(vehicleId)
                        .onFailure { e -> PaparcarLogger.w(TAG, "release: declareActiveVehicle($vehicleId) failed", e) }
                }
            }
            releaseSession(target.location.latitude, target.location.longitude, target, reason)
                .onSuccess { published ->
                    // `clearedModeFields()` and not just `selection = null`: the "delete record"
                    // confirm lives INSIDE the AddingParking peek and no longer dismisses itself on
                    // tap, so leaving the mode is what closes it — once the write actually landed.
                    // From Browse (every departure release) this is the same no-op plus the
                    // selection reset it already did.
                    // [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
                    updateState { clearedModeFields().copy(releasingReason = null) }
                    // The use case says whether a spot ACTUALLY went out — a zone session wider
                    // than the publish ceiling withholds it, and thanking the user for a plaza
                    // that never existed would be the lie the gate removes.
                    // [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001]
                    if (published) sendEffect(HomeEffect.SpotReported)
                }
                .onFailure { e ->
                    updateState { copy(releasingReason = null) }
                    sendEffect(HomeEffect.ShowError(PaparcarError.Database.WriteError(e.message ?: "")))
                }
        }
    }

    // ── Habitual zones ────────────────────────────────────────────────────────

    private fun handleZoneIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.EnterAddZoneMode -> {
                updateState {
                    clearedModeFields().copy(
                        mode = HomeMode.AddingZone,
                        pinCameraLat = intent.lat,
                        pinCameraLon = intent.lon,
                    )
                }
                // Same centre, wider frame: sizing a zone means seeing its whole circle,
                // which the navigation zoom crops. [UI-ZONE-MANAGE-001]
                sendEffect(HomeEffect.MoveCameraTo(intent.lat, intent.lon, CameraFrame.ZoneEditing))
            }
            is HomeIntent.ExitAddZoneMode -> updateState { clearedModeFields() }
            is HomeIntent.ConfirmAddZone -> confirmAddZone()
            is HomeIntent.UpdateAddingZoneName -> updateState { copy(addingZoneName = intent.name) }
            is HomeIntent.UpdateAddingZoneIcon -> updateState { copy(addingZoneIconKey = intent.iconKey) }
            is HomeIntent.SetZoneRadius -> updateState { copy(addingZoneRadius = intent.radius) }
            is HomeIntent.SetZoneIsPrivate -> updateState { copy(addingZoneIsPrivate = intent.isPrivate) }
            // [MONEY-001] Soft gate: the switch is visible to everyone; ENABLING it as free is the
            // "attempt" that opens the paywall (the switch stays off). Disabling is always allowed.
            is HomeIntent.SetZoneNotify ->
                if (intent.notify && !state.value.isPlus) {
                    sendEffect(HomeEffect.NavigateToPaywall(PaywallReason.ZONE_ALERTS))
                } else {
                    updateState { copy(addingZoneNotify = intent.notify) }
                }
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
        // [FREE-LIMITS-001] Soft gate on CREATION only (an edit carries editingZoneId): free at
        // the limit → paywall, the zone is NOT saved and the form stays intact for after the
        // upgrade. Zones already above the limit are conserved — gate the NEW, keep the created.
        if (current.editingZoneId == null && !canCreateZone(current.zones.size, current.isPlus)) {
            sendEffect(HomeEffect.NavigateToPaywall(PaywallReason.ZONES))
            return
        }
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
                notifyOnSpot = current.addingZoneNotify,
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
                // Deleting the zone you are editing leaves the modal editing a ghost — close it
                // here, once, instead of asking every caller to remember. [UI-ZONE-MANAGE-001]
                // On SUCCESS only, and no longer the moment the tap lands: the confirm dialog now
                // holds its spinner until the write resolves, and a failure has to leave the peek
                // standing so the retry is one tap away.
                // [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
                .onSuccess { if (state.value.editingZoneId == zoneId) updateState { clearedModeFields() } }
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
                addingZoneNotify = zone.notifyOnSpot,
                editingZoneId = zoneId,
            )
        }
        sendEffect(HomeEffect.MoveCameraTo(zone.lat, zone.lon, CameraFrame.ZoneEditing))
    }

    private fun selectZone(zoneId: String) {
        val zone = state.value.zones.find { it.id == zoneId } ?: return
        // A zone is a navigation shortcut, not a thing to "manage": fly the camera
        // there and stay in Browse so the spots at the zone are immediately visible.
        // No selection state / management peek — managing it (icon, radius, privacy,
        // name, delete) lives in its own modal, opened by the chip's pencil.
        // [ZONE-NOSEL-001] [UI-ZONE-MANAGE-001]
        sendEffect(HomeEffect.MoveCameraTo(zone.lat, zone.lon))
    }

    // ── Detection controls ────────────────────────────────────────────────────

    private fun handleDetectionIntent(intent: HomeIntent) {
        when (intent) {
            // [DET-G-01b] "I'm driving" — start tracking now; the readiness flow flips to Monitoring
            // (the ephemeral pill) as the service marks itself running, which is the user's feedback.
            is HomeIntent.StartDrivingDetection -> startDrivingDetection(intent.vehicleId)

            // [DET-STOP-BUTTON-001] "Parar detección" — the trip ends with no spot saved and no
            // question asked. The row itself disappears as the service leaves Monitoring; the
            // snackbar is what tells the user the silence is deliberate and how to come back.
            is HomeIntent.StopDetection -> {
                manualParkingDetection.stopByUser()
                sendEffect(HomeEffect.TripDetectionStopped)
            }

            // [DET-ASK-STATE-001] The user answered the question from the Home row. Nothing is
            // written here: the answer takes the same route as the notification's buttons, and the
            // coordinator hook behind it dismisses the notification — which is what clears the
            // durable window and makes the row disappear, through the same flow, for both surfaces.
            is HomeIntent.AnswerParkingPrompt -> manualParkingDetection.answerPrompt(intent.parked)

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

            // [DET-NUDGE-PERSIST-001] The user declines to mark — resolve the question everywhere
            // (durable record + tray notification). The state field clears via the observe flow.
            is HomeIntent.DismissParkNudge -> clearParkNudge()

            // [DET-BATTERY-EXEMPTION-NUDGE-001] "Activate now" on the battery nudge → fire the system
            // exemption request (and, on aggressive OEMs, the manufacturer's foreground/autostart page).
            // The screen handles the effect; the nudge clears itself once the reliability flow re-emits.
            is HomeIntent.RequestBatteryExemption -> sendEffect(HomeEffect.RequestBatteryOptimizationExemption)

            // [DET-WATCH-REACTIVATE-001] "Reactivate" on the interrupted-watch row → rebuild the
            // watcher for real. Success needs no message: the service flips its presence to Sentry and
            // the row becomes "watching" by itself. A refusal DOES need one — the whole bug being fixed
            // here is a CTA that looked like it did nothing.
            is HomeIntent.ResumeWatch -> viewModelScope.launch {
                val resumed = departureWatchResumer.resume(source = "home-cta", force = true)
                if (!resumed) sendEffect(HomeEffect.ShowError(PaparcarError.Detection.WatchResumeFailed))
            }

            else -> Unit
        }
    }

    /**
     * "I'm driving [vehicleId]" — the tap declares which car is being driven. If that car isn't the
     * active one, make it active first (the single-active invariant lives in the repository), so the
     * detector attributes the upcoming parking to it instead of whatever ranked active before.
     * Then arm manual tracking. Setting active is idempotent when it already is. [VEH-ACTIVE-FENCE-001]
     */
    private fun startDrivingDetection(vehicleId: String?) {
        viewModelScope.launch {
            // Declare the car active (idempotent when it already is) so the parking attributes to it
            // and its fence is registered; then arm. The swap lives in DeclareActiveVehicle. [VEH-ACTIVE-FENCE-001]
            vehicleId?.let {
                declareActiveVehicle(it)
                    .onFailure { e -> PaparcarLogger.w(TAG, "startDriving: declareActiveVehicle($it) failed", e) }
            }
            manualParkingDetection.start()
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
                is SearchUpdate.Searching -> updateState { copy(isSearching = true, searchNoResults = false) }
                is SearchUpdate.Success -> updateState {
                    copy(
                        searchResults = update.results,
                        isSearching = false,
                        searchNoResults = update.results.isEmpty(),
                    )
                }
                // A broken geocoder must not read like "nothing found": clear + say so. [H3]
                is SearchUpdate.Failure -> {
                    updateState { copy(searchResults = emptyList(), isSearching = false, searchNoResults = false) }
                    sendEffect(HomeEffect.ShowError(PaparcarError.Location.SearchFailed))
                }
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
                // The first-entry ask was just granted → offer the star feature ONCE, at the
                // moment of maximum engagement. Declining is one tap and never comes back — the
                // banner and the checklist keep owning the ask.
                // [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
                if (pitchDetectionAfterGrant && perm.hasLocationPermission) {
                    pitchDetectionAfterGrant = false
                    if (!perm.hasProducerPermissions) {
                        updateState { copy(showDetectionPitch = true) }
                    }
                }
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
                // A persisted fuel layer that outlived the process gets its stations back as soon
                // as there is a centre to ask around — the camera's if it already spoke, this fix
                // otherwise. [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]
                // Both layers: the camera's centre if it already spoke, this fix otherwise.
                viewportCentreOrGps()?.let { (lat, lon) ->
                    maybeFetchFuelViewport(lat, lon)
                    maybeFetchParkingsViewport(lat, lon)
                }
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
    private fun instrumentedHighAccuracyLocation(): Flow<com.rndeveloper.paparcar.domain.model.GpsPoint> {
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
    /** [WIDGET-ACTIONS-002] The widget's "more" SHOWS the parking — camera on the pin, card
     *  selected — where every option lives with its own confirmations. The ask waits for the
     *  sessions to land (cold start), is validated against what is STILL active, and a stale ask
     *  (session already released) dies silently. */
    private fun subscribeShowParkingAsks() {
        showParkingAskEventBus?.asks
            ?.collectSafely("showParkingAsk") { sessionId ->
                // Bounded wait: on cold start the sessions have not landed yet, but "no sessions"
                // can also be the TRUTH (released elsewhere since the widget rendered) — an
                // unbounded first{isNotEmpty} would hang the collector on that truth forever.
                val sessions = withTimeoutOrNull(RELEASE_ASK_SESSIONS_WAIT_MS) {
                    state.map { it.activeSessions }.first { it.isNotEmpty() }
                } ?: return@collectSafely
                val session = sessions.firstOrNull { it.id == sessionId } ?: return@collectSafely
                handleIntent(HomeIntent.SelectItem(HomeSelection.Parking(sessionId)))
                sendEffect(HomeEffect.MoveCameraTo(session.location.latitude, session.location.longitude))
            }
    }

    private fun subscribeStartAddParkingRequests() {
        startAddParkingEventBus.requests
            .collectSafely("startAddParking") { request ->
                val vehicles = state.map { it.vehicles }.first { it.isNotEmpty() }
                val markVehicleId = vehicles.firstOrNull { it.isActive }?.id
                    ?: vehicles.firstOrNull()?.id
                handleIntent(
                    HomeIntent.EnterAddParkingMode(
                        initialGps = state.value.userGpsPoint,
                        targetVehicleId = markVehicleId,
                        // A detection-nominated nudge keeps detection provenance on the
                        // confirmed pin. [DET-NUDGE-PIN-PROVENANCE-001]
                        fromDetectionNudge = request.fromDetectionNudge,
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

    /**
     * [DET-BATTERY-EXEMPTION-NUDGE-001] Surface the "detection can fail on your phone" nudge in Home
     * when — and only when — detection is actually working but its background survival is fragile:
     * reliability [DetectionReliabilityLevel.REDUCED] already encodes the exact trap (no Bluetooth
     * pairing + no battery exemption + aggressive OEM). A Bluetooth-paired car never hits REDUCED, so
     * the nudge stays silent for the strategy that doesn't need the exemption. Gated on the detection
     * actually being ON so it never competes with the "activate detection" readiness banner.
     */
    private fun subscribeDetectionReliability() {
        combine(
            observeDetectionReadiness(),
            observeDetectionReliability(),
        ) { readiness, reliability ->
            readiness.toUiState().isDetectionWorking &&
                reliability.level == DetectionReliabilityLevel.REDUCED
        }
            .distinctUntilChanged()
            .collectSafely("batteryNudge") { show ->
                updateState { copy(showBatteryOptimizationNudge = show) }
            }
    }

    /**
     * [DET-WATCH-HONEST-001] The real foreground-service lifecycle → state, so the parked watch badge
     * tells the truth: "Vigilando tu sitio" only when the service is genuinely resident/active, and the
     * "watch interrupted" / battery corrective when it is not.
     */
    private fun subscribeServicePresence() {
        // presence is a StateFlow (already conflated/distinct) — no distinctUntilChanged needed.
        detectionRuntime.presence
            .collectSafely("servicePresence") { presence ->
                updateState { copy(servicePresence = presence) }
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

    // Raw satellite is no longer offered — the map toggle only flips terrain ⇄ hybrid.
    // A preference stored before that change migrates to hybrid (same aerial imagery, plus
    // street labels), so nobody is stranded on a style the toggle can't leave.
    // [UI-MAP-TYPE-TOGGLE-001]
    private fun String.toMapType(): MapType = when (this) {
        MAP_TYPE_HYBRID, MAP_TYPE_SATELLITE_LEGACY -> MapType.HYBRID
        else -> MapType.TERRAIN
    }

    private fun MapType.toPreferenceString(): String = when (this) {
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

        /** Longer than [CAMERA_SETTLED_MS]: a viewport ask costs a network round-trip, so the
         *  camera has to REST, not merely pause between flicks. [FUEL-LAYER-FOLLOWS-THE-CAMERA-001] */
        const val FUEL_VIEWPORT_SETTLE_MS = 600L
        // How long a widget release-ask waits for the sessions to land on cold start before
        // concluding the garage is genuinely empty. [WIDGET-ACTIONS-002]
        const val RELEASE_ASK_SESSIONS_WAIT_MS = 5_000L

        // Priority label stamped on UI-location samples — mirrors the android LocationRequest priority
        // behind observeHighAccuracyLocation(). [UI-LOC-FOREGROUND-001]
        const val LOCATION_PRIORITY_HIGH_ACCURACY = "HIGH_ACCURACY"

        // Map type preference strings
        const val MAP_TYPE_TERRAIN = "TERRAIN"
        const val MAP_TYPE_HYBRID = "HYBRID"

        // Retired option, kept only to read (and migrate) preferences written before
        // the map control became a two-way toggle. Never written back.
        const val MAP_TYPE_SATELLITE_LEGACY = "SATELLITE"
    }
}

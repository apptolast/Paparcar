package com.rndeveloper.paparcar.presentation.home

import com.rndeveloper.paparcar.domain.parkings.ParkingZoneGrid
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
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.presentation.home.model.ViewportRefetch
import com.rndeveloper.paparcar.presentation.home.model.warnsLocationMissing
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
import com.rndeveloper.paparcar.domain.model.SpotReportGate
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
import com.rndeveloper.paparcar.presentation.home.model.HomeFuelSlice
import com.rndeveloper.paparcar.presentation.home.model.HomeNearbyLens
import com.rndeveloper.paparcar.presentation.home.model.HomeParkingsSlice
import com.rndeveloper.paparcar.presentation.home.model.isDetectionStopped
import com.rndeveloper.paparcar.presentation.home.model.isDetectionWorking
import com.rndeveloper.paparcar.presentation.home.model.toUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
import com.rndeveloper.paparcar.domain.usecase.fuel.ObserveFollowedFuelStationsUseCase
import com.rndeveloper.paparcar.domain.usecase.fuel.ToggleFollowFuelStationUseCase
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
    // ⛔ 37th + 38th, AT THE END and never in the middle: PresentationModule hands this constructor
    // a row of bare `get()`s whose types come from the positions they land on, so one inserted
    // beside its cousins would silently shift every argument after it.
    // [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
    private val toggleFollowFuelStation: ToggleFollowFuelStationUseCase,
    private val observeFollowedFuelStations: ObserveFollowedFuelStationsUseCase,
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

    // [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001] Debounce of the nearby ask, and the last camera centre
    // (for the moments a lane needs a centre before any settle — entering it, the first fix).
    private var nearbyCameraJob: Job? = null
    private var lastCameraLat: Double? = null
    private var lastCameraLon: Double? = null

    /** Whether the Home map is foreground (RESUMED). Drives [subscribeGpsLocation]: the high-accuracy
     *  user-location request runs ONLY while true, so a backgrounded map costs no GPS. Starts false;
     *  the screen flips it via [HomeIntent.SetMapForeground] on resume/pause. [UI-LOC-FOREGROUND-001] */
    private val mapForeground = MutableStateFlow(false)

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        // ⛔ The lens is NOT restored here, and that is the rule: Home is the AHORA and every cold
        // start opens on the spots the community is offering.
        // [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
        updateState { copy(mapType = appPreferences.defaultMapType.toMapType()) }
        // Whether the first-entry ask has EVER been made — read here so a returning user who already
        // refused gets the warning immediately, with nothing to wait for, while a first-timer stays
        // quiet until they have answered. [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001]
        updateState { copy(locationEverAsked = appPreferences.hasRequestedLocationPermission) }
        geocoder.attach(viewModelScope)
        subscribeControllerUpdates()
        // Repo streams → state slices, one line each ([collectInto] isolates stream errors so the UI
        // keeps serving the cache). Sessions escalate to a snackbar; the rest log. [HOMEVM-CTRL-003]
        userParkingRepository.observeActiveSessions()
            .collectInto("activeSessions", notify = { e -> PaparcarError.Database.Unknown(e.message ?: "") }) {
                withActiveSessions(it)
            }
        observeParkedVehicles().collectInto("parkedVehicles") { copy(parkedVehicles = it) }
        zoneRepository.observeZones().collectInto("zones") { copy(zones = it) }
        vehicleRepository.observeVehicles().collectInto("vehicles") { copy(vehicles = it) }
        observeIsPlus().collectInto("isPlus") { copy(isPlus = it) }
        // A standing subscription, not a lane answer: the star must be right the moment a peek
        // opens, whether or not the fuel lane was ever entered.
        // [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
        observeFollowedFuelStations().collectInto("followedFuelStations") { followed ->
            copy(followedFuelStationKeys = followed.map { it.stationKey }.toSet())
        }
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
        // Mirrored the moment it is stamped: from here on Home knows it has asked, and the warning
        // surfaces stay quiet until the answer is read. [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001]
        updateState { copy(locationEverAsked = true, locationAnswerInFlight = true) }
        // If this very ask is granted, pitch auto-detection right after (see subscribePermissions):
        // the user just granted location and is looking at the map — peak engagement for the app's
        // star feature, which no longer has a first-run screen to introduce it.
        pitchDetectionAfterGrant = true
        sendEffect(HomeEffect.RequestLocationPermission)
    }

    /**
     * The first-entry ask was answered with a NO: open the sheet on the location warning, so the
     * user sees at once what the refusal costs and where to fix it — the badge in the header and
     * the amber row in the list. Only when that warning exists ([warnsLocationMissing]: a registered
     * car with detection on): without a car there is nothing to warn about.
     * [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]
     *
     * The answer is read off the map's own foreground bracket ([HomeIntent.SetMapForeground])
     * instead of a platform callback: the system dialog pauses the screen, and resuming it means the
     * dialog is gone. Measured on Android; on iOS it relies on the alert resigning the app active.
     * A grant is left to [subscribePermissions] (the detection pitch).
     *
     * It also ends the SILENCE the question bought: while the ask is in flight the pill and the
     * amber row say nothing, because the app would be stating the absence of a permission the user
     * is still being asked for. [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001]
     */
    private fun resolveFirstEntryAnswer() {
        if (!state.value.locationAnswerInFlight) return
        // Back on screen means the dialog is gone, whatever was answered — so the silence lifts HERE
        // and not below the [firstEntryDialogShown] guard. An OEM that never paused the map then
        // costs us the auto-open, which is a missed nicety; muting the warning for a whole session
        // would be the app going quiet about the one thing it needs.
        updateState { copy(locationAnswerInFlight = false) }
        if (!firstEntryDialogShown) return
        firstEntryDialogShown = false
        val denied = !permissionManager.permissionState.value.hasLocationPermission
        if (denied && state.value.detectionUiState.warnsLocationMissing) {
            updateState { copy(locationWarningCue = locationWarningCue + 1) }
        }
    }

    /** Set once the dialog paused the map; the ask's own latch is
     *  [HomeState.locationAnswerInFlight] — it lives in state because the warning surfaces read it,
     *  and a second private copy here is how the two would drift apart.
     *  [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001] */
    private var firstEntryDialogShown = false

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

        // [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001] The complements already introduced — the same set
        // that decides whether a lane still owes its provenance line and whether EXPLORE_NEARBY is
        // done. Its own subscription and not part of the triple above: it is written from the lane,
        // not from the checklist.
        appPreferences.observeIntroducedComplements()
            .collectSafely("introducedComplements") { complements ->
                updateState { copy(introducedComplements = complements) }
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
            // Rides with the spots: same sheet, same CORE gate, same "what is around me" question —
            // and since [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001] the three subjects
            // share ONE lane, so choosing between them is a spots-family intent too.
            // [FUEL-STATIONS-LIST-001] [FUEL-STATIONS-MAP-AND-PEEK-001]
            is HomeIntent.SetNearbyLens,
            is HomeIntent.SendSpotSignal,
            is HomeIntent.EnterReportMode,
            is HomeIntent.ExitReportMode,
            is HomeIntent.ConfirmReportSpot,
            is HomeIntent.SetReportingSize,
            is HomeIntent.ReportTestSpot,
            -> handleSpotIntent(intent)

            // Following a forecourt — a write to the user's own store, answered by the star alone.
            // [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
            is HomeIntent.ToggleFollowFuelStation -> onToggleFollowFuelStation(intent.stationKey)

            // Own parking sessions
            is HomeIntent.OpenReleaseDialog,
            is HomeIntent.DismissReleaseDialog,
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
                if (intent.active) {
                    // Back from the system dialog → read its answer. [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]
                    resolveFirstEntryAnswer()
                    askFineLocationOnFirstEntry()
                } else if (state.value.locationAnswerInFlight) {
                    firstEntryDialogShown = true
                }
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
        // The lane on show travels with the camera — list and pins together, one store.
        // [A-SECTION-ANSWERS-ABOUT-WHERE-YOU-LOOK-001] [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
        if (state.value.mode is HomeMode.Browse) {
            scheduleNearbyRefresh(lat, lon)
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
     * Points the sheet's nearby lane at a subject, and — the first time only — goes and asks it.
     * [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
     *
     * The single place where "which subject is on show" is decided, which is what the three separate
     * folds it replaces could not be: with a section per subject, the `y` of each one depended on how
     * many rows the one above it held, and the free-spots feed has no cap.
     *
     * Nothing is fetched for a lane nobody enters: most sessions never ask for fuel or car parks, and
     * Spain's fuel feed is downloaded a province at a time. Leaving a lane keeps its answer, so coming
     * back costs nothing.
     *
     * **The lane is also what the MAP paints of the two complements** — there is no layers menu any
     * more, and no persisted switch behind it. Two controls for one subject is what let the map show
     * price capsules while the sheet listed car parks, and what let both label layers stack over the
     * spot pins that are the product. So entering a lane asks around the camera for that subject, and
     * leaving it drops a selection of the subject being left: a peek that kept talking about a pin no
     * longer drawn is the state this replaces, not a new one.
     * [PARKINGS-ON-THE-MAP-001] [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]
     */
    private fun setNearbyLens(lens: HomeNearbyLens) {
        // Already there: the chip of the lane you are in is a no-op, not a second ask.
        if (state.value.nearbyLens == lens) return
        // [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001] Entering a complement for the first time is the
        // one moment the app can say where its data comes from without lecturing anybody: the user
        // just asked the question. The line is BANKED here and shown for the whole visit — see
        // `HomeState.nearbyIntro` for why the flag and the line are two fields.
        val introducing = lens.complement?.takeIf { it !in state.value.introducedComplements }
        if (introducing != null) {
            appPreferences.setIntroducedComplements(state.value.introducedComplements + introducing)
        }
        updateState {
            copy(
                nearbyLens = lens,
                // Recomputed on EVERY lane change, null included: leaving a lane takes its line with
                // it, so coming back within the same session does not re-introduce what was read.
                nearbyIntro = introducing,
                // The complement being left stops being drawn, so a selection of it stops having a
                // subject. Own sessions, spots and zones are untouched — they are not lane-governed.
                selection = selection.takeUnless {
                    (it is HomeSelection.FuelStation && lens != HomeNearbyLens.FUEL) ||
                        (it is HomeSelection.PublicParking && lens != HomeNearbyLens.PARKINGS)
                },
            )
        }
        when (lens) {
            // The spots feed is already live — it is the app's standing subscription, not an ask.
            HomeNearbyLens.SPOTS -> Unit
            // ONE request per entry, into the ONE store every surface reads — and none at all when
            // the store already answers for where the camera is. [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
            HomeNearbyLens.FUEL, HomeNearbyLens.PARKINGS -> refreshNearby(lens, NearbyTrigger.ENTER)
        }
    }

    // ── Fuel stations & public car parks ──────────────────────────────────────
    // [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
    //
    // ONE store per subject and ONE way into it, the shape the free spots already had: a single
    // answer about where the camera looks, which the lane lists, the map paints, the peek steps
    // through and a tap resolves against.
    //
    // ⛔ What this replaced, so nobody rebuilds it: a lane store plus a camera-anchored "layer" store,
    // each with its own anchor and its own requests, and every surface painting their UNION. The union
    // mixed areas — rows for pins that had panned off-screen, measured from a centre nobody was looking
    // at — and the two request paths had drifted apart: a pan during an in-flight ask was DROPPED
    // (`isLoading` guard), past 20 km only one store emptied, and the parkings layer had been left with
    // the GPS fix as its only trigger, re-asking a failed Overpass on every fix.

    /** What woke the nearby ask — each earns a different amount of authority over the store. */
    private enum class NearbyTrigger {
        /** The user entered the lane: a question with no answer yet, or a stale one, is asked. */
        ENTER,

        /**
         * Home opened and we are warming a lane the user has **not** opened, once.
         * [HOME-THE-THREE-LANES-WARM-UP-ONCE-ON-ENTRY-001]
         *
         * Same semantics as [ENTER] — it is a first ask, not a retry — and kept as its own value
         * because it is the only trigger that fires for a lane that is not on screen. A log that
         * cannot tell "the user opened fuel" from "we warmed fuel" cannot explain a request nobody
         * asked for.
         */
        WARM_UP,

        /** The camera settled: the question moves with it, past half the radius. */
        CAMERA,

        /**
         * A GPS fix. ⛔ It never re-asks a failed or a stale answer: that is exactly the one-request-
         * every-5-s storm measured against Overpass on 09-09. It only supplies a centre the lane was
         * waiting for, and re-judges how far the camera is from the user.
         */
        FIX,
    }

    /** Lanes that were entered with no centre to ask around; the first fix asks for them. */
    private val nearbyAwaitingCentre = mutableSetOf<HomeNearbyLens>()

    // Latest-wins, like the spots' `flatMapLatest`: a new ask CANCELS the one in flight instead of
    // being dropped behind it. Overpass can take 20 s, and a pan inside that window used to leave the
    // store on the previous area until the camera happened to move again.
    private var fuelJob: Job? = null
    private var parkingsJob: Job? = null

    /** ⛔ Once per Home, never per fix. [HOME-THE-THREE-LANES-WARM-UP-ONCE-ON-ENTRY-001] */
    private var warmUpJob: Job? = null
    private var hasWarmedUpLanes = false

    /**
     * Fills the lanes the user has not opened, so that pressing Fuel or Car parks shows an answer
     * instead of a spinner. [HOME-THE-THREE-LANES-WARM-UP-ONCE-ON-ENTRY-001]
     *
     * ⛔ **Once, on entry — never on a pan.** The rule this narrows is real and stays in force for
     * everything else: *a lane that is not on screen downloads nothing when the camera moves*. A user
     * who only drags the map still pays no requests for a list they never opened. What changes is the
     * single warm-up at the start, which is what makes the first lane switch instant.
     *
     * ⛔ **Sequential, and that is not a style choice.** The Overpass budget is ONE ask at a time
     * ([com.rndeveloper.paparcar.data.overpass.OverpassQuota]) because the server grants 2 per IP and
     * an IP is a whole carrier's worth of users. Firing both lanes at once would put the second in a
     * queue it gives up on after 10 s — a warm-up that reliably fails is worse than none, because it
     * also teaches the lane it already asked.
     *
     * Spots are not in here: they come from Firestore and are already loaded by the default lens.
     */
    private fun warmUpHiddenLanes() {
        if (hasWarmedUpLanes) return
        val centre = viewportCentreOrGps() ?: return
        hasWarmedUpLanes = true

        warmUpJob = viewModelScope.launch {
            WARM_UP_ORDER
                .filter { it != state.value.nearbyLens }
                .forEach { lens ->
                    refreshNearby(lens, NearbyTrigger.WARM_UP, centre)
                    // Wait for the answer before asking the next question — see above.
                    when (lens) {
                        HomeNearbyLens.FUEL -> fuelJob?.join()
                        HomeNearbyLens.PARKINGS -> parkingsJob?.join()
                        HomeNearbyLens.SPOTS -> Unit
                    }
                }
        }
    }

    /**
     * The one decision about whether [subject]'s store must ask, stay, or empty itself.
     *
     * - No centre (no camera, no fix): the lane says it could not ask, and waits for the first fix.
     * - Further from the user than [ViewportRefetch.MAX_FROM_USER_METERS]: the store EMPTIES and says
     *   it is too far — map and list together, never your own city's pins over another one.
     * - Otherwise it asks when nothing answers for this centre: no anchor (never asked, or the last
     *   ask failed), the camera moved past half the radius, or — fuel — the car now burns something
     *   the answer was not filtered for.
     */
    private fun refreshNearby(
        subject: HomeNearbyLens,
        trigger: NearbyTrigger,
        centre: Pair<Double, Double>? = viewportCentreOrGps(),
    ) {
        if (subject == HomeNearbyLens.SPOTS) return
        val s = state.value

        if (centre == null) {
            if (trigger == NearbyTrigger.FIX) return
            nearbyAwaitingCentre += subject
            // "We could not ask" — never the empty sentence, which is a verdict about the world.
            when (subject) {
                HomeNearbyLens.FUEL -> updateState {
                    copy(fuel = fuel.copy(isLoading = false, failed = true, tooFar = false, missingFuelType = false))
                }
                HomeNearbyLens.PARKINGS -> updateState {
                    copy(parkings = parkings.copy(isLoading = false, failed = true, tooFar = false))
                }
                HomeNearbyLens.SPOTS -> Unit
            }
            return
        }
        val (lat, lon) = centre
        val wasAwaitingCentre = subject in nearbyAwaitingCentre

        if (!ViewportRefetch.isWorthAsking(lat, lon, s.userGpsPoint?.latitude, s.userGpsPoint?.longitude)) {
            nearbyAwaitingCentre -= subject
            emptyNearbyAsTooFar(subject)
            return
        }

        val anchorLat: Double?
        val anchorLon: Double?
        val radius: Int
        val wasTooFar: Boolean
        val questionChanged: Boolean
        when (subject) {
            HomeNearbyLens.FUEL -> {
                anchorLat = s.fuel.anchorLat
                anchorLon = s.fuel.anchorLon
                radius = FuelStationsQuery.DEFAULT_RADIUS_METERS
                wasTooFar = s.fuel.tooFar
                questionChanged = s.fuel.fetchedFor != activeFuelType()
            }
            HomeNearbyLens.PARKINGS -> {
                anchorLat = s.parkings.anchorLat
                anchorLon = s.parkings.anchorLon
                radius = PublicParkingsQuery.DEFAULT_RADIUS_METERS
                wasTooFar = s.parkings.tooFar
                questionChanged = false
            }
            HomeNearbyLens.SPOTS -> return
        }

        // A fix supplies a missing centre or brings a too-far lane back into range. Nothing else.
        if (trigger == NearbyTrigger.FIX && !wasAwaitingCentre && !wasTooFar) return
        nearbyAwaitingCentre -= subject

        // ⛔ Car parks re-ask by ZONE, not by distance. Panning inside the zone owes the server
        // nothing — the answer already covers it — so the question is "am I still in the same
        // zone", never "how far have I drifted".
        // [PARKINGS-A-ZONE-IS-ASKED-ONCE-AND-PANNED-FOR-FREE-001]
        val moved = when (subject) {
            HomeNearbyLens.PARKINGS ->
                anchorLat == null || anchorLon == null ||
                    !ParkingZoneGrid.sameZone(anchorLat, anchorLon, lat, lon)
            else -> ViewportRefetch.shouldRefetch(anchorLat, anchorLon, lat, lon, radius)
        }
        // ⛔ A SUBSTITUTE answer does not settle the question, so it does not seal the ground.
        // [FUEL-AN-UNKNOWN-COUNTRY-IS-NOT-A-COUNTRY-WITHOUT-PRICES-001]
        //
        // The rows on show are real forecourts — they stay — but they came from the worldwide feed
        // because the national one could not be used, and the reason is usually a moment long: a
        // geocoder that had not woken up yet. Without this, the anchor stamped on that attempt held
        // the whole area, and panning under 2,5 km (half the 5 km radius) never asked again — the
        // user was left with priceless rows until they drove out of the zone. Measured on a fresh
        // Play Store install in Spain.
        val provisional = subject == HomeNearbyLens.FUEL && s.fuel.didFallBack
        if (!moved && !questionChanged && !provisional) return

        when (subject) {
            // ⛔ The re-ask goes to the ANCHOR, not to where the camera drifted to, and that is what
            // makes it free to repeat. The question has not changed — the camera has not left the
            // area the answer is about — so asking at the anchor reproduces the same cache key
            // (`locationCacheKey`: coordinates, radius, country, region) and `CachingFuelStations
            // Source` serves it from memory for 15 minutes without touching the network.
            //
            // The moment the situation DOES change, the key changes with it: a geocoder that has
            // woken up puts a country in the query, the cache misses, and the Ministry answers with
            // prices. So there is no retry counter and no cooldown to tune — the cost is paid only
            // when there is something new to learn.
            HomeNearbyLens.FUEL -> {
                val reAskingSubstitute = provisional && !moved && !questionChanged
                if (reAskingSubstitute && anchorLat != null && anchorLon != null) {
                    fetchFuelStations(anchorLat, anchorLon)
                } else {
                    fetchFuelStations(lat, lon)
                }
            }
            HomeNearbyLens.PARKINGS -> fetchPublicParkings(lat, lon)
            HomeNearbyLens.SPOTS -> Unit
        }
    }

    /**
     * Past the follow limit the store holds NOTHING and says why. The anchor goes with the rows, so
     * coming back into range asks again instead of restoring an answer about somewhere else.
     */
    private fun emptyNearbyAsTooFar(subject: HomeNearbyLens) {
        val s = state.value
        when (subject) {
            HomeNearbyLens.FUEL -> {
                if (s.fuel.tooFar && s.fuel.stations.isEmpty() && s.fuel.anchorLat == null) return
                fuelJob?.cancel()
                updateState {
                    copy(
                        fuel = HomeFuelSlice(tooFar = true),
                        selection = selection.takeUnless { it is HomeSelection.FuelStation },
                    )
                }
            }
            HomeNearbyLens.PARKINGS -> {
                if (s.parkings.tooFar && s.parkings.parkings.isEmpty() && s.parkings.anchorLat == null) return
                parkingsJob?.cancel()
                updateState {
                    copy(
                        parkings = HomeParkingsSlice(tooFar = true),
                        selection = selection.takeUnless { it is HomeSelection.PublicParking },
                    )
                }
            }
            HomeNearbyLens.SPOTS -> Unit
        }
    }

    /**
     * Debounced camera-settle trigger — a nervous pan is not a question. Only the lane on show asks:
     * **a subject nobody is looking at downloads nothing when the camera moves.**
     *
     * ⛔ That sentence used to end at "downloads nothing", and the tail is the whole difference. The
     * one-off warm-up below fills the hidden lanes ONCE, so the first switch to Fuel or Car parks is
     * instant; every pan after that still costs a hidden lane exactly nothing.
     * [HOME-THE-THREE-LANES-WARM-UP-ONCE-ON-ENTRY-001]
     */
    private fun scheduleNearbyRefresh(lat: Double, lon: Double) {
        // Without location permission there is no fix to warm from, and the camera is the only thing
        // that ever says where the user is looking. Idempotent, so the pans that follow cost nothing.
        warmUpHiddenLanes()
        val lens = state.value.nearbyLens
        if (lens == HomeNearbyLens.SPOTS) return
        nearbyCameraJob?.cancel()
        nearbyCameraJob = viewModelScope.launch {
            delay(NEARBY_CAMERA_SETTLE_MS)
            // Re-read at fire time: the lane or the mode may have changed during the rest.
            val now = state.value
            if (now.nearbyLens == lens && now.mode is HomeMode.Browse) {
                refreshNearby(lens, NearbyTrigger.CAMERA, lat to lon)
            }
        }
    }

    /**
     * Best-known centre: the settled camera when it has spoken, the fix otherwise (cold start).
     * ⛔ (0, 0) is not a camera, it is a map that has no camera yet — falling through to the fix
     * there is what keeps a lane from asking the Gulf of Guinea.
     */
    private fun viewportCentreOrGps(): Pair<Double, Double>? {
        val camLat = lastCameraLat
        val camLon = lastCameraLon
        if (camLat != null && camLon != null && !(camLat == 0.0 && camLon == 0.0)) return camLat to camLon
        return state.value.userGpsPoint?.let { it.latitude to it.longitude }
    }

    /**
     * ⛔ One definition, in the state, read by the ask AND by every surface that shows a figure.
     * This used to be a second copy of the same two lines, which is how the map could label one fuel
     * and price another. [MAP-A-PIN-IS-PRICED-FOR-THE-CAR-YOU-DRIVE-001]
     */
    private fun activeFuelType() = state.value.pricedFuelType

    /**
     * The ONE fuel request. [FUEL-STATIONS-LIST-001] [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
     *
     * Asked for the car's fuel when it has one, fuel-less when it does not: the lane then says its
     * own sentence (`missingFuelType`) while the map paints capsules without a figure — honest either
     * way, and the SAME stations underneath.
     */
    /**
     * Star / unstar the forecourt, reading the rest of what a follow stores out of the live slice.
     * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
     *
     * ⛔ Nothing is written into the state here: [followedFuelStationKeys] comes off the store's own
     * stream, so the star lights when the row lands. An optimistic flip would be a second source of
     * truth for a fact the database already publishes — and the one that shows a filled star for a
     * write that failed.
     */
    private fun onToggleFollowFuelStation(stationKey: String) {
        val nearby = state.value.fuel.stations.firstOrNull { it.stationKey == stationKey } ?: return
        viewModelScope.launch {
            toggleFollowFuelStation(
                stationKey = stationKey,
                source = nearby.source,
                id = nearby.station.id,
                name = nearby.station.name,
                latitude = nearby.station.latitude,
                longitude = nearby.station.longitude,
            ).onFailure { e ->
                PaparcarLogger.w(TAG, "follow toggle failed for $stationKey", e)
                sendEffect(HomeEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
            }
        }
    }

    private fun fetchFuelStations(lat: Double, lon: Double) {
        val fuelType = activeFuelType()
        fuelJob?.cancel()
        updateState {
            copy(
                fuel = fuel.copy(
                    // The previous rows stay on show while the answer flies, like the spots do.
                    isLoading = true,
                    failed = false,
                    tooFar = false,
                    missingFuelType = fuelType == null,
                    fetchedFor = fuelType,
                    // Set on the ATTEMPT, so the same ground is not asked twice while it flies.
                    anchorLat = lat,
                    anchorLon = lon,
                ),
            )
        }
        fuelJob = viewModelScope.launch {
            val result = getNearbyFuelStations(lat, lon, fuelType)
            // A superseded ask writes nothing, even when a source folded the cancellation into a
            // failed Result.
            ensureActive()
            result
                .onSuccess { nearby ->
                    PaparcarLogger.d(
                        TAG,
                        "fuel: ${nearby.stations.size} stations around $lat,$lon" +
                            // ⛔ Which feed answered was not logged anywhere, and that is how a
                            // country reported as "publishes no prices" went unexamined for days.
                            (if (nearby.didFallBack) " (substitute: the national feed was not used)" else ""),
                    )
                    updateState {
                        copy(
                            fuel = fuel.copy(
                                isLoading = false,
                                failed = false,
                                stations = nearby.stations,
                                reportedAt = nearby.reportedAt,
                                rememberedAtMs = nearby.rememberedAtMs,
                                didFallBack = nearby.didFallBack,
                                pricesAreLive = nearby.pricesAreLive,
                                fetchedFor = nearby.fuel,
                            ),
                            // A selection whose pin is gone has no subject any more.
                            selection = selection.takeUnless { sel ->
                                sel is HomeSelection.FuelStation &&
                                    nearby.stations.none { it.station.id == sel.id }
                            },
                        )
                    }
                }
                // ⛔ A failure is NOT an empty list: the lane says "we could not ask", never "there are
                // none nearby". The rows go — map and list both stop showing another area's answer —
                // and so does the ANCHOR, so any camera move retries, in the same place too: a
                // transient 504 once left Paris blank until the user panned kilometres away.
                // [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001] [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]
                .onFailure { e ->
                    PaparcarLogger.w(TAG, "fuel stations lookup failed", e)
                    updateState {
                        copy(
                            fuel = fuel.copy(
                                isLoading = false,
                                failed = true,
                                stations = emptyList(),
                                reportedAt = null,
                                rememberedAtMs = null,
                                didFallBack = false,
                                pricesAreLive = false,
                                anchorLat = null,
                                anchorLon = null,
                            ),
                            selection = selection.takeUnless { it is HomeSelection.FuelStation },
                        )
                    }
                }
        }
    }

    /** The ONE car-parks request — twin of [fetchFuelStations]. [PARKINGS-ON-THE-MAP-001] */
    private fun fetchPublicParkings(lat: Double, lon: Double) {
        parkingsJob?.cancel()
        updateState {
            copy(
                parkings = parkings.copy(
                    isLoading = true,
                    failed = false,
                    tooFar = false,
                    anchorLat = lat,
                    anchorLon = lon,
                ),
            )
        }
        parkingsJob = viewModelScope.launch {
            // ⛔ **What we already know, BEFORE the wire.**
            // [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001]
            //
            // The disk holds up to 30 days of this very zone and the lane used to show a skeleton
            // over it for however long Overpass took — 1,4 s warm, 4,7–7,7 s cold, and up to 40 s
            // once the quota wait and the mirror walk are counted.
            //
            // The lane needs no new sentence for this and that is not luck, it is the shape the
            // remembered answer already had: rows with `isLoading` true skip the skeleton, and the
            // "no connection, these are the ones we saw here" note is ALREADY suppressed while
            // loading — which is exactly right, because right now we CAN ask and are asking. If the
            // ask then fails, `isLoading` drops, the note appears, and the sentence becomes true.
            //
            // ⛔ The selection is deliberately left alone here: a remembered list is not a verdict
            // about what still exists, so it must not be the thing that closes somebody's peek.
            getNearbyPublicParkings.remembered(lat, lon)
                ?.takeIf { it.parkings.isNotEmpty() }
                ?.let { known ->
                    ensureActive()
                    PaparcarLogger.d(
                        TAG,
                        "parkings: painting ${known.parkings.size} remembered car parks while asking",
                    )
                    updateState {
                        copy(
                            parkings = parkings.copy(
                                parkings = known.parkings,
                                rememberedAtMs = known.rememberedAtMs,
                            ),
                        )
                    }
                }

            val result = getNearbyPublicParkings(lat, lon)
            ensureActive()
            result
                .onSuccess { found ->
                    PaparcarLogger.d(
                        TAG,
                        "parkings: ${found.parkings.size} car parks around $lat,$lon" +
                            (found.rememberedAtMs?.let { " (remembered from $it)" } ?: ""),
                    )
                    updateState {
                        copy(
                            parkings = parkings.copy(
                                isLoading = false,
                                failed = false,
                                parkings = found.parkings,
                                rememberedAtMs = found.rememberedAtMs,
                            ),
                            selection = selection.takeUnless { sel ->
                                sel is HomeSelection.PublicParking &&
                                    found.parkings.none { it.parking.id == sel.id }
                            },
                        )
                    }
                }
                // Same rule as fuel: no rows, no anchor, and the lane says it could not ask.
                .onFailure { e ->
                    PaparcarLogger.w(TAG, "public parkings lookup failed", e)
                    updateState {
                        copy(
                            parkings = parkings.copy(
                                isLoading = false,
                                failed = true,
                                parkings = emptyList(),
                                rememberedAtMs = null,
                                anchorLat = null,
                                anchorLon = null,
                            ),
                            selection = selection.takeUnless { it is HomeSelection.PublicParking },
                        )
                    }
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
            is HomeIntent.SetNearbyLens -> setNearbyLens(intent.lens)
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
        // ⛔ Last fence, not the only one: the peek already disables its confirm and says why, so
        // reaching here means the fix moved between the camera settling and the press. It still
        // refuses — a spot nobody saw sends the whole neighbourhood on a wasted drive, which is the
        // failure the asymmetry doctrine exists to avoid.
        // [SPOT-A-REPORT-YOU-CANNOT-SEE-IS-NOT-A-REPORT-001]
        when (current.reportGate()) {
            SpotReportGate.ALLOWED -> Unit
            // Losing the fix is not standing far away, and the two must not share a sentence:
            // "turn your location on" is actionable, "you're too far" would be a guess.
            SpotReportGate.NO_FIX -> return reportPinMissing()
            SpotReportGate.TOO_FAR ->
                return sendEffect(HomeEffect.ShowError(PaparcarError.Spot.TooFarToReport))
        }
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
            is HomeIntent.OpenReleaseDialog -> updateState { copy(releaseDialogSessionId = intent.sessionId) }
            // While a release runs, its outcome owns the dialog. [PARK-RELEASE-THE-SCREEN-NEVER-SAW-001]
            is HomeIntent.DismissReleaseDialog -> updateState {
                if (isReleasingParking) this else copy(releaseDialogSessionId = null)
            }
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
        // One release at a time. The dialog's buttons disable only once the UI recomposes with
        // `releasingReason`, so two taps inside one frame both reach here — and "publish" + "don't
        // publish" racing each other would publish the spot while Room records it as unpublished.
        // Intents run on the main thread and the flag is set synchronously below, so this check
        // cannot be overtaken. [PARK-RELEASE-THE-SCREEN-NEVER-SAW-001]
        if (state.value.isReleasingParking) {
            PaparcarLogger.d(TAG, "releaseParking: a release is already in flight — ignoring id=$sessionId")
            return
        }
        // Resolve the tapped card's session explicitly — no `?: userParking` fallback. With two
        // active sessions, ranking-first could release the wrong car's spot. [VEH-ACTIVE-FENCE-001]
        val target = state.value.activeSessions.firstOrNull { it.id == sessionId }
        if (target == null) {
            PaparcarLogger.w(TAG, "releaseParking: no active session for id=$sessionId — no-op")
            // Close the dialog too: left open, it covers the snackbar and every further tap lands
            // here again — the "nothing happens" loop. [PARK-RELEASE-THE-SCREEN-NEVER-SAW-001]
            updateState { copy(releaseDialogSessionId = null) }
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
                    // The dialog closes in this same update — never inferred by the screen from a
                    // transition it may not get to see. [PARK-RELEASE-THE-SCREEN-NEVER-SAW-001]
                    updateState {
                        clearedModeFields().copy(releasingReason = null, releaseDialogSessionId = null)
                    }
                    // The use case says whether a spot ACTUALLY went out — a zone session wider
                    // than the publish ceiling withholds it, and thanking the user for a plaza
                    // that never existed would be the lie the gate removes.
                    // [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001]
                    if (published) sendEffect(HomeEffect.SpotReported)
                }
                .onFailure { e ->
                    // Closed on failure as well, as before: the error goes to the snackbar, which an
                    // open dialog would cover. [PARK-RELEASE-THE-SCREEN-NEVER-SAW-001]
                    updateState { copy(releasingReason = null, releaseDialogSessionId = null) }
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
                        // While the checklist is asking for a private zone, the zone being added IS
                        // that one: start it private (the switch stays in the user's hands).
                        // [FIRST-STEPS-A-PRIVATE-GARAGE-IS-A-PRIVATE-ZONE-001]
                        addingZoneIsPrivate = firstSteps.isVisible &&
                            firstSteps.current == FirstStep.PRIVATE_ZONE,
                    )
                }
                // Same centre, wider frame: sizing a zone means seeing its whole circle,
                // which the navigation zoom crops. [UI-ZONE-MANAGE-001]
                //
                // Sin sitio no se mueve la cámara a ninguna parte: la hoja se abre diciendo que no
                // sabemos dónde estás, con el guardar apagado, y el buscador es la salida.
                // [HOME-AN-UNSEEDED-CAMERA-IS-NOT-A-PLACE-001]
                if (intent.lat != null && intent.lon != null) {
                    sendEffect(HomeEffect.MoveCameraTo(intent.lat, intent.lon, CameraFrame.ZoneEditing))
                }
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
                is SpotsUpdate.Data -> updateState { applyNewSpots(update.spots).copy(spotsUnasked = false) }
                is SpotsUpdate.Error ->
                    sendEffect(HomeEffect.ShowError(PaparcarError.Network.Unknown(update.message)))
                // Sin centro no se ha preguntado, y el carril tiene que decir eso y no «no hay
                // plazas». [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
                SpotsUpdate.Unasked -> updateState { applyNewSpots(emptyList()).copy(spotsUnasked = true) }
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
                // The open lane may have been waiting for a centre, or the user may have moved in or
                // out of its follow range. ⛔ Never a retry of a failed ask: see NearbyTrigger.FIX.
                // [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
                refreshNearby(state.value.nearbyLens, NearbyTrigger.FIX)
                // The first fix is also the first moment Home knows WHERE to warm the other lanes.
                // Guarded inside, so the fixes that follow cost nothing.
                // [HOME-THE-THREE-LANES-WARM-UP-ONCE-ON-ENTRY-001]
                warmUpHiddenLanes()
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

    // Two retired options, read-only, both migrating to the style that replaced them:
    //  · "SATELLITE" → hybrid (same aerial imagery, plus street labels) since the map control
    //    became a two-way toggle. [UI-MAP-TYPE-TOGGLE-001]
    //  · "TERRAIN"   → normal. Google applies JSON styling to the normal map type ONLY, so on
    //    terrain every rule in MapStyles.kt was silently dropped and the map came back wearing
    //    Google's own colours and Google's own POIs. A preference written before that was found
    //    would have stranded the user on the one tile style our styling cannot reach.
    //    [MAP-A-STYLE-THAT-NEVER-RAN-001]
    private fun String.toMapType(): MapType = when (this) {
        MAP_TYPE_HYBRID, MAP_TYPE_SATELLITE_LEGACY -> MapType.HYBRID
        else -> MapType.NORMAL
    }

    private fun MapType.toPreferenceString(): String = when (this) {
        MapType.HYBRID -> MAP_TYPE_HYBRID
        else -> MAP_TYPE_NORMAL
    }

    // The pure `HomeState → HomeState` transitions of the mode machine (clearedModeFields,
    // applyNewSpots, resetSearch, pinCoordinates) live in HomeStateTransitions.kt, next to
    // the mode↔selection invariant they enforce. [HOMEVM-CTRL-004]

    // ── Constants ─────────────────────────────────────────────────────────────

    private companion object {
        const val TAG = "HomeViewModel"

        /**
         * Which hidden lane is warmed FIRST — a decision, not the order somebody happened to declare
         * an enum in. [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001]
         *
         * The warm-up is sequential because the Overpass budget is one ask at a time
         * (`OverpassQuota.CONCURRENT_ASKS`), so whoever goes second waits out the first lane's WHOLE
         * answer before it even starts. That used to be car parks, by nothing better than
         * `HomeNearbyLens.entries` reading `SPOTS, FUEL, PARKINGS`.
         *
         * Car parks go first because theirs is the slower question and the one with the weaker
         * fallback: fuel in a country with a national feed never touches Overpass, while every car
         * park in the world does.
         *
         * ⛔ [HomeNearbyLens.SPOTS] is absent rather than filtered out: free spots come from
         * Firestore and the default lens already loads them, so there has never been anything here
         * to warm.
         */
        val WARM_UP_ORDER = listOf(HomeNearbyLens.PARKINGS, HomeNearbyLens.FUEL)

        // Timing
        const val CAMERA_SETTLED_MS = 280L

        /** Longer than [CAMERA_SETTLED_MS]: a nearby ask costs a network round-trip, so the
         *  camera has to REST, not merely pause between flicks. [FUEL-LAYER-FOLLOWS-THE-CAMERA-001] */
        const val NEARBY_CAMERA_SETTLE_MS = 600L
        // How long a widget release-ask waits for the sessions to land on cold start before
        // concluding the garage is genuinely empty. [WIDGET-ACTIONS-002]
        const val RELEASE_ASK_SESSIONS_WAIT_MS = 5_000L

        // Priority label stamped on UI-location samples — mirrors the android LocationRequest priority
        // behind observeHighAccuracyLocation(). [UI-LOC-FOREGROUND-001]
        const val LOCATION_PRIORITY_HIGH_ACCURACY = "HIGH_ACCURACY"

        // Map type preference strings
        const val MAP_TYPE_NORMAL = "NORMAL"
        const val MAP_TYPE_HYBRID = "HYBRID"

        // Retired option, kept only to read (and migrate) preferences written before
        // the map control became a two-way toggle. Never written back.
        // The other retired value, "TERRAIN", needs no constant: it falls through the same
        // `else` as any unrecognised string, which is exactly the migration it wants.
        const val MAP_TYPE_SATELLITE_LEGACY = "SATELLITE"
    }
}

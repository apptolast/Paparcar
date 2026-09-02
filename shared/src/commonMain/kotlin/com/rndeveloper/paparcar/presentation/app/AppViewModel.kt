package com.rndeveloper.paparcar.presentation.app

import com.rndeveloper.paparcar.domain.connectivity.ConnectivityBannerPhase
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityObserver
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityStatus
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class AppViewModel(
    private val permissionManager: PermissionManager,
    private val appPreferences: AppPreferences,
    private val connectivityObserver: ConnectivityObserver,
    private val vehicleRepository: VehicleRepository,
    private val zoneRepository: ZoneRepository,
    private val userParkingRepository: UserParkingRepository,
// `Nothing` as the effect type: the root ViewModel has no one-shot side effects to emit, and saying
// so in the type stops one from being smuggled in. Its only effect used to be applying a language
// the OS owns. [SETTINGS-LANGUAGE-LIVES-IN-THE-SYSTEM-001]
) : BaseViewModel<AppState, AppIntent, Nothing>() {

    init {
        // Synchronously correct state before first composition.
        // initState() cannot access constructor params — it is called during the superclass
        // constructor (via the _state lazy delegate) before subclass params are assigned.
        permissionManager.permissionState.value.let { current ->
            updateState {
                copy(
                    // [DET-READY-001d] Gate on CORE only (foreground location + notifications).
                    // PRODUCER (background + AR) no longer blocks the app — its absence is surfaced
                    // in the Home detection banner, not by ejecting the user to the permission gate.
                    permissionsGranted = current.hasCorePermissions,
                    locationServicesEnabled = current.isLocationServicesEnabled,
                    themeMode = appPreferences.themeMode,
                    imperialUnits = appPreferences.useImperialUnits,
                    connectivity = connectivityObserver.status.value,
                    // Cold-start offline shows the banner immediately; online starts hidden. [CONN-BANNER-001]
                    connectivityBanner = if (connectivityObserver.status.value == ConnectivityStatus.Offline) {
                        ConnectivityBannerPhase.Offline
                    } else {
                        ConnectivityBannerPhase.Hidden
                    },
                    hasSeenGpsAccuracyDisclaimer = appPreferences.hasSeenGpsAccuracyDisclaimer,
                    hasAcceptedLegalConsent = appPreferences.hasAcceptedLegalConsent,
                )
            }
        }
        viewModelScope.launch {
            // StateFlow never throws — no .catch needed here.
            permissionManager.permissionState.collect { permState ->
                updateState {
                    copy(
                        permissionsGranted = permState.hasCorePermissions, // CORE-only gate [DET-READY-001d]
                        locationServicesEnabled = permState.isLocationServicesEnabled,
                    )
                }
            }
        }
        viewModelScope.launch {
            // Track the previous status locally so the transient "Restored" banner only fires on a
            // real Offline → Online transition, not on cold start. [CONN-BANNER-001]
            var previous = connectivityObserver.status.value
            connectivityObserver.status
                .collect { current ->
                    when {
                        current == ConnectivityStatus.Offline -> {
                            // Cancel any pending auto-hide: we're offline again, keep the red banner up.
                            restoredHideJob?.cancel()
                            updateState { copy(connectivity = current, connectivityBanner = ConnectivityBannerPhase.Offline) }
                        }
                        previous == ConnectivityStatus.Offline -> {
                            // Real reconnect → push any edits made while offline to the cloud, then
                            // show the green banner briefly, then settle to Hidden. [SYNC-RECONCILE-001]
                            drainPendingSync()
                            updateState { copy(connectivity = current, connectivityBanner = ConnectivityBannerPhase.Restored) }
                            restoredHideJob?.cancel()
                            restoredHideJob = viewModelScope.launch {
                                delay(RESTORED_VISIBLE_MS.milliseconds)
                                updateState { copy(connectivityBanner = ConnectivityBannerPhase.Hidden) }
                            }
                        }
                        else -> {
                            // Steady online (incl. cold start) → leave the banner as-is (Hidden).
                            updateState { copy(connectivity = current) }
                        }
                    }
                    previous = current
                }
        }
        // Mirror the real vehicle list into appState so navigation decisions
        // (e.g. post-permissions: HOME vs VEHICLE_REGISTRATION) can read it directly.
        //
        // observeVehicles() is auth-reactive at the repo level — it switches
        // automatically as the user logs in/out, emitting [] while unauthenticated.
        // No flatMapLatest needed here.
        vehicleRepository.observeVehicles()
            .catch { e -> PaparcarLogger.e(TAG, "vehicle observation failed", e) }
            .onEach { vehicles ->
                updateState { copy(hasVehicle = vehicles.isNotEmpty()) }
            }
            .launchIn(viewModelScope)

        // On a fresh (online) start, drain any edits left un-synced by a previous offline session so
        // they reliably reach the cloud (and other devices). [SYNC-RECONCILE-001]
        if (connectivityObserver.status.value == ConnectivityStatus.Online) drainPendingSync()
    }

    /** Auto-hide timer for the transient [ConnectivityBannerPhase.Restored] banner. */
    private var restoredHideJob: Job? = null

    /** Fire-and-forget push of any un-synced vehicle + zone + parking edits to Firestore (the outbox
     *  drainer). Parking drains an offline clear/move so the remote mirror converges instead of
     *  keeping a stale active session. [SYNC-RECONCILE-USERPARKING-001] */
    private fun drainPendingSync() {
        viewModelScope.launch { vehicleRepository.pushPendingVehicles() }
        viewModelScope.launch { zoneRepository.pushPendingZones() }
        viewModelScope.launch { userParkingRepository.pushPendingParkingSessions() }
    }

    private companion object {
        const val TAG = "AppViewModel"

        /** How long the "connection restored" banner stays before settling to Hidden. [CONN-BANNER-001] */
        const val RESTORED_VISIBLE_MS = 2_500L
    }

    override fun initState() = AppState()

    override fun handleIntent(intent: AppIntent) {
        when (intent) {
            AppIntent.MarkOnboardingCompleted -> appPreferences.setOnboardingCompleted()
            AppIntent.DismissGpsAccuracyDisclaimer -> {
                appPreferences.setGpsAccuracyDisclaimerSeen()
                updateState { copy(hasSeenGpsAccuracyDisclaimer = true) }
            }
            AppIntent.AcceptLegalConsent -> {
                appPreferences.setLegalConsentAccepted()
                updateState { copy(hasAcceptedLegalConsent = true) }
            }
            is AppIntent.SetThemeMode -> {
                appPreferences.setThemeMode(intent.mode)
                updateState { copy(themeMode = intent.mode) }
            }
            is AppIntent.SetDistanceUnit -> {
                appPreferences.setUseImperialUnits(intent.imperial)
                updateState { copy(imperialUnits = intent.imperial) }
            }
        }
    }
}

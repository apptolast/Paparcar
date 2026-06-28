package io.apptolast.paparcar.presentation.app

import io.apptolast.paparcar.domain.connectivity.ConnectivityObserver
import io.apptolast.paparcar.domain.connectivity.ConnectivityStatus
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

class AppViewModel(
    private val permissionManager: PermissionManager,
    private val appPreferences: AppPreferences,
    private val connectivityObserver: ConnectivityObserver,
    private val vehicleRepository: VehicleRepository,
) : BaseViewModel<AppState, AppIntent, AppEffect>() {

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
                    selectedLanguage = appPreferences.selectedLanguage,
                    connectivity = connectivityObserver.status.value,
                    hasSeenGpsAccuracyDisclaimer = appPreferences.hasSeenGpsAccuracyDisclaimer,
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
            // Track the previous status locally so we only fire the "Back online"
            // snackbar on a real Offline → Online transition, not on cold start.
            var previous = connectivityObserver.status.value
            connectivityObserver.status
                .collect { current ->
                    updateState { copy(connectivity = current) }
                    if (previous == ConnectivityStatus.Offline && current == ConnectivityStatus.Online) {
                        sendEffect(AppEffect.ShowConnectionRestored)
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
    }

    private companion object {
        const val TAG = "AppViewModel"
    }

    override fun initState() = AppState()

    override fun handleIntent(intent: AppIntent) {
        when (intent) {
            AppIntent.MarkOnboardingCompleted -> appPreferences.setOnboardingCompleted()
            AppIntent.DismissGpsAccuracyDisclaimer -> {
                appPreferences.setGpsAccuracyDisclaimerSeen()
                updateState { copy(hasSeenGpsAccuracyDisclaimer = true) }
            }
            is AppIntent.SetThemeMode -> {
                appPreferences.setThemeMode(intent.mode)
                updateState { copy(themeMode = intent.mode) }
            }
            is AppIntent.SetDistanceUnit -> {
                appPreferences.setUseImperialUnits(intent.imperial)
                updateState { copy(imperialUnits = intent.imperial) }
            }
            is AppIntent.SetLanguage -> {
                appPreferences.setSelectedLanguage(intent.tag)
                updateState { copy(selectedLanguage = intent.tag) }
                sendEffect(AppEffect.ApplyLocale(intent.tag))
            }
        }
    }
}

package io.apptolast.paparcar.presentation.app

import io.apptolast.paparcar.domain.connectivity.ConnectivityObserver
import io.apptolast.paparcar.domain.connectivity.ConnectivityStatus
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.launch

class AppViewModel(
    private val permissionManager: PermissionManager,
    private val appPreferences: AppPreferences,
    private val connectivityObserver: ConnectivityObserver,
) : BaseViewModel<AppState, AppIntent, AppEffect>() {

    init {
        // Synchronously correct state before first composition.
        // initState() cannot access constructor params — it is called during the superclass
        // constructor (via the _state lazy delegate) before subclass params are assigned.
        permissionManager.permissionState.value.let { current ->
            updateState {
                copy(
                    permissionsGranted = current.allPermissionsGranted,
                    locationServicesEnabled = current.isLocationServicesEnabled,
                    themeMode = appPreferences.themeMode,
                    imperialUnits = appPreferences.useImperialUnits,
                    selectedLanguage = appPreferences.selectedLanguage,
                    connectivity = connectivityObserver.status.value,
                )
            }
        }
        viewModelScope.launch {
            // StateFlow never throws — no .catch needed here.
            permissionManager.permissionState.collect { permState ->
                updateState {
                    copy(
                        permissionsGranted = permState.allPermissionsGranted,
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
    }

    override fun initState() = AppState()

    override fun handleIntent(intent: AppIntent) {
        when (intent) {
            AppIntent.MarkOnboardingCompleted -> appPreferences.setOnboardingCompleted()
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

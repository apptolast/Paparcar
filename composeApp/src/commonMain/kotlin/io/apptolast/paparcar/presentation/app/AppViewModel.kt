package io.apptolast.paparcar.presentation.app

import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.launch

class AppViewModel(
    private val permissionManager: PermissionManager,
    private val appPreferences: AppPreferences,
) : BaseViewModel<AppState, AppIntent, Nothing>() {

    init {
        // Synchronously correct state before first composition.
        // initState() cannot access constructor params — it is called during the superclass
        // constructor (via the _state lazy delegate) before subclass params are assigned.
        permissionManager.permissionState.value.let { current ->
            updateState {
                copy(
                    permissionsGranted = current.allPermissionsGranted,
                    locationServicesEnabled = current.isLocationServicesEnabled,
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
    }

    override fun initState() = AppState()

    override fun handleIntent(intent: AppIntent) {
        when (intent) {
            AppIntent.MarkOnboardingCompleted -> appPreferences.setOnboardingCompleted()
        }
    }
}

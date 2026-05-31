package io.apptolast.paparcar.presentation.permissions

import io.apptolast.paparcar.domain.permissions.OemBackgroundReliabilityManager
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.launch

class PermissionsViewModel(
    private val permissionManager: PermissionManager,
    private val oemBackgroundReliabilityManager: OemBackgroundReliabilityManager,
) : BaseViewModel<PermissionsState, PermissionsIntent, PermissionsEffect>() {

    // Counts how many times a system permission dialog has been launched.
    // Escalation (rationale → settings-prompt) is driven by tap count, not by
    // StateFlow emissions — a denial of an already-denied permission does not
    // change the StateFlow value, so the collect block would never fire in that case.
    private var requestCount = 0

    init {
        // Synchronously correct state before first composition (same reason as AppViewModel).
        permissionManager.permissionState.value.let { current ->
            updateState {
                copy(
                    hasFineLocation = current.hasLocationPermission,
                    hasBackgroundLocation = current.hasBackgroundLocationPermission,
                    hasActivityRecognition = current.hasActivityRecognitionPermission,
                    hasNotifications = current.hasNotificationPermission,
                    isLocationServicesEnabled = current.isLocationServicesEnabled,
                    hasBluetoothConnect = current.hasBluetoothConnectPermission,
                    isBatteryOptimizationExempt = current.isBatteryOptimizationExempt,
                    showAutostartCard = oemBackgroundReliabilityManager.requiresAutostartWhitelist,
                    showOemBatteryCard = oemBackgroundReliabilityManager.requiresOemBatterySettings,
                )
            }
        }
        // StateFlow never throws — no .catch needed here.
        viewModelScope.launch {
            permissionManager.permissionState.collect { appState ->
                updateState {
                    copy(
                        hasFineLocation = appState.hasLocationPermission,
                        hasBackgroundLocation = appState.hasBackgroundLocationPermission,
                        hasActivityRecognition = appState.hasActivityRecognitionPermission,
                        hasNotifications = appState.hasNotificationPermission,
                        isLocationServicesEnabled = appState.isLocationServicesEnabled,
                        hasBluetoothConnect = appState.hasBluetoothConnectPermission,
                        isBatteryOptimizationExempt = appState.isBatteryOptimizationExempt,
                    )
                }
                // Navigate home once the app is fully operational.
                if (appState.allPermissionsGranted && appState.isLocationServicesEnabled) {
                    sendEffect(PermissionsEffect.NavigateToHome)
                }
            }
        }
    }

    override fun initState() = PermissionsState()

    override fun handleIntent(intent: PermissionsIntent) {
        when (intent) {
            PermissionsIntent.RequestPermissions -> handleRequestPermissions()
            PermissionsIntent.RequestBluetoothPermission -> sendEffect(PermissionsEffect.RequestStepBluetooth)
            PermissionsIntent.RequestBatteryOptimization -> sendEffect(PermissionsEffect.RequestBatteryOptimizationExemption)
            PermissionsIntent.RequestOemAutostart -> {
                updateState { copy(hasAcknowledgedAutostart = true) }
                sendEffect(PermissionsEffect.LaunchOemAutostartSettings)
            }
            PermissionsIntent.RequestOemBatterySettings -> {
                updateState { copy(hasAcknowledgedOemBattery = true) }
                sendEffect(PermissionsEffect.LaunchOemBatterySettings)
            }
            PermissionsIntent.RefreshPermissions -> permissionManager.refreshPermissions()
            PermissionsIntent.ConfirmBackgroundLocationGuide -> {
                updateState { copy(showBackgroundLocationGuide = false) }
                requestCount++
                sendEffect(PermissionsEffect.RequestStep2BackgroundLocation)
            }
            PermissionsIntent.DismissBackgroundLocationGuide ->
                updateState { copy(showBackgroundLocationGuide = false) }
        }
    }

    private fun handleRequestPermissions() {
        val current = state.value
        when {
            // Already escalated to settings — send the user there.
            current.showSettingsPrompt ->
                sendEffect(PermissionsEffect.OpenAppSettings)

            // Step 1: fine location + activity + notifications still pending.
            !current.hasFineLocation
                || !current.hasActivityRecognition
                || !current.hasNotifications -> {
                escalateIfNeeded()
                requestCount++
                sendEffect(PermissionsEffect.RequestStep1)
            }

            // Step 2: background location still pending — show guide first so the user
            // knows to select "Allow all the time" and press Back. The guide's confirm
            // button dispatches ConfirmBackgroundLocationGuide which sends the real effect.
            !current.hasBackgroundLocation -> {
                escalateIfNeeded()
                updateState { copy(showBackgroundLocationGuide = true) }
            }

            // All runtime permissions granted, but GPS is off.
            !current.isLocationServicesEnabled ->
                sendEffect(PermissionsEffect.OpenLocationSettings)
        }
    }

    /**
     * Called before each dialog launch (requestCount is still the previous value here).
     * On the first tap requestCount = 0, so no escalation.
     * On the second tap requestCount = 1, show rationale.
     * On the third tap requestCount = 2 and rationale is already shown, escalate to settings.
     */
    private fun escalateIfNeeded() {
        if (requestCount == 0) return
        updateState {
            when {
                showSettingsPrompt -> this
                showRationale -> copy(showSettingsPrompt = true)
                else -> copy(showRationale = true)
            }
        }
    }
}

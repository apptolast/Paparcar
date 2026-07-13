package io.apptolast.paparcar.presentation.permissions

import io.apptolast.paparcar.domain.model.DetectionReliabilityLevel
import io.apptolast.paparcar.domain.permissions.OemBackgroundReliabilityManager
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.usecase.detection.ObserveDetectionReliabilityUseCase
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class PermissionsViewModel(
    private val permissionManager: PermissionManager,
    private val oemBackgroundReliabilityManager: OemBackgroundReliabilityManager,
    observeDetectionReliability: ObserveDetectionReliabilityUseCase,
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
                // NOTE: we no longer auto-navigate the moment every required permission is granted.
                // Doing so yanked the user off the screen before they could opt into the optional
                // background-reliability toggles (battery/autostart) — the ones that matter most on
                // Doze-aggressive OEMs. Departure is now an explicit tap: the footer's "Continue"
                // (FinishSetup) or "Maybe later" (ContinueWithCore). [PERM-FOOTER-001]
            }
        }
        // Single reliability evaluator — REDUCED swaps the optional tier's generic battery hint
        // for the honest manufacturer-policy callout. [DET-RELIABILITY-001]
        viewModelScope.launch {
            observeDetectionReliability()
                .catch { /* keep the last known value — a dead stream must not kill the screen */ }
                .collect { report ->
                    updateState { copy(isReliabilityReduced = report.level == DetectionReliabilityLevel.REDUCED) }
                }
        }
    }

    override fun initState() = PermissionsState()

    override fun handleIntent(intent: PermissionsIntent) {
        when (intent) {
            PermissionsIntent.RequestPermissions -> handleRequestPermissions()

            // ── Per-card direct grants [ONB-CARDS-001] ──────────────────────────────
            PermissionsIntent.RequestForegroundLocation -> requestForegroundLocation()
            PermissionsIntent.OpenLocationServices -> sendEffect(PermissionsEffect.OpenLocationSettings)
            PermissionsIntent.RequestBackgroundLocation ->
                // Android only grants background once foreground is held; if it isn't, get that first.
                if (!state.value.hasFineLocation) {
                    requestForegroundLocation()
                } else {
                    updateState { copy(showBackgroundLocationGuide = true) }
                }
            PermissionsIntent.RequestActivityRecognition -> {
                escalateIfNeeded()
                requestCount++
                sendEffect(PermissionsEffect.RequestActivityRecognition)
            }
            PermissionsIntent.RequestNotifications -> {
                escalateIfNeeded()
                requestCount++
                sendEffect(PermissionsEffect.RequestNotifications)
            }

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
            PermissionsIntent.RequestSkipDetection ->
                // "Maybe later" → confirm the user really wants to skip auto-detection first. [DET-TOGGLE-002]
                updateState { copy(showSkipDetectionDialog = true) }
            PermissionsIntent.DismissSkipDetectionDialog ->
                updateState { copy(showSkipDetectionDialog = false) }
            PermissionsIntent.ContinueWithCore -> {
                // Enter with CORE only; PRODUCER stays pending and is nudged from the Home banner.
                // Guard on CORE + GPS so we never navigate into a non-operational app. [DET-READY-001e]
                updateState { copy(showSkipDetectionDialog = false) }
                if (state.value.canContinueWithCore) {
                    sendEffect(PermissionsEffect.NavigateToHome)
                }
            }
            PermissionsIntent.FinishSetup ->
                // "Continue" — every required permission is granted; enter the app. Optional
                // reliability toggles (battery/autostart) were offered and can still be enabled
                // later from Settings. [PERM-FOOTER-001]
                sendEffect(PermissionsEffect.NavigateToHome)
            PermissionsIntent.ConfirmBackgroundLocationGuide -> {
                updateState { copy(showBackgroundLocationGuide = false) }
                requestCount++
                sendEffect(PermissionsEffect.RequestStep2BackgroundLocation)
            }
            PermissionsIntent.DismissBackgroundLocationGuide ->
                updateState { copy(showBackgroundLocationGuide = false) }
            is PermissionsIntent.SetLocationPermanentlyDenied ->
                updateState { copy(locationPermanentlyDenied = intent.value) }
        }
    }

    /** Foreground-location request shared by the footer CORE step and the per-card tap. [ONB-CARDS-001] */
    private fun requestForegroundLocation() {
        val current = state.value
        if (current.showSettingsPrompt || (!current.hasFineLocation && current.locationPermanentlyDenied)) {
            sendEffect(PermissionsEffect.OpenAppSettings)
            return
        }
        escalateIfNeeded()
        requestCount++
        sendEffect(PermissionsEffect.RequestStep1)
    }

    private fun handleRequestPermissions() {
        val current = state.value
        when {
            // Already escalated to settings, OR location is permanently denied / revoked (the system
            // dialog would no-op) — send the user straight to system settings. [DET-READY-001m]
            current.showSettingsPrompt || (!current.hasFineLocation && current.locationPermanentlyDenied) ->
                sendEffect(PermissionsEffect.OpenAppSettings)

            // CORE step 1: foreground location only — the minimum to use the map. We no longer
            // bundle activity-recognition + notifications here: those are PRODUCER and only asked
            // when the user deliberately activates auto-detection. [DET-READY-001i]
            !current.hasFineLocation -> {
                escalateIfNeeded()
                requestCount++
                sendEffect(PermissionsEffect.RequestStep1)
            }

            // CORE: GPS toggle off — also essential for the map.
            !current.isLocationServicesEnabled ->
                sendEffect(PermissionsEffect.OpenLocationSettings)

            // PRODUCER sensors: activity recognition + notifications, requested together.
            !current.hasActivityRecognition || !current.hasNotifications -> {
                escalateIfNeeded()
                requestCount++
                sendEffect(PermissionsEffect.RequestProducerSensors)
            }

            // PRODUCER: background location — show the guide first so the user knows to select
            // "Allow all the time" and press Back. The guide's confirm dispatches the real effect.
            !current.hasBackgroundLocation -> {
                escalateIfNeeded()
                updateState { copy(showBackgroundLocationGuide = true) }
            }
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

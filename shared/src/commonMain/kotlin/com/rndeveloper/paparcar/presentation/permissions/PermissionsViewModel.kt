package com.rndeveloper.paparcar.presentation.permissions

import com.rndeveloper.paparcar.domain.model.DetectionReliabilityLevel
import com.rndeveloper.paparcar.domain.permissions.OemBackgroundReliabilityManager
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.permissions.SystemPermissionDialog
import com.rndeveloper.paparcar.domain.usecase.detection.ObserveDetectionReliabilityUseCase
import com.rndeveloper.paparcar.presentation.base.BaseViewModel
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
                    hasReducedLocationAccuracy = current.hasReducedLocationAccuracy,
                    isActivityRecognitionAvailable = current.isActivityRecognitionAvailable,
                    spentDialogs = current.spentDialogs,
                    producerGranted = current.hasProducerPermissions,
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
                        hasReducedLocationAccuracy = appState.hasReducedLocationAccuracy,
                        isActivityRecognitionAvailable = appState.isActivityRecognitionAvailable,
                        spentDialogs = appState.spentDialogs,
                        producerGranted = appState.hasProducerPermissions,
                        hasBluetoothConnect = appState.hasBluetoothConnectPermission,
                        isBatteryOptimizationExempt = appState.isBatteryOptimizationExempt,
                    )
                }
                // NOTE: we no longer auto-navigate the moment every required permission is granted.
                // Doing so yanked the user off the screen before they could opt into the optional
                // background-reliability toggles (battery/autostart) — the ones that matter most on
                // Doze-aggressive OEMs. Departure is now an explicit tap: the footer's "Continue"
                // (FinishSetup) or "Not now" (NotNow). [PERM-FOOTER-001]
            }
        }
        // Single reliability evaluator — REDUCED swaps the optional tier's generic battery hint
        // for the honest manufacturer-policy callout. [DET-RELIABILITY-001]
        viewModelScope.launch {
            observeDetectionReliability()
                .catch { /* keep the last known value — a dead stream must not kill the screen */ }
                .collect { report ->
                    updateState {
                        copy(
                            isReliabilityReduced = report.level == DetectionReliabilityLevel.REDUCED,
                            currentTier = report.tier,
                        )
                    }
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
            // La precisión no tiene diálogo propio: se cambia en los Ajustes de la app. La escalada
            // temporal de iOS caduca al cerrar la app, y una sesión de aparcamiento dura horas.
            // [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001]
            PermissionsIntent.RequestPreciseLocation -> sendEffect(PermissionsEffect.OpenAppSettings)
            PermissionsIntent.RequestActivityRecognition ->
                askOrOpenSettings(
                    SystemPermissionDialog.ACTIVITY,
                    PermissionsEffect.RequestActivityRecognition,
                )
            PermissionsIntent.RequestNotifications ->
                askOrOpenSettings(
                    SystemPermissionDialog.NOTIFICATIONS,
                    PermissionsEffect.RequestNotifications,
                )

            PermissionsIntent.RequestBluetoothPermission ->
                // Opcional: no cuenta para la escalada rationale → ajustes, que narra los permisos
                // requeridos. Un tap en Bluetooth no puede empujar ese texto.
                askOrOpenSettings(
                    SystemPermissionDialog.BLUETOOTH,
                    PermissionsEffect.RequestStepBluetooth,
                    escalates = false,
                )
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
            PermissionsIntent.NotNow ->
                // Decline the producer visit's ask and leave — no confirmation dialog. PRODUCER
                // stays pending and the Home banner / checklist keep owning the ask.
                // [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
                sendEffect(PermissionsEffect.NavigateToHome)
            PermissionsIntent.FinishSetup ->
                // "Continue" — everything the current focus asks for is granted; leave the screen.
                // Optional reliability toggles (battery/autostart) were offered on the producer
                // visit and can still be enabled later from Settings. [PERM-FOOTER-001]
                sendEffect(PermissionsEffect.NavigateToHome)
            PermissionsIntent.ConfirmBackgroundLocationGuide -> {
                updateState { copy(showBackgroundLocationGuide = false) }
                requestCount++
                // iOS presenta la escalada a «Siempre» UNA vez y después la ignora en silencio; con
                // ella gastada, el único camino son los Ajustes. En Android este efecto ya abre los
                // ajustes de ubicación de la app, así que el conjunto está vacío y nada cambia.
                if (state.value.dialogSpent(SystemPermissionDialog.ALWAYS_LOCATION)) {
                    sendEffect(PermissionsEffect.OpenAppSettings)
                } else {
                    sendEffect(PermissionsEffect.RequestStep2BackgroundLocation)
                }
            }
            PermissionsIntent.DismissBackgroundLocationGuide ->
                updateState { copy(showBackgroundLocationGuide = false) }
            is PermissionsIntent.SetLocationPermanentlyDenied ->
                updateState { copy(locationPermanentlyDenied = intent.value) }
        }
    }

    /** Foreground-location request shared by the footer CORE step and the per-card tap. [ONB-CARDS-001] */
    private fun requestForegroundLocation() {
        if (state.value.showSettingsPrompt) {
            sendEffect(PermissionsEffect.OpenAppSettings)
            return
        }
        askOrOpenSettings(SystemPermissionDialog.LOCATION, PermissionsEffect.RequestStep1)
    }

    /**
     * El punto ÚNICO por el que pasa toda fila cuya acción sea pedir un permiso: levanta el diálogo,
     * o abre los Ajustes si ese diálogo ya no va a salir.
     *
     * Existe como sitio único a propósito. Saltárselo en una sola fila devuelve exactamente el
     * defecto que este ticket arregla: una fila que se pinta pulsable, responde al tap y no hace
     * nada, porque el sistema descarta en silencio una petición que ya contestó.
     * [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001]
     *
     * @param escalates si el tap cuenta para la escalada rationale → ajustes. Los permisos
     *   opcionales no cuentan: su texto narra los requeridos.
     */
    private fun askOrOpenSettings(
        dialog: SystemPermissionDialog,
        ask: PermissionsEffect,
        escalates: Boolean = true,
    ) {
        if (state.value.dialogSpent(dialog)) {
            sendEffect(PermissionsEffect.OpenAppSettings)
            return
        }
        if (escalates) {
            escalateIfNeeded()
            requestCount++
        }
        sendEffect(ask)
    }

    private fun handleRequestPermissions() {
        val current = state.value
        when {
            // Already escalated to settings, OR location is permanently denied / revoked (the system
            // dialog would no-op) — send the user straight to system settings. [DET-READY-001m]
            current.showSettingsPrompt ||
                (!current.hasFineLocation && current.dialogSpent(SystemPermissionDialog.LOCATION)) ->
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

            // PRODUCER: ubicación concedida pero aproximada. Va DELANTE del resto porque sin
            // precisión nada de lo que viene después sirve de nada, y no tiene diálogo: se arregla
            // en los Ajustes. [IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001]
            current.hasReducedLocationAccuracy ->
                sendEffect(PermissionsEffect.OpenAppSettings)

            // PRODUCER sensors: activity recognition + notifications, requested together. La
            // actividad solo cuenta si el dispositivo la tiene — si no, este paso lo decide
            // únicamente la notificación.
            (!current.hasActivityRecognition && current.isActivityRecognitionAvailable) ||
                !current.hasNotifications -> {
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

package io.apptolast.paparcar.presentation.settings

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.appVersion as platformAppVersion
import io.apptolast.paparcar.core.crash.CrashReporter
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.permissions.PermissionManager
import io.apptolast.paparcar.domain.permissions.missingCorePermissions
import io.apptolast.paparcar.domain.permissions.missingPermissions
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.repository.UserProfileRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.usecase.user.DeleteAccountUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.presentation.base.BaseViewModel
import io.apptolast.paparcar.presentation.permissions.PermissionsFocus
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val prefs: AppPreferences,
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val permissionManager: PermissionManager,
    private val vehicleRepository: VehicleRepository,
) : BaseViewModel<SettingsState, SettingsIntent, SettingsEffect>() {

    override fun initState(): SettingsState = SettingsState()

    init {
        loadFromPreferences()
        observePermissions()
        observeActiveVehicle()
        viewModelScope.launch {
            val userId = authRepository.getCurrentSession()?.userId
            if (userId != null) {
                userProfileRepository.observeProfile(userId)
                    .onEach { profile -> updateState { copy(userProfile = profile) } }
                    .catch { e -> PaparcarLogger.w(TAG, "Failed to observe profile", e) }
                    .launchIn(viewModelScope)
            }
        }
    }

    /**
     * Mirrors [PermissionManager.permissionState] into the detection-health fields. Reactive, so a
     * grant made in the permissions screen (or system settings) reflects the moment the manager is
     * refreshed on resume. [SETTINGS-REMODEL-001]
     */
    private fun observePermissions() {
        permissionManager.permissionState
            .onEach { perms ->
                updateState {
                    copy(
                        missingDetectionPermissions = perms.missingPermissions(),
                        isLocationServicesEnabled = perms.isLocationServicesEnabled,
                        isBatteryOptimizationExempt = perms.isBatteryOptimizationExempt,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /** Tracks the active vehicle so the BT-improvement row knows its target + configured state. */
    private fun observeActiveVehicle() {
        vehicleRepository.observeActiveVehicle()
            .onEach { vehicle ->
                updateState {
                    copy(
                        activeVehicleId = vehicle?.id,
                        btDeviceConfigured = vehicle?.bluetoothDeviceId != null,
                    )
                }
            }
            .catch { e -> PaparcarLogger.w(TAG, "Failed to observe active vehicle", e) }
            .launchIn(viewModelScope)
    }

    /**
     * Snapshot-reads every pref-backed field into state. Called from [init]
     * and again from the screen via [refreshFromPreferences] every time the
     * user navigates back to Settings — covers the case where another screen
     * (e.g. Vehicles, Bluetooth config) mutates a pref while Settings was
     * off-screen. AppPreferences doesn't expose Flow accessors yet, so a
     * pull-on-resume strategy is the cheapest way to keep state fresh.
     */
    private fun loadFromPreferences() {
        updateState {
            copy(
                autoDetectParking = prefs.autoDetectParking,
                notifyParkingDetected = prefs.notifyParkingDetected,
                notifySpotFreed = prefs.notifySpotFreed,
                appVersion = platformAppVersion,
            )
        }
    }

    /**
     * Public hook used by [SettingsScreen] from a `LaunchedEffect(Unit)`. Also re-queries runtime
     * permissions so the detection-health row is fresh when the user returns from the permissions
     * flow / system settings (permission grants can change while off-screen).
     */
    fun refreshFromPreferences() {
        loadFromPreferences()
        permissionManager.refreshPermissions()
    }

    override fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.ToggleAutoDetect -> {
                prefs.setAutoDetectParking(intent.enabled)
                updateState { copy(autoDetectParking = intent.enabled) }
                // Confirm a turn-OFF at the point of action with a one-tap undo. [DET-TOGGLE-002]
                if (!intent.enabled) sendEffect(SettingsEffect.DetectionTurnedOff)
            }
            is SettingsIntent.ToggleParkingDetectedNotif -> {
                prefs.setNotifyParkingDetected(intent.enabled)
                updateState { copy(notifyParkingDetected = intent.enabled) }
            }
            is SettingsIntent.ToggleSpotFreedNotif -> {
                prefs.setNotifySpotFreed(intent.enabled)
                updateState { copy(notifySpotFreed = intent.enabled) }
            }
            is SettingsIntent.ToggleMasterNotifications -> {
                prefs.setNotifyParkingDetected(intent.enabled)
                prefs.setNotifySpotFreed(intent.enabled)
                updateState {
                    copy(
                        notifyParkingDetected = intent.enabled,
                        notifySpotFreed = intent.enabled,
                    )
                }
            }
            is SettingsIntent.NavigateToVehicles ->
                sendEffect(SettingsEffect.NavigateToVehicles)
            // Health "Fix": jump to CORE when a hard-blocker (foreground location) is missing OR the
            // GPS master switch is off (the "Enable GPS" row lives in the essential/CORE section);
            // otherwise the PRODUCER section holds the remaining detection permissions. [SETTINGS-REMODEL-001]
            is SettingsIntent.FixDetectionPermissions -> {
                val perms = permissionManager.permissionState.value
                val focus = if (perms.missingCorePermissions().isNotEmpty() || !perms.isLocationServicesEnabled) {
                    PermissionsFocus.Core
                } else {
                    PermissionsFocus.Producer
                }
                sendEffect(SettingsEffect.NavigateToPermissions(focus))
            }
            // BT setup needs a vehicle to attach to; send them to add one first if none exists.
            is SettingsIntent.ConfigureBluetooth -> {
                val vehicleId = state.value.activeVehicleId
                if (vehicleId != null) {
                    sendEffect(SettingsEffect.NavigateToBluetoothConfig(vehicleId))
                } else {
                    sendEffect(SettingsEffect.NavigateToVehicles)
                }
            }
            // Battery exemption lives in the PRODUCER reliability section of the permissions flow.
            is SettingsIntent.ConfigureBattery ->
                sendEffect(SettingsEffect.NavigateToPermissions(PermissionsFocus.Producer))
            is SettingsIntent.OpenPrivacyPolicy ->
                sendEffect(SettingsEffect.OpenUrl("https://paparcar.app/privacy"))
            is SettingsIntent.OpenLicenses ->
                sendEffect(SettingsEffect.OpenUrl("https://paparcar.app/licenses"))
            is SettingsIntent.OpenContact ->
                sendEffect(SettingsEffect.OpenUrl("mailto:hola@paparcar.app"))
            is SettingsIntent.RequestDeleteAccount ->
                updateState { copy(showDeleteAccountConfirmation = true) }
            is SettingsIntent.DismissDeleteAccount ->
                updateState { copy(showDeleteAccountConfirmation = false) }
            is SettingsIntent.ConfirmDeleteAccount -> deleteAccount()
            is SettingsIntent.Logout -> viewModelScope.launch {
                CrashReporter.setUserId(null)
                authRepository.signOut()
            }
        }
    }

    private fun deleteAccount() {
        updateState { copy(isDeletingAccount = true, showDeleteAccountConfirmation = false) }
        viewModelScope.launch {
            deleteAccountUseCase()
                .onSuccess {
                    CrashReporter.setUserId(null)
                    sendEffect(SettingsEffect.NavigateToAuth)
                }
                .onFailure { e ->
                    updateState { copy(isDeletingAccount = false) }
                    PaparcarLogger.e(TAG, "Failed to delete account", e)
                    sendEffect(SettingsEffect.ShowError(PaparcarError.Auth.DeleteFailed))
                }
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}

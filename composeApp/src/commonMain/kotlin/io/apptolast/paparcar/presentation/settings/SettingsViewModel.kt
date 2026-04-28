package io.apptolast.paparcar.presentation.settings

import com.apptolast.customlogin.domain.AuthRepository
import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.repository.UserProfileRepository
import io.apptolast.paparcar.domain.usecase.user.DeleteAccountUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val prefs: AppPreferences,
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository,
    private val deleteAccountUseCase: DeleteAccountUseCase,
) : BaseViewModel<SettingsState, SettingsIntent, SettingsEffect>() {

    override fun initState(): SettingsState = SettingsState()

    init {
        updateState {
            copy(
                autoDetectParking = prefs.autoDetectParking,
                notifyParkingDetected = prefs.notifyParkingDetected,
                notifySpotFreed = prefs.notifySpotFreed,
                mapType = prefs.defaultMapType.toMapType(),
            )
        }
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

    override fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.ToggleAutoDetect -> {
                prefs.setAutoDetectParking(intent.enabled)
                updateState { copy(autoDetectParking = intent.enabled) }
            }
            is SettingsIntent.ToggleParkingDetectedNotif -> {
                prefs.setNotifyParkingDetected(intent.enabled)
                updateState { copy(notifyParkingDetected = intent.enabled) }
            }
            is SettingsIntent.ToggleSpotFreedNotif -> {
                prefs.setNotifySpotFreed(intent.enabled)
                updateState { copy(notifySpotFreed = intent.enabled) }
            }
            is SettingsIntent.SetMapType -> {
                prefs.setDefaultMapType(intent.type.toPreferenceString())
                updateState { copy(mapType = intent.type) }
            }
            is SettingsIntent.NavigateBack ->
                sendEffect(SettingsEffect.NavigateBack)
            is SettingsIntent.NavigateToVehicles ->
                sendEffect(SettingsEffect.NavigateToVehicles)
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
                authRepository.signOut()
            }
        }
    }

    private fun deleteAccount() {
        updateState { copy(isDeletingAccount = true, showDeleteAccountConfirmation = false) }
        viewModelScope.launch {
            deleteAccountUseCase()
                .onSuccess { sendEffect(SettingsEffect.NavigateToAuth) }
                .onFailure { e ->
                    updateState { copy(isDeletingAccount = false) }
                    PaparcarLogger.e(TAG, "Failed to delete account", e)
                }
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
        const val MAP_TYPE_SATELLITE = "SATELLITE"
        const val MAP_TYPE_TERRAIN = "TERRAIN"
    }

    private fun String.toMapType(): MapType = when (this) {
        MAP_TYPE_SATELLITE -> MapType.SATELLITE
        MAP_TYPE_TERRAIN -> MapType.TERRAIN
        else -> MapType.NORMAL
    }

    private fun MapType.toPreferenceString(): String = when (this) {
        MapType.SATELLITE -> MAP_TYPE_SATELLITE
        MapType.TERRAIN -> MAP_TYPE_TERRAIN
        else -> "NORMAL"
    }
}

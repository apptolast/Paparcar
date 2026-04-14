package io.apptolast.paparcar.presentation.settings

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.repository.UserProfileRepository
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
) : BaseViewModel<SettingsState, SettingsIntent, SettingsEffect>() {

    override fun initState(): SettingsState = SettingsState(
        autoDetectParking = prefs.autoDetectParking,
        notifyParkingDetected = prefs.notifyParkingDetected,
        notifySpotFreed = prefs.notifySpotFreed,
    )

    init {
        val userId = authRepository.getCurrentSession()?.userId
        if (userId != null) {
            userProfileRepository.observeProfile(userId)
                .onEach { profile -> updateState { copy(userProfile = profile) } }
                .catch { e -> PaparcarLogger.w(TAG, "Failed to observe profile", e) }
                .launchIn(viewModelScope)
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
            is SettingsIntent.NavigateBack ->
                sendEffect(SettingsEffect.NavigateBack)
            is SettingsIntent.NavigateToMyCar ->
                sendEffect(SettingsEffect.NavigateToMyCar)
            is SettingsIntent.OpenPrivacyPolicy ->
                sendEffect(SettingsEffect.OpenUrl("https://paparcar.app/privacy"))
            is SettingsIntent.Logout -> viewModelScope.launch {
                authRepository.signOut()
            }
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}

package io.apptolast.paparcar.presentation.settings

import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.presentation.base.BaseViewModel

class SettingsViewModel(
    private val prefs: AppPreferences,
) : BaseViewModel<SettingsState, SettingsIntent, SettingsEffect>() {

    override fun initState(): SettingsState = SettingsState(
        autoDetectParking = prefs.autoDetectParking,
        notifyParkingDetected = prefs.notifyParkingDetected,
        notifySpotFreed = prefs.notifySpotFreed,
    )

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
        }
    }
}

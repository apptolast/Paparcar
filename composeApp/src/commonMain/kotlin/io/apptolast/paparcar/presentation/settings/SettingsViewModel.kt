package io.apptolast.paparcar.presentation.settings

import io.apptolast.paparcar.presentation.base.BaseViewModel

class SettingsViewModel : BaseViewModel<SettingsState, SettingsIntent, SettingsEffect>() {

    override fun initState(): SettingsState = SettingsState()

    override fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.ToggleAutoDetect ->
                updateState { copy(autoDetectParking = intent.enabled) }
            is SettingsIntent.ToggleParkingDetectedNotif ->
                updateState { copy(notifyParkingDetected = intent.enabled) }
            is SettingsIntent.ToggleSpotFreedNotif ->
                updateState { copy(notifySpotFreed = intent.enabled) }
            is SettingsIntent.NavigateBack ->
                sendEffect(SettingsEffect.NavigateBack)
            is SettingsIntent.OpenPrivacyPolicy ->
                sendEffect(SettingsEffect.OpenUrl("https://paparcar.app/privacy"))
        }
    }
}

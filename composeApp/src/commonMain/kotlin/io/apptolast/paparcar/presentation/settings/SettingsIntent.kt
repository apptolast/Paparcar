package io.apptolast.paparcar.presentation.settings

sealed class SettingsIntent {
    data class ToggleAutoDetect(val enabled: Boolean) : SettingsIntent()
    data class ToggleParkingDetectedNotif(val enabled: Boolean) : SettingsIntent()
    data class ToggleSpotFreedNotif(val enabled: Boolean) : SettingsIntent()
    data object NavigateBack : SettingsIntent()
    data object OpenPrivacyPolicy : SettingsIntent()
}

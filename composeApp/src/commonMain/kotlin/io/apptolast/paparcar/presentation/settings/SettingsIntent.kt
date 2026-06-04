package io.apptolast.paparcar.presentation.settings

sealed class SettingsIntent {
    data class ToggleAutoDetect(val enabled: Boolean) : SettingsIntent()
    data class ToggleParkingDetectedNotif(val enabled: Boolean) : SettingsIntent()
    data class ToggleSpotFreedNotif(val enabled: Boolean) : SettingsIntent()
    /** Master switch — toggles every sub-notification at once. */
    data class ToggleMasterNotifications(val enabled: Boolean) : SettingsIntent()
    data object NavigateToVehicles : SettingsIntent()
    data object OpenPrivacyPolicy : SettingsIntent()
    data object OpenLicenses : SettingsIntent()
    data object OpenContact : SettingsIntent()
    data object RequestDeleteAccount : SettingsIntent()
    data object ConfirmDeleteAccount : SettingsIntent()
    data object DismissDeleteAccount : SettingsIntent()
    data object Logout : SettingsIntent()
}

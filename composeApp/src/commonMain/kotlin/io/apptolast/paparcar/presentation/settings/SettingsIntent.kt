package io.apptolast.paparcar.presentation.settings

sealed class SettingsIntent {
    data class ToggleAutoDetect(val enabled: Boolean) : SettingsIntent()
    data class ToggleParkingDetectedNotif(val enabled: Boolean) : SettingsIntent()
    data class ToggleSpotFreedNotif(val enabled: Boolean) : SettingsIntent()
    /** Master switch — toggles every sub-notification at once. */
    data class ToggleMasterNotifications(val enabled: Boolean) : SettingsIntent()
    data object NavigateToVehicles : SettingsIntent()
    /** "Fix" on the detection health row — jump to the permissions flow focused on what's missing. */
    data object FixDetectionPermissions : SettingsIntent()
    /** "Set up" the car-Bluetooth improvement — deep-link into BT config for the active vehicle. */
    data object ConfigureBluetooth : SettingsIntent()
    /** "Set up" the battery-exemption improvement (Android Doze). */
    data object ConfigureBattery : SettingsIntent()
    /** "Fix" on the REDUCED-reliability health row — jump to the optional-reliability section of
     *  the permissions flow (battery exemption + OEM autostart cards). [DET-RELIABILITY-001] */
    data object FixDetectionReliability : SettingsIntent()
    data object OpenPrivacyPolicy : SettingsIntent()
    data object OpenLicenses : SettingsIntent()
    data object OpenContact : SettingsIntent()
    data object RequestDeleteAccount : SettingsIntent()
    data object ConfirmDeleteAccount : SettingsIntent()
    data object DismissDeleteAccount : SettingsIntent()
    data object Logout : SettingsIntent()
}

package io.apptolast.paparcar.presentation.settings

import com.swmansion.kmpmaps.core.MapType

sealed class SettingsIntent {
    data class ToggleAutoDetect(val enabled: Boolean) : SettingsIntent()
    data class ToggleParkingDetectedNotif(val enabled: Boolean) : SettingsIntent()
    data class ToggleSpotFreedNotif(val enabled: Boolean) : SettingsIntent()
    data class SetMapType(val type: MapType) : SettingsIntent()
    data object NavigateBack : SettingsIntent()
    data object NavigateToMyCar : SettingsIntent()
    data object OpenPrivacyPolicy : SettingsIntent()
    data object OpenLicenses : SettingsIntent()
    data object OpenContact : SettingsIntent()
    data object RequestDeleteAccount : SettingsIntent()
    data object ConfirmDeleteAccount : SettingsIntent()
    data object DismissDeleteAccount : SettingsIntent()
    data object Logout : SettingsIntent()
}

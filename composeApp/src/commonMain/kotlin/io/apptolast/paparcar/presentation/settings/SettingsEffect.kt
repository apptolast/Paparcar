package io.apptolast.paparcar.presentation.settings

sealed class SettingsEffect {
    data object NavigateBack : SettingsEffect()
    data object NavigateToVehicles : SettingsEffect()
    data object NavigateToAuth : SettingsEffect()
    data class OpenUrl(val url: String) : SettingsEffect()
}

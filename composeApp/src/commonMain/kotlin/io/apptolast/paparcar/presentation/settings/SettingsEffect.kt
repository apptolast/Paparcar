package io.apptolast.paparcar.presentation.settings

sealed class SettingsEffect {
    data object NavigateBack : SettingsEffect()
    data class OpenUrl(val url: String) : SettingsEffect()
}

package io.apptolast.paparcar.presentation.settings

import io.apptolast.paparcar.domain.error.PaparcarError

sealed class SettingsEffect {
    data object NavigateToVehicles : SettingsEffect()
    data object NavigateToAuth : SettingsEffect()
    data class OpenUrl(val url: String) : SettingsEffect()
    data class ShowError(val error: PaparcarError) : SettingsEffect()
}

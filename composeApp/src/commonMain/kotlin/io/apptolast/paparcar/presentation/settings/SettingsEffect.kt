package io.apptolast.paparcar.presentation.settings

import io.apptolast.paparcar.domain.error.PaparcarError

sealed class SettingsEffect {
    data object NavigateToVehicles : SettingsEffect()
    data object NavigateToAuth : SettingsEffect()
    data class OpenUrl(val url: String) : SettingsEffect()
    data class ShowError(val error: PaparcarError) : SettingsEffect()
    /** Auto-detection just turned OFF from the toggle — confirm at the point of action with an
     *  undo/turn-on snackbar, since the user may not realise what it disables. [DET-TOGGLE-002] */
    data object DetectionTurnedOff : SettingsEffect()
}

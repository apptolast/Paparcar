package io.apptolast.paparcar.presentation.settings

import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.presentation.permissions.PermissionsFocus

sealed class SettingsEffect {
    data object NavigateToVehicles : SettingsEffect()
    data object NavigateToAuth : SettingsEffect()
    /** Open the permissions flow focused on a tier (detection health "Fix" / battery setup). */
    data class NavigateToPermissions(val focus: PermissionsFocus) : SettingsEffect()
    /** Deep-link into the car-Bluetooth config for a given vehicle. */
    data class NavigateToBluetoothConfig(val vehicleId: String) : SettingsEffect()
    data class OpenUrl(val url: String) : SettingsEffect()
    data class ShowError(val error: PaparcarError) : SettingsEffect()
    /** Auto-detection just turned OFF from the toggle — confirm at the point of action with an
     *  undo/turn-on snackbar, since the user may not realise what it disables. [DET-TOGGLE-002] */
    data object DetectionTurnedOff : SettingsEffect()
}

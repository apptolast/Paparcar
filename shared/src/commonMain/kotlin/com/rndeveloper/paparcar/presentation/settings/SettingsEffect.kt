package com.rndeveloper.paparcar.presentation.settings

import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.presentation.permissions.PermissionsFocus

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
    /** The problem report reached the backend — confirm so the user knows it's on us now.
     *  [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001] */
    data object DiagnosticsSent : SettingsEffect()

    /** The guided checklist was put back. Confirmed with a snackbar because the RESULT is on another
     *  screen — without it, the row would look like it did nothing.
     *  [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] */
    data object FirstStepsRestarted : SettingsEffect()
}

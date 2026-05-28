package io.apptolast.paparcar.presentation.permissions

sealed class PermissionsEffect {
    data object RequestStep1 : PermissionsEffect()
    data object RequestStep2BackgroundLocation : PermissionsEffect()
    data object RequestStepBluetooth : PermissionsEffect()
    data object RequestBatteryOptimizationExemption : PermissionsEffect()
    /** Launch the manufacturer's autostart / background-activity settings screen. */
    data object LaunchOemAutostartSettings : PermissionsEffect()
    data object OpenAppSettings : PermissionsEffect()
    data object OpenLocationSettings : PermissionsEffect()
    data object NavigateToHome : PermissionsEffect()
}

package io.apptolast.paparcar.presentation.permissions

data class PermissionsState(
    val hasFineLocation: Boolean = false,
    val hasBackgroundLocation: Boolean = false,
    val hasActivityRecognition: Boolean = false,
    val hasNotifications: Boolean = false,
    val isLocationServicesEnabled: Boolean = false,
    /** Optional — Bluetooth parking detection only. Does not block navigation. */
    val hasBluetoothConnect: Boolean = false,
    val showRationale: Boolean = false,
    val showSettingsPrompt: Boolean = false,
)

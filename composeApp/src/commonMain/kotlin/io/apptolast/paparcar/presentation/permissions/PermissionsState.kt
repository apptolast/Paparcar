package io.apptolast.paparcar.presentation.permissions

data class PermissionsState(
    val hasFineLocation: Boolean = false,
    val hasBackgroundLocation: Boolean = false,
    val hasActivityRecognition: Boolean = false,
    val hasNotifications: Boolean = false,
    val isLocationServicesEnabled: Boolean = false,
    /** Optional — Bluetooth parking detection only. Does not block navigation. */
    val hasBluetoothConnect: Boolean = false,
    /** Optional — battery optimization exemption. Does not block navigation.
     *  Critical on Doze-aggressive OEMs (MIUI, ColorOS, EMUI). [DOZE-001] */
    val isBatteryOptimizationExempt: Boolean = false,
    /** Optional — OEM autostart / background-activity whitelist. Only shown on
     *  manufacturers that ship the toggle (MIUI, ColorOS, EMUI, OriginOS…).
     *  We can't read its actual state (no public API), so the card always shows
     *  as "pending action" while it is visible. [BUG-DETECT-OEM-KILLER-001] */
    val showAutostartCard: Boolean = false,
    val showRationale: Boolean = false,
    val showSettingsPrompt: Boolean = false,
    /** Show step-by-step guide before opening system Settings for background location.
     *  Android 11+ takes the user directly to Settings with no dialog — without this
     *  guide users don't know they must select "Allow all the time" then press Back. */
    val showBackgroundLocationGuide: Boolean = false,
)

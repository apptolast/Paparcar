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
     *  We can't read its actual state (no public API), so we track whether the
     *  user has tapped the button and opened settings. [BUG-DETECT-OEM-KILLER-001] */
    val showAutostartCard: Boolean = false,
    /** `true` once the user has tapped the autostart button this session, used to
     *  show the card in the "granted" visual state (optimistic — we trust the user
     *  granted it after opening settings). Resets on process death. */
    val hasAcknowledgedAutostart: Boolean = false,
    /** Optional — OEM battery / power management settings for OplusHansManager (ColorOS).
     *  Hans freezes background processes with SIGSTOP independent of the autostart toggle.
     *  Only shown on OPPO/Realme. [OEM-002] */
    val showOemBatteryCard: Boolean = false,
    /** `true` once the user has tapped the OEM battery button this session. */
    val hasAcknowledgedOemBattery: Boolean = false,
    val showRationale: Boolean = false,
    val showSettingsPrompt: Boolean = false,
    /** Show step-by-step guide before opening system Settings for background location.
     *  Android 11+ takes the user directly to Settings with no dialog — without this
     *  guide users don't know they must select "Allow all the time" then press Back. */
    val showBackgroundLocationGuide: Boolean = false,
)

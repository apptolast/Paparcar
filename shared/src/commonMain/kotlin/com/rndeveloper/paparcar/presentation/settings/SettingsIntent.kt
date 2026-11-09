package com.rndeveloper.paparcar.presentation.settings

sealed class SettingsIntent {
    data class ToggleAutoDetect(val enabled: Boolean) : SettingsIntent()
    data class ToggleParkingDetectedNotif(val enabled: Boolean) : SettingsIntent()
    /** Master switch for the Plus zone alerts; enabling as free routes to the paywall. [MONEY-001] */
    data class ToggleZoneAlertsNotif(val enabled: Boolean) : SettingsIntent()

    /** Master gate for the Plus fuel-price alerts. Same soft-gate shape as the zone one.
     *  [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] */
    data class ToggleFuelAlertsNotif(val enabled: Boolean) : SettingsIntent()

    /** El resumen del mes. Mismo gate blando que sus dos vecinas.
     *  [MONEY-A-CARD-THAT-NEVER-APPEARS-IS-NEVER-SOLD-001] */
    data class ToggleMonthRecapNotif(val enabled: Boolean) : SettingsIntent()

    /** [PRIV-A-TRACE-IS-NOT-A-STAT-001] Soft-gated like the zone alerts: enabling as free opens
     *  the paywall; disabling is always allowed. */
    data class ToggleRouteCloudSync(val enabled: Boolean) : SettingsIntent()
    /** Give or take back the yes to advertising measurement. Never gated.
     *  [MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001] */
    data class ToggleAdMeasurement(val enabled: Boolean) : SettingsIntent()
    /** The Plus entry at the top of Settings (free users only — Plus shows a badge). [MONEY-001] */
    data object OpenPlusPaywall : SettingsIntent()
    data object NavigateToVehicles : SettingsIntent()
    /** "Fix" on the detection health row — jump to the permissions flow focused on what's missing. */
    data object FixDetectionPermissions : SettingsIntent()
    /** "Set up" the car-Bluetooth improvement — deep-link into BT config for the active vehicle. */
    data object ConfigureBluetooth : SettingsIntent()
    /** "Set up" the battery-exemption improvement (Android Doze). */
    data object ConfigureBattery : SettingsIntent()
    /** "Fix" on the REDUCED-reliability health row — jump to the optional-reliability section of
     *  the permissions flow (battery exemption + OEM autostart cards). [DET-RELIABILITY-001] */
    data object FixDetectionReliability : SettingsIntent()
    /**
     * "Replay the first steps" — bring the guided checklist back on Home.
     * [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001]
     *
     * Clears the TUTORIAL's own flags and nothing else. In particular it does not touch
     * `hasConfirmedFirstPark`, which arms the cold-start notification: replaying an explanation must
     * not re-arm a reminder for something the user demonstrably already did.
     */
    data object RestartFirstSteps : SettingsIntent()
    /** Open the system sheet to pin the parked-car widget. [WIDGET-ACTIONS-002] */
    data object PinWidget : SettingsIntent()
    data object OpenPrivacyPolicy : SettingsIntent()
    data object OpenLicenses : SettingsIntent()
    data object OpenContact : SettingsIntent()
    /** "Report a problem" row — open the consent dialog. [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001] */
    data object RequestSendDiagnostics : SettingsIntent()
    /** The user's description of what went wrong, as they type it.
     *  [SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001] */
    data class UpdateDiagnosticsMessage(val message: String) : SettingsIntent()
    data object ConfirmSendDiagnostics : SettingsIntent()
    data object DismissSendDiagnostics : SettingsIntent()
    data object RequestDeleteAccount : SettingsIntent()
    data object ConfirmDeleteAccount : SettingsIntent()
    data object DismissDeleteAccount : SettingsIntent()
    data object Logout : SettingsIntent()

    /** Pide el correo de confirmación para la cuenta activa. [AUTH-AN-UNCONFIRMED-EMAIL-MUST-SAY-SO-001] */
    data object SendEmailVerification : SettingsIntent()
}

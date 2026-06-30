package io.apptolast.paparcar.presentation.permissions

sealed class PermissionsIntent {
    /** Footer CTA — guided sequential flow (CORE → GPS → PRODUCER). [DET-READY-001i] */
    data object RequestPermissions : PermissionsIntent()

    // ── Per-card direct grants — each permission row is independently tappable, in addition to the
    //    footer's guided flow. [ONB-CARDS-001]
    /** Foreground (fine) location row. Routes to system settings if permanently denied. */
    data object RequestForegroundLocation : PermissionsIntent()
    /** Location services (GPS toggle) row → opens system location settings. */
    data object OpenLocationServices : PermissionsIntent()
    /** Background location row → guide dialog, then the always-on request (needs foreground first). */
    data object RequestBackgroundLocation : PermissionsIntent()
    /** Activity recognition row → its own system dialog (no longer bundled with notifications). */
    data object RequestActivityRecognition : PermissionsIntent()
    /** Notifications row → its own system dialog. */
    data object RequestNotifications : PermissionsIntent()

    data object RequestBluetoothPermission : PermissionsIntent()
    data object RequestBatteryOptimization : PermissionsIntent()
    /** Open the manufacturer's autostart / background-activity settings screen. */
    data object RequestOemAutostart : PermissionsIntent()
    /** Open the OEM-specific battery / power management settings (ColorOS Hans freeze). */
    data object RequestOemBatterySettings : PermissionsIntent()
    data object RefreshPermissions : PermissionsIntent()
    /**
     * "Maybe later" — enter the app with CORE (foreground location + notifications) only,
     * deferring the PRODUCER tier (background + activity recognition). The Home banner nudges
     * the user to enable detection afterwards. [DET-READY-001e]
     */
    data object ContinueWithCore : PermissionsIntent()
    /** "Maybe later" tapped — show the educational "you'll miss auto-detection" dialog first. [DET-TOGGLE-002] */
    data object RequestSkipDetection : PermissionsIntent()
    /** Dismiss the skip-detection dialog and stay on the permissions screen (user chose to activate). */
    data object DismissSkipDetectionDialog : PermissionsIntent()
    /** User confirmed the background-location guide and wants to open system Settings now. */
    data object ConfirmBackgroundLocationGuide : PermissionsIntent()
    /** User dismissed the background-location guide without opening Settings. */
    data object DismissBackgroundLocationGuide : PermissionsIntent()

    /** Platform layer reports whether foreground location is permanently denied / revoked (Android
     *  shouldShowRequestPermissionRationale + a "have we asked" flag). [DET-READY-001m] */
    data class SetLocationPermanentlyDenied(val value: Boolean) : PermissionsIntent()
}

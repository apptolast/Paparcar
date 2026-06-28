package io.apptolast.paparcar.presentation.permissions

sealed class PermissionsIntent {
    data object RequestPermissions : PermissionsIntent()
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
    /** User confirmed the background-location guide and wants to open system Settings now. */
    data object ConfirmBackgroundLocationGuide : PermissionsIntent()
    /** User dismissed the background-location guide without opening Settings. */
    data object DismissBackgroundLocationGuide : PermissionsIntent()

    /** Platform layer reports whether foreground location is permanently denied / revoked (Android
     *  shouldShowRequestPermissionRationale + a "have we asked" flag). [DET-READY-001m] */
    data class SetLocationPermanentlyDenied(val value: Boolean) : PermissionsIntent()
}

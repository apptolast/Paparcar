package io.apptolast.paparcar.presentation.permissions

sealed class PermissionsIntent {
    data object RequestPermissions : PermissionsIntent()
    data object RequestBluetoothPermission : PermissionsIntent()
    data object RequestBatteryOptimization : PermissionsIntent()
    data object RefreshPermissions : PermissionsIntent()
    /** User confirmed the background-location guide and wants to open system Settings now. */
    data object ConfirmBackgroundLocationGuide : PermissionsIntent()
    /** User dismissed the background-location guide without opening Settings. */
    data object DismissBackgroundLocationGuide : PermissionsIntent()
}

package io.apptolast.paparcar.presentation.permissions

sealed class PermissionsIntent {
    data object RequestPermissions : PermissionsIntent()
    data object RequestBluetoothPermission : PermissionsIntent()
    data object RefreshPermissions : PermissionsIntent()
}

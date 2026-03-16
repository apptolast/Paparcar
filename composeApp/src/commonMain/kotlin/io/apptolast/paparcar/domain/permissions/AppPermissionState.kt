package io.apptolast.paparcar.domain.permissions

data class AppPermissionState(
    val hasLocationPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val hasActivityRecognitionPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    // System setting — separate from runtime permissions.
    // NOT included in allPermissionsGranted to avoid redirecting the user
    // to the permissions gate just because they toggled GPS off mid-session.
    val isLocationServicesEnabled: Boolean = false,
) {
    val allPermissionsGranted: Boolean
        get() = hasLocationPermission
            && hasBackgroundLocationPermission
            && hasActivityRecognitionPermission
            && hasNotificationPermission
}

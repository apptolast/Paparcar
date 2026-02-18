package io.apptolast.paparcar.domain.permissions

data class AppPermissionState(
    val hasLocationPermission: Boolean = false,
    val hasActivityRecognitionPermission: Boolean = false
) {
    val allPermissionsGranted: Boolean
        get() = hasLocationPermission && hasActivityRecognitionPermission
}

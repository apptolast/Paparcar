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
    // Optional — only available on Android 12+. Not required for core app functionality;
    // only needed for the Bluetooth parking detection strategy.
    val hasBluetoothConnectPermission: Boolean = false,
    // Optional — not a runtime permission. User must approve via system dialog.
    // Critical on Doze-aggressive OEMs (MIUI, ColorOS, EMUI). Not in allPermissionsGranted
    // because the app still works without it; detection just degrades on OEM killers. [DOZE-001]
    val isBatteryOptimizationExempt: Boolean = false,
) {
    val allPermissionsGranted: Boolean
        get() = hasLocationPermission
            && hasBackgroundLocationPermission
            && hasActivityRecognitionPermission
            && hasNotificationPermission

    /**
     * CORE tier — the minimum to use the app at all (spot map / consumer side): foreground
     * location. Notifications are NOT here — you can browse spots without them. Gates entry to
     * Home. See [RequiredPermission] and [PermissionTier.CORE]. [DET-READY-001a] [DET-READY-001i]
     */
    val hasCorePermissions: Boolean
        get() = hasLocationPermission

    /**
     * PRODUCER tier — the full automatic-detection experience: background location, activity
     * recognition AND notifications (so detection can actually tell the user anything). Requested
     * contextually; never gates Home. [DET-READY-001a] [DET-READY-001i]
     */
    val hasProducerPermissions: Boolean
        get() = hasBackgroundLocationPermission &&
            hasActivityRecognitionPermission &&
            hasNotificationPermission
}

package io.apptolast.paparcar.presentation.permissions

import androidx.compose.runtime.Composable

/**
 * Platform-specific permissions rationale and request screen.
 *
 * Android: handles fine location (ACCESS_FINE_LOCATION), activity recognition
 *          (ACTIVITY_RECOGNITION), notifications (POST_NOTIFICATIONS), background
 *          location (ACCESS_BACKGROUND_LOCATION) and Bluetooth connect
 *          (BLUETOOTH_CONNECT) via ActivityResultLauncher permission launchers.
 *          Calls [onPermissionsGranted] only when all required permissions are granted.
 *
 * iOS:     uses [IosPermissionRequester] (CLLocationManager for always-on location,
 *          UNUserNotificationCenter for push notifications).
 *          Calls [onPermissionsGranted] when CLAuthorizationStatus is authorizedAlways.
 */
expect @Composable fun PermissionsScreen(onPermissionsGranted: () -> Unit)

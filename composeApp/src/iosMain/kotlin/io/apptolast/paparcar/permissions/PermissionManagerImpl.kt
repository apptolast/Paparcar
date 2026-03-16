@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.permissions

import io.apptolast.paparcar.domain.permissions.AppPermissionState
import io.apptolast.paparcar.domain.permissions.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreMotion.CMAuthorizationStatusAuthorized
import platform.CoreMotion.CMMotionActivityManager
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS implementation of [PermissionManager].
 *
 * Location and activity auth are checked synchronously.
 * Notification auth requires an async callback — a cached value is used immediately
 * and the StateFlow is updated once the callback resolves.
 */
class PermissionManagerImpl : PermissionManager {

    private val locationManager = CLLocationManager()
    private val _permissionState = MutableStateFlow(AppPermissionState())
    override val permissionState: StateFlow<AppPermissionState> = _permissionState.asStateFlow()

    override fun refreshPermissions() {
        val authStatus = locationManager.authorizationStatus
        val hasLocation = authStatus == kCLAuthorizationStatusAuthorizedWhenInUse
            || authStatus == kCLAuthorizationStatusAuthorizedAlways
        val hasBackground = authStatus == kCLAuthorizationStatusAuthorizedAlways
        val hasActivity = CMMotionActivityManager.isActivityAvailable()
            && CMMotionActivityManager.authorizationStatus() == CMAuthorizationStatusAuthorized
        val gpsEnabled = CLLocationManager.locationServicesEnabled()

        // Synchronous fields updated immediately; notification status updated via callback.
        val current = _permissionState.value
        _permissionState.value = current.copy(
            hasLocationPermission = hasLocation,
            hasBackgroundLocationPermission = hasBackground,
            hasActivityRecognitionPermission = hasActivity,
            isLocationServicesEnabled = gpsEnabled,
        )

        // Async notification check — updates the StateFlow when the callback resolves.
        UNUserNotificationCenter.currentNotificationCenter()
            .getNotificationSettingsWithCompletionHandler { settings ->
                val granted = settings?.authorizationStatus == UNAuthorizationStatusAuthorized
                _permissionState.value = _permissionState.value.copy(
                    hasNotificationPermission = granted,
                )
            }
    }
}

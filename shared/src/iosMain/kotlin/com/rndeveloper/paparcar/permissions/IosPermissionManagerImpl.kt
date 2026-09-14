@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.permissions

import com.rndeveloper.paparcar.domain.permissions.AppPermissionState
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreMotion.CMAuthorizationStatusAuthorized
import platform.CoreMotion.CMMotionActivityManager
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue

/**
 * iOS implementation of [PermissionManager].
 *
 * Location and activity auth are checked synchronously.
 * Notification auth requires an async callback — a cached value is used immediately
 * and the StateFlow is updated once the callback resolves.
 * The system-wide Location Services switch is read off the main thread too: see [refreshPermissions].
 */
class IosPermissionManagerImpl : PermissionManager {

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

        // Synchronous fields updated immediately; notification status updated via callback.
        // An authorised status already proves Location Services are on: with the global switch off,
        // CoreLocation reports every app as denied. So the common case never flashes "GPS off".
        _permissionState.update { current ->
            current.copy(
                hasLocationPermission = hasLocation,
                hasBackgroundLocationPermission = hasBackground,
                hasActivityRecognitionPermission = hasActivity,
                isLocationServicesEnabled = hasLocation || current.isLocationServicesEnabled,
            )
        }

        // `CLLocationManager.locationServicesEnabled()` is a blocking round-trip to locationd, and
        // Xcode flags it on the main thread ("can cause UI unresponsiveness") — every caller of this
        // method is a ViewModel on Main. Read it on a background queue and publish the answer.
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            val servicesEnabled = CLLocationManager.locationServicesEnabled()
            _permissionState.update { it.copy(isLocationServicesEnabled = servicesEnabled) }
        }

        // Async notification check — updates the StateFlow when the callback resolves.
        UNUserNotificationCenter.currentNotificationCenter()
            .getNotificationSettingsWithCompletionHandler { settings ->
                val granted = settings?.authorizationStatus == UNAuthorizationStatusAuthorized
                _permissionState.update { it.copy(hasNotificationPermission = granted) }
            }
    }
}

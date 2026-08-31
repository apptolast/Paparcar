@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.ios.permissions

import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBManagerState
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreMotion.CMMotionActivityManager
import platform.Foundation.NSOperationQueue
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject

/**
 * Handles iOS-specific permission request flows using CLLocationManager,
 * CMMotionActivityManager and UNUserNotificationCenter.
 *
 * The CLLocationManagerDelegate is kept alive as a class-level property to
 * prevent it from being garbage-collected (CLLocationManager.delegate is weak).
 */
class IosPermissionRequester(private val permissionManager: PermissionManager) {

    private val locationManager = CLLocationManager()

    private val locationDelegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            permissionManager.refreshPermissions()
        }
    }

    init {
        locationManager.delegate = locationDelegate
    }

    /**
     * Step 1: request "when in use" location + notifications + activity (motion).
     * On iOS, notifications and activity dialogs are shown independently.
     */
    fun requestStep1() {
        locationManager.requestWhenInUseAuthorization()
        requestNotifications()
        requestActivityRecognition()
    }

    /**
     * Step 2: upgrade location to "always allow" (background location on iOS).
     * Must be called after step 1 has been granted.
     */
    fun requestAlwaysLocation() {
        locationManager.requestAlwaysAuthorization()
    }

    /**
     * PRODUCER sensors: the iOS counterpart of Android's activity-recognition +
     * notifications request. Presents the Motion & Fitness and Notifications
     * dialogs; both are idempotent once the user has answered.
     */
    fun requestProducerSensors() {
        requestNotifications()
        requestActivityRecognition()
    }

    /**
     * Notifications ALONE — the per-card direct grant [ONB-CARDS-001]. Public and not folded into
     * [requestProducerSensors] on purpose: a card that asks for one permission must raise one
     * dialog, or the user answers a question the card never posed.
     */
    fun requestNotifications() {
        val options = UNAuthorizationOptionAlert or
            UNAuthorizationOptionBadge or
            UNAuthorizationOptionSound
        UNUserNotificationCenter.currentNotificationCenter()
            .requestAuthorizationWithOptions(options) { _, _ ->
                permissionManager.refreshPermissions()
            }
    }

    /** Motion & Fitness ALONE — the per-card direct grant [ONB-CARDS-001]. See [requestNotifications]. */
    fun requestActivityRecognition() {
        if (!CMMotionActivityManager.isActivityAvailable()) return
        // Triggering a brief query causes iOS to show the Motion & Fitness permission dialog.
        val manager = CMMotionActivityManager()
        manager.startActivityUpdatesToQueue(NSOperationQueue.mainQueue) { _ ->
            manager.stopActivityUpdates()
            permissionManager.refreshPermissions()
        }
    }

    /**
     * Triggers the CoreBluetooth permission dialog by instantiating a CBCentralManager.
     * iOS presents the dialog on first access; subsequent calls are no-ops once granted/denied.
     * The manager is kept alive as a field to ensure the delegate receives the state callback.
     */
    private var btManager: CBCentralManager? = null
    private val btDelegate = object : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            permissionManager.refreshPermissions()
        }
    }

    fun requestBluetooth() {
        if (btManager == null) {
            btManager = CBCentralManager(btDelegate, null)
        }
        permissionManager.refreshPermissions()
    }
}

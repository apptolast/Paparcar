package io.apptolast.paparcar.ios.preferences

import io.apptolast.paparcar.domain.preferences.AppPreferences
import platform.Foundation.NSUserDefaults

private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
private const val KEY_AUTO_DETECT_PARKING = "auto_detect_parking"
private const val KEY_NOTIFY_PARKING_DETECTED = "notify_parking_detected"
private const val KEY_NOTIFY_SPOT_FREED = "notify_spot_freed"
private const val KEY_VEHICLE_REGISTERED = "vehicle_registered"

/**
 * iOS implementation of [AppPreferences] backed by [NSUserDefaults].
 * Equivalent to Android's SharedPreferences.
 */
class IosAppPreferences : AppPreferences {

    private val userDefaults = NSUserDefaults.standardUserDefaults

    override val isOnboardingCompleted: Boolean
        get() = userDefaults.boolForKey(KEY_ONBOARDING_COMPLETED)

    override fun setOnboardingCompleted() {
        userDefaults.setBool(true, forKey = KEY_ONBOARDING_COMPLETED)
    }

    override val autoDetectParking: Boolean
        get() = if (userDefaults.objectForKey(KEY_AUTO_DETECT_PARKING) == null) true
                else userDefaults.boolForKey(KEY_AUTO_DETECT_PARKING)

    override fun setAutoDetectParking(enabled: Boolean) {
        userDefaults.setBool(enabled, forKey = KEY_AUTO_DETECT_PARKING)
    }

    override val notifyParkingDetected: Boolean
        get() = if (userDefaults.objectForKey(KEY_NOTIFY_PARKING_DETECTED) == null) true
                else userDefaults.boolForKey(KEY_NOTIFY_PARKING_DETECTED)

    override fun setNotifyParkingDetected(enabled: Boolean) {
        userDefaults.setBool(enabled, forKey = KEY_NOTIFY_PARKING_DETECTED)
    }

    override val notifySpotFreed: Boolean
        get() = if (userDefaults.objectForKey(KEY_NOTIFY_SPOT_FREED) == null) true
                else userDefaults.boolForKey(KEY_NOTIFY_SPOT_FREED)

    override fun setNotifySpotFreed(enabled: Boolean) {
        userDefaults.setBool(enabled, forKey = KEY_NOTIFY_SPOT_FREED)
    }

    override val hasVehicleRegistered: Boolean
        get() = userDefaults.boolForKey(KEY_VEHICLE_REGISTERED)

    override fun setVehicleRegistered() {
        userDefaults.setBool(true, forKey = KEY_VEHICLE_REGISTERED)
    }
}

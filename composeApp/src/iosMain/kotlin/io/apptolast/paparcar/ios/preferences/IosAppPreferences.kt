package io.apptolast.paparcar.ios.preferences

import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.preferences.ThemeMode
import platform.Foundation.NSUserDefaults

private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
private const val KEY_AUTO_DETECT_PARKING = "auto_detect_parking"
private const val KEY_NOTIFY_PARKING_DETECTED = "notify_parking_detected"
private const val KEY_NOTIFY_SPOT_FREED = "notify_spot_freed"
private const val KEY_VEHICLE_REGISTERED = "vehicle_registered"
private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_USE_IMPERIAL_UNITS = "use_imperial_units"
private const val KEY_DEFAULT_MAP_TYPE = "default_map_type"
private const val KEY_SELECTED_LANGUAGE = "selected_language"
private const val DEFAULT_MAP_TYPE = "NORMAL"
private const val LANGUAGE_AUTO = "auto"

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

    override val themeMode: ThemeMode
        get() {
            userDefaults.stringForKey(KEY_THEME_MODE)?.let { stored ->
                return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
            }
            // Migration: legacy boolean → enum, then drop the old key.
            return if (userDefaults.objectForKey(KEY_DARK_MODE_ENABLED) != null) {
                val migrated = if (userDefaults.boolForKey(KEY_DARK_MODE_ENABLED)) ThemeMode.DARK else ThemeMode.LIGHT
                userDefaults.setObject(migrated.name, forKey = KEY_THEME_MODE)
                userDefaults.removeObjectForKey(KEY_DARK_MODE_ENABLED)
                migrated
            } else {
                ThemeMode.SYSTEM
            }
        }

    override fun setThemeMode(mode: ThemeMode) {
        userDefaults.setObject(mode.name, forKey = KEY_THEME_MODE)
    }

    override val useImperialUnits: Boolean
        get() = userDefaults.boolForKey(KEY_USE_IMPERIAL_UNITS)

    override fun setUseImperialUnits(enabled: Boolean) {
        userDefaults.setBool(enabled, forKey = KEY_USE_IMPERIAL_UNITS)
    }

    override val defaultMapType: String
        get() = userDefaults.stringForKey(KEY_DEFAULT_MAP_TYPE) ?: DEFAULT_MAP_TYPE

    override fun setDefaultMapType(type: String) {
        userDefaults.setObject(type, forKey = KEY_DEFAULT_MAP_TYPE)
    }

    override val selectedLanguage: String
        get() = userDefaults.stringForKey(KEY_SELECTED_LANGUAGE) ?: LANGUAGE_AUTO

    override fun setSelectedLanguage(tag: String) {
        userDefaults.setObject(tag, forKey = KEY_SELECTED_LANGUAGE)
    }
}

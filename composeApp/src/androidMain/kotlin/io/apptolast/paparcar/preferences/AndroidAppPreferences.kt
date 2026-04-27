package io.apptolast.paparcar.preferences

import android.content.Context
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.preferences.ThemeMode
import androidx.core.content.edit

private const val PREFS_NAME = "paparcar_prefs"
private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
private const val KEY_AUTO_DETECT_PARKING = "auto_detect_parking"
private const val KEY_NOTIFY_PARKING_DETECTED = "notify_parking_detected"
private const val KEY_NOTIFY_SPOT_FREED = "notify_spot_freed"
private const val KEY_VEHICLE_REGISTERED = "vehicle_registered"
private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_USE_IMPERIAL_UNITS = "use_imperial_units"
private const val KEY_DEFAULT_MAP_TYPE = "default_map_type"
private const val DEFAULT_MAP_TYPE = "NORMAL"

class AndroidAppPreferences(context: Context) : AppPreferences {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    override fun setOnboardingCompleted() {
        prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, true) }
    }

    override val autoDetectParking: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DETECT_PARKING, true)

    override fun setAutoDetectParking(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_DETECT_PARKING, enabled) }
    }

    override val notifyParkingDetected: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_PARKING_DETECTED, true)

    override fun setNotifyParkingDetected(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_NOTIFY_PARKING_DETECTED, enabled) }
    }

    override val notifySpotFreed: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_SPOT_FREED, true)

    override fun setNotifySpotFreed(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_NOTIFY_SPOT_FREED, enabled) }
    }

    override val hasVehicleRegistered: Boolean
        get() = prefs.getBoolean(KEY_VEHICLE_REGISTERED, false)

    override fun setVehicleRegistered() {
        prefs.edit { putBoolean(KEY_VEHICLE_REGISTERED, true) }
    }

    override val themeMode: ThemeMode
        get() {
            prefs.getString(KEY_THEME_MODE, null)?.let { stored ->
                return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
            }
            // Migration: legacy boolean → enum, then drop the old key.
            return if (prefs.contains(KEY_DARK_MODE_ENABLED)) {
                val migrated = if (prefs.getBoolean(KEY_DARK_MODE_ENABLED, true)) ThemeMode.DARK else ThemeMode.LIGHT
                prefs.edit {
                    putString(KEY_THEME_MODE, migrated.name)
                    remove(KEY_DARK_MODE_ENABLED)
                }
                migrated
            } else {
                ThemeMode.SYSTEM
            }
        }

    override fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    override val useImperialUnits: Boolean
        get() = prefs.getBoolean(KEY_USE_IMPERIAL_UNITS, false)

    override fun setUseImperialUnits(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_USE_IMPERIAL_UNITS, enabled) }
    }

    override val defaultMapType: String
        get() = prefs.getString(KEY_DEFAULT_MAP_TYPE, DEFAULT_MAP_TYPE) ?: DEFAULT_MAP_TYPE

    override fun setDefaultMapType(type: String) {
        prefs.edit { putString(KEY_DEFAULT_MAP_TYPE, type) }
    }
}

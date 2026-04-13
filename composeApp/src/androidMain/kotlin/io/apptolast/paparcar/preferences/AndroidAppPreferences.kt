package io.apptolast.paparcar.preferences

import android.content.Context
import io.apptolast.paparcar.domain.preferences.AppPreferences
import androidx.core.content.edit

private const val PREFS_NAME = "paparcar_prefs"
private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
private const val KEY_AUTO_DETECT_PARKING = "auto_detect_parking"
private const val KEY_NOTIFY_PARKING_DETECTED = "notify_parking_detected"
private const val KEY_NOTIFY_SPOT_FREED = "notify_spot_freed"
private const val KEY_VEHICLE_REGISTERED = "vehicle_registered"
private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
private const val KEY_USE_IMPERIAL_UNITS = "use_imperial_units"

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

    override val darkModeEnabled: Boolean
        get() = if (prefs.contains(KEY_DARK_MODE_ENABLED)) prefs.getBoolean(KEY_DARK_MODE_ENABLED, true) else true

    override fun setDarkModeEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DARK_MODE_ENABLED, enabled) }
    }

    override val useImperialUnits: Boolean
        get() = prefs.getBoolean(KEY_USE_IMPERIAL_UNITS, false)

    override fun setUseImperialUnits(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_USE_IMPERIAL_UNITS, enabled) }
    }
}

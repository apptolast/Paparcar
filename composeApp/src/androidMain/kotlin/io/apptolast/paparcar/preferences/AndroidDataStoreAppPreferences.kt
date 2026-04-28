package io.apptolast.paparcar.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.preferencesDataStore
import io.apptolast.paparcar.domain.preferences.AppPreferences
import io.apptolast.paparcar.domain.preferences.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATASTORE_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, LEGACY_PREFS_NAME))
    },
)

class AndroidDataStoreAppPreferences(context: Context) : AppPreferences {

    private val store = context.dataStore

    private fun <T> get(key: Preferences.Key<T>, default: T): T =
        runBlocking { store.data.map { it[key] ?: default }.first() }

    private fun <T> set(key: Preferences.Key<T>, value: T): Unit =
        runBlocking { store.edit { it[key] = value }.let {} }

    // ── Onboarding ──────────────────────────────────────────────────────────

    override val isOnboardingCompleted: Boolean
        get() = get(Keys.ONBOARDING_COMPLETED, false)

    override fun setOnboardingCompleted() = set(Keys.ONBOARDING_COMPLETED, true)

    override val hasSeenGpsAccuracyDisclaimer: Boolean
        get() = get(Keys.GPS_ACCURACY_DISCLAIMER_SEEN, false)

    override fun setGpsAccuracyDisclaimerSeen() = set(Keys.GPS_ACCURACY_DISCLAIMER_SEEN, true)

    // ── Parking detection ────────────────────────────────────────────────────

    override val autoDetectParking: Boolean
        get() = get(Keys.AUTO_DETECT_PARKING, true)

    override fun setAutoDetectParking(enabled: Boolean) = set(Keys.AUTO_DETECT_PARKING, enabled)

    // ── Notifications ────────────────────────────────────────────────────────

    override val notifyParkingDetected: Boolean
        get() = get(Keys.NOTIFY_PARKING_DETECTED, true)

    override fun setNotifyParkingDetected(enabled: Boolean) = set(Keys.NOTIFY_PARKING_DETECTED, enabled)

    override val notifySpotFreed: Boolean
        get() = get(Keys.NOTIFY_SPOT_FREED, true)

    override fun setNotifySpotFreed(enabled: Boolean) = set(Keys.NOTIFY_SPOT_FREED, enabled)

    // ── Vehicle ──────────────────────────────────────────────────────────────

    override val hasVehicleRegistered: Boolean
        get() = get(Keys.VEHICLE_REGISTERED, false)

    override fun setVehicleRegistered() = set(Keys.VEHICLE_REGISTERED, true)

    // ── Theme ────────────────────────────────────────────────────────────────

    override val themeMode: ThemeMode
        get() {
            val stored = get(Keys.THEME_MODE, ThemeMode.SYSTEM.name)
            return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
        }

    override fun setThemeMode(mode: ThemeMode) = set(Keys.THEME_MODE, mode.name)

    // ── Units ────────────────────────────────────────────────────────────────

    override val useImperialUnits: Boolean
        get() = get(Keys.USE_IMPERIAL_UNITS, false)

    override fun setUseImperialUnits(enabled: Boolean) = set(Keys.USE_IMPERIAL_UNITS, enabled)

    // ── Map ──────────────────────────────────────────────────────────────────

    override val defaultMapType: String
        get() = get(Keys.DEFAULT_MAP_TYPE, DEFAULT_MAP_TYPE)

    override fun setDefaultMapType(type: String) = set(Keys.DEFAULT_MAP_TYPE, type)

    // ── Language ─────────────────────────────────────────────────────────────

    override val selectedLanguage: String
        get() = get(Keys.SELECTED_LANGUAGE, LANGUAGE_AUTO)

    override fun setSelectedLanguage(tag: String) = set(Keys.SELECTED_LANGUAGE, tag)

    // ── Keys ─────────────────────────────────────────────────────────────────

    private object Keys {
        val ONBOARDING_COMPLETED            = booleanPreferencesKey("onboarding_completed")
        val GPS_ACCURACY_DISCLAIMER_SEEN    = booleanPreferencesKey("gps_accuracy_disclaimer_seen")
        val AUTO_DETECT_PARKING     = booleanPreferencesKey("auto_detect_parking")
        val NOTIFY_PARKING_DETECTED = booleanPreferencesKey("notify_parking_detected")
        val NOTIFY_SPOT_FREED       = booleanPreferencesKey("notify_spot_freed")
        val VEHICLE_REGISTERED      = booleanPreferencesKey("vehicle_registered")
        val THEME_MODE              = stringPreferencesKey("theme_mode")
        val USE_IMPERIAL_UNITS      = booleanPreferencesKey("use_imperial_units")
        val DEFAULT_MAP_TYPE        = stringPreferencesKey("default_map_type")
        val SELECTED_LANGUAGE       = stringPreferencesKey("selected_language")
    }

    private companion object {
        const val DEFAULT_MAP_TYPE = "NORMAL"
        const val LANGUAGE_AUTO    = "auto"
    }
}

private const val DATASTORE_NAME    = "paparcar_prefs"
private const val LEGACY_PREFS_NAME = "paparcar_prefs"

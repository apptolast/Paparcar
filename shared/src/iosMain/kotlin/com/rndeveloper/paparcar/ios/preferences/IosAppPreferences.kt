package com.rndeveloper.paparcar.ios.preferences

import com.rndeveloper.paparcar.domain.detection.PendingParkNudge
import com.rndeveloper.paparcar.localePrefersImperialUnits
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.preferences.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
private const val KEY_GPS_ACCURACY_DISCLAIMER_SEEN = "gps_accuracy_disclaimer_seen"
private const val KEY_LOCATION_PERMISSION_REQUESTED = "location_permission_requested"
private const val KEY_AUTO_DETECT_PARKING = "auto_detect_parking"
private const val KEY_FIRST_PARK_NUDGE_COUNT = "first_park_nudge_count"
private const val KEY_LAST_FIRST_PARK_NUDGE_AT = "last_first_park_nudge_at"
private const val KEY_HAS_CONFIRMED_FIRST_PARK = "has_confirmed_first_park"
private const val KEY_PENDING_NUDGE_CREATED_AT = "pending_park_nudge_created_at"
private const val KEY_PENDING_NUDGE_SOURCE = "pending_park_nudge_source"
private const val KEY_PENDING_NUDGE_VEHICLE_ID = "pending_park_nudge_vehicle_id"
private const val KEY_PENDING_PROMPT_SHOWN_AT = "pending_prompt_window_shown_at"
private const val KEY_PENDING_PROMPT_VEHICLE_NAME = "pending_prompt_window_vehicle_name"
private const val KEY_NOTIFY_PARKING_DETECTED = "notify_parking_detected"
private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_USE_IMPERIAL_UNITS = "use_imperial_units"
private const val KEY_DEFAULT_MAP_TYPE = "default_map_type"
private const val DEFAULT_MAP_TYPE = "TERRAIN"

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

    override val hasSeenGpsAccuracyDisclaimer: Boolean
        get() = userDefaults.boolForKey(KEY_GPS_ACCURACY_DISCLAIMER_SEEN)

    override fun setGpsAccuracyDisclaimerSeen() {
        userDefaults.setBool(true, forKey = KEY_GPS_ACCURACY_DISCLAIMER_SEEN)
    }

    override val hasRequestedLocationPermission: Boolean
        get() = userDefaults.boolForKey(KEY_LOCATION_PERMISSION_REQUESTED)

    override fun setLocationPermissionRequested() {
        userDefaults.setBool(true, forKey = KEY_LOCATION_PERMISSION_REQUESTED)
    }

    override val autoDetectParking: Boolean
        get() = if (userDefaults.objectForKey(KEY_AUTO_DETECT_PARKING) == null) true
                else userDefaults.boolForKey(KEY_AUTO_DETECT_PARKING)

    // Single-process app: a StateFlow seeded from the stored value and updated in the setter is enough
    // for Home/orchestration to react live (NSUserDefaults has no native Kotlin Flow). [DET-TOGGLE-001]
    private val autoDetectFlow = MutableStateFlow(autoDetectParking)

    override fun setAutoDetectParking(enabled: Boolean) {
        userDefaults.setBool(enabled, forKey = KEY_AUTO_DETECT_PARKING)
        autoDetectFlow.value = enabled
    }

    override fun observeAutoDetectParking(): Flow<Boolean> = autoDetectFlow.asStateFlow()

    // ── First-park nudge ───────────────────────────────────────────────────────

    override val firstParkNudgeCount: Int
        get() = userDefaults.integerForKey(KEY_FIRST_PARK_NUDGE_COUNT).toInt()

    override fun setFirstParkNudgeCount(count: Int) {
        userDefaults.setInteger(count.toLong(), forKey = KEY_FIRST_PARK_NUDGE_COUNT)
    }

    override val lastFirstParkNudgeAtMillis: Long
        get() = userDefaults.integerForKey(KEY_LAST_FIRST_PARK_NUDGE_AT)

    override fun setLastFirstParkNudgeAt(millis: Long) {
        userDefaults.setInteger(millis, forKey = KEY_LAST_FIRST_PARK_NUDGE_AT)
    }

    override val hasConfirmedFirstPark: Boolean
        get() = userDefaults.boolForKey(KEY_HAS_CONFIRMED_FIRST_PARK)

    override fun setHasConfirmedFirstPark() {
        userDefaults.setBool(true, forKey = KEY_HAS_CONFIRMED_FIRST_PARK)
    }

    // ── Pending mark-parking nudge. [DET-NUDGE-PERSIST-001] ──────────────────
    // Single-process app: StateFlow seeded from the stored values, updated in the setters
    // (same pattern as [observeAutoDetectParking]).

    private fun storedPendingParkNudge(): PendingParkNudge? {
        if (userDefaults.objectForKey(KEY_PENDING_NUDGE_CREATED_AT) == null) return null
        return PendingParkNudge(
            createdAtMs = userDefaults.integerForKey(KEY_PENDING_NUDGE_CREATED_AT),
            source = userDefaults.stringForKey(KEY_PENDING_NUDGE_SOURCE) ?: "",
            vehicleId = userDefaults.stringForKey(KEY_PENDING_NUDGE_VEHICLE_ID),
        )
    }

    private val pendingParkNudgeFlow = MutableStateFlow(storedPendingParkNudge())

    override fun observePendingParkNudge(): Flow<PendingParkNudge?> = pendingParkNudgeFlow.asStateFlow()

    override fun setPendingParkNudge(nudge: PendingParkNudge) {
        userDefaults.setInteger(nudge.createdAtMs, forKey = KEY_PENDING_NUDGE_CREATED_AT)
        userDefaults.setObject(nudge.source, forKey = KEY_PENDING_NUDGE_SOURCE)
        nudge.vehicleId
            ?.let { userDefaults.setObject(it, forKey = KEY_PENDING_NUDGE_VEHICLE_ID) }
            ?: userDefaults.removeObjectForKey(KEY_PENDING_NUDGE_VEHICLE_ID)
        pendingParkNudgeFlow.value = nudge
    }

    override fun clearPendingParkNudge() {
        userDefaults.removeObjectForKey(KEY_PENDING_NUDGE_CREATED_AT)
        userDefaults.removeObjectForKey(KEY_PENDING_NUDGE_SOURCE)
        userDefaults.removeObjectForKey(KEY_PENDING_NUDGE_VEHICLE_ID)
        pendingParkNudgeFlow.value = null
    }

    // ── Open "did you park?" question. [DET-ASK-STATE-001] ───────────────────

    private fun storedPendingPromptWindow(): PendingPromptWindow? {
        if (userDefaults.objectForKey(KEY_PENDING_PROMPT_SHOWN_AT) == null) return null
        return PendingPromptWindow(
            shownAtMs = userDefaults.integerForKey(KEY_PENDING_PROMPT_SHOWN_AT),
            vehicleName = userDefaults.stringForKey(KEY_PENDING_PROMPT_VEHICLE_NAME),
        )
    }

    private val pendingPromptWindowFlow = MutableStateFlow(storedPendingPromptWindow())

    override fun observePendingPromptWindow(): Flow<PendingPromptWindow?> = pendingPromptWindowFlow.asStateFlow()

    override fun setPendingPromptWindow(window: PendingPromptWindow) {
        userDefaults.setInteger(window.shownAtMs, forKey = KEY_PENDING_PROMPT_SHOWN_AT)
        window.vehicleName
            ?.let { userDefaults.setObject(it, forKey = KEY_PENDING_PROMPT_VEHICLE_NAME) }
            ?: userDefaults.removeObjectForKey(KEY_PENDING_PROMPT_VEHICLE_NAME)
        pendingPromptWindowFlow.value = window
    }

    override fun clearPendingPromptWindow() {
        userDefaults.removeObjectForKey(KEY_PENDING_PROMPT_SHOWN_AT)
        userDefaults.removeObjectForKey(KEY_PENDING_PROMPT_VEHICLE_NAME)
        pendingPromptWindowFlow.value = null
    }

    override val notifyParkingDetected: Boolean
        get() = if (userDefaults.objectForKey(KEY_NOTIFY_PARKING_DETECTED) == null) true
                else userDefaults.boolForKey(KEY_NOTIFY_PARKING_DETECTED)

    override fun setNotifyParkingDetected(enabled: Boolean) {
        userDefaults.setBool(enabled, forKey = KEY_NOTIFY_PARKING_DETECTED)
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
        get() = if (userDefaults.objectForKey(KEY_USE_IMPERIAL_UNITS) != null) {
            userDefaults.boolForKey(KEY_USE_IMPERIAL_UNITS)
        } else {
            localePrefersImperialUnits()
        }

    override fun setUseImperialUnits(enabled: Boolean) {
        userDefaults.setBool(enabled, forKey = KEY_USE_IMPERIAL_UNITS)
    }

    override val defaultMapType: String
        get() = userDefaults.stringForKey(KEY_DEFAULT_MAP_TYPE) ?: DEFAULT_MAP_TYPE

    override fun setDefaultMapType(type: String) {
        userDefaults.setObject(type, forKey = KEY_DEFAULT_MAP_TYPE)
    }
}

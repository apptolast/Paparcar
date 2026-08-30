package com.rndeveloper.paparcar.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rndeveloper.paparcar.domain.detection.PendingParkNudge
import com.rndeveloper.paparcar.localePrefersImperialUnits
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.preferences.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// No produceMigrations: the legacy SharedPreferences file ("paparcar_prefs") was fully migrated
// long before the pre-beta Room v1 reset — no device still carries un-migrated data, and fresh
// installs never had the file. NOTE this covers USER prefs only: the detection stores keep raw
// SharedPreferences ON PURPOSE (synchronous commit that survives imminent process death — see
// TripTrailImpl / ParkingSafetyNetWorker KDocs). [SETTINGS-AUDIT-REMEDIATION-001]
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)

class AndroidDataStoreAppPreferences(context: Context) : AppPreferences {

    private val store = context.dataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Single blocking warmup at construction. The DataStore APIs are suspend-only,
    // but the [AppPreferences] contract is synchronous (read on Main thread, before
    // first composition, by AppViewModel/SplashViewModel). Blocking once at startup
    // is the trade-off that lets every subsequent getter return from the in-memory
    // snapshot without touching disk or jumping threads — see [PERF-001].
    @Volatile
    private var snapshot: Preferences = runBlocking { store.data.first() }

    init {
        store.data
            .onEach { snapshot = it }
            .launchIn(scope)
    }

    private fun <T> get(key: Preferences.Key<T>, default: T): T =
        snapshot[key] ?: default

    private fun <T> set(key: Preferences.Key<T>, value: T) {
        // Optimistic in-memory update so the next sync getter returns the new value
        // before the async DataStore write flushes. The collect() above will reconcile
        // shortly with the persisted state.
        snapshot = snapshot.toMutablePreferences().apply { this[key] = value }
        scope.launch { store.edit { it[key] = value } }
    }

    // ── Onboarding ──────────────────────────────────────────────────────────

    override val isOnboardingCompleted: Boolean
        get() = get(Keys.ONBOARDING_COMPLETED, false)

    override fun setOnboardingCompleted() = set(Keys.ONBOARDING_COMPLETED, true)

    override val hasSeenGpsAccuracyDisclaimer: Boolean
        get() = get(Keys.GPS_ACCURACY_DISCLAIMER_SEEN, false)

    override fun setGpsAccuracyDisclaimerSeen() = set(Keys.GPS_ACCURACY_DISCLAIMER_SEEN, true)

    override val hasRequestedLocationPermission: Boolean
        get() = get(Keys.LOCATION_PERMISSION_REQUESTED, false)

    override fun setLocationPermissionRequested() = set(Keys.LOCATION_PERMISSION_REQUESTED, true)

    // ── Parking detection ────────────────────────────────────────────────────

    override val autoDetectParking: Boolean
        get() = get(Keys.AUTO_DETECT_PARKING, true)

    override fun setAutoDetectParking(enabled: Boolean) = set(Keys.AUTO_DETECT_PARKING, enabled)

    override fun observeAutoDetectParking(): Flow<Boolean> =
        store.data.map { it[Keys.AUTO_DETECT_PARKING] ?: true }.distinctUntilChanged()

    // ── First-park nudge ───────────────────────────────────────────────────────

    override val firstParkNudgeCount: Int
        get() = get(Keys.FIRST_PARK_NUDGE_COUNT, 0)

    override fun setFirstParkNudgeCount(count: Int) = set(Keys.FIRST_PARK_NUDGE_COUNT, count)

    override val lastFirstParkNudgeAtMillis: Long
        get() = get(Keys.LAST_FIRST_PARK_NUDGE_AT, 0L)

    override fun setLastFirstParkNudgeAt(millis: Long) = set(Keys.LAST_FIRST_PARK_NUDGE_AT, millis)

    override val hasConfirmedFirstPark: Boolean
        get() = get(Keys.HAS_CONFIRMED_FIRST_PARK, false)

    override fun setHasConfirmedFirstPark() = set(Keys.HAS_CONFIRMED_FIRST_PARK, true)

    // ── Pending mark-parking nudge. [DET-NUDGE-PERSIST-001] ──────────────────

    override fun observePendingParkNudge(): Flow<PendingParkNudge?> =
        store.data.map { it.toPendingParkNudge() }.distinctUntilChanged()

    override fun setPendingParkNudge(nudge: PendingParkNudge) {
        snapshot = snapshot.toMutablePreferences().apply { write(nudge) }
        scope.launch { store.edit { it.write(nudge) } }
    }

    override fun clearPendingParkNudge() {
        snapshot = snapshot.toMutablePreferences().apply { write(null) }
        scope.launch { store.edit { it.write(null) } }
    }

    private fun Preferences.toPendingParkNudge(): PendingParkNudge? {
        val createdAt = this[Keys.PENDING_NUDGE_CREATED_AT] ?: return null
        return PendingParkNudge(
            createdAtMs = createdAt,
            source = this[Keys.PENDING_NUDGE_SOURCE] ?: "",
            vehicleId = this[Keys.PENDING_NUDGE_VEHICLE_ID],
        )
    }

    private fun MutablePreferences.write(nudge: PendingParkNudge?) {
        if (nudge == null) {
            remove(Keys.PENDING_NUDGE_CREATED_AT)
            remove(Keys.PENDING_NUDGE_SOURCE)
            remove(Keys.PENDING_NUDGE_VEHICLE_ID)
        } else {
            this[Keys.PENDING_NUDGE_CREATED_AT] = nudge.createdAtMs
            this[Keys.PENDING_NUDGE_SOURCE] = nudge.source
            nudge.vehicleId?.let { this[Keys.PENDING_NUDGE_VEHICLE_ID] = it }
                ?: remove(Keys.PENDING_NUDGE_VEHICLE_ID)
        }
    }

    // ── Open "did you park?" question. [DET-ASK-STATE-001] ───────────────────

    override fun observePendingPromptWindow(): Flow<PendingPromptWindow?> =
        store.data.map { it.toPendingPromptWindow() }.distinctUntilChanged()

    override fun setPendingPromptWindow(window: PendingPromptWindow) {
        snapshot = snapshot.toMutablePreferences().apply { writePromptWindow(window) }
        scope.launch { store.edit { it.writePromptWindow(window) } }
    }

    override fun clearPendingPromptWindow() {
        snapshot = snapshot.toMutablePreferences().apply { writePromptWindow(null) }
        scope.launch { store.edit { it.writePromptWindow(null) } }
    }

    private fun Preferences.toPendingPromptWindow(): PendingPromptWindow? {
        val shownAt = this[Keys.PENDING_PROMPT_SHOWN_AT] ?: return null
        val lat = this[Keys.PENDING_PROMPT_LAT]
        val lon = this[Keys.PENDING_PROMPT_LON]
        return PendingPromptWindow(
            shownAtMs = shownAt,
            vehicleName = this[Keys.PENDING_PROMPT_VEHICLE_NAME],
            street = this[Keys.PENDING_PROMPT_STREET],
            // [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] Both halves or neither: half a
            // coordinate is a point off the coast of Africa, not a missing one. A window written by
            // a build that predates this field reads as "no place", which is what it is.
            candidate = if (lat != null && lon != null) {
                GpsPoint(
                    latitude = lat,
                    longitude = lon,
                    accuracy = this[Keys.PENDING_PROMPT_ACC] ?: 0f,
                    timestamp = this[Keys.PENDING_PROMPT_AT] ?: shownAt,
                    // A witnessed car stop is stationary by construction — this is the definition
                    // of the field, not a value lost in the round-trip.
                    speed = 0f,
                )
            } else {
                null
            },
        )
    }

    // Named apart from the nudge's `write` on purpose: two nullable overloads make `write(null)`
    // ambiguous, and the compiler is right — "clear the slot" has to say WHICH slot.
    private fun MutablePreferences.writePromptWindow(window: PendingPromptWindow?) {
        if (window == null) {
            remove(Keys.PENDING_PROMPT_SHOWN_AT)
            remove(Keys.PENDING_PROMPT_VEHICLE_NAME)
            remove(Keys.PENDING_PROMPT_STREET)
            removePromptCandidate()
        } else {
            this[Keys.PENDING_PROMPT_SHOWN_AT] = window.shownAtMs
            window.vehicleName?.let { this[Keys.PENDING_PROMPT_VEHICLE_NAME] = it }
                ?: remove(Keys.PENDING_PROMPT_VEHICLE_NAME)
            window.street?.let { this[Keys.PENDING_PROMPT_STREET] = it }
                ?: remove(Keys.PENDING_PROMPT_STREET)
            window.candidate?.let {
                this[Keys.PENDING_PROMPT_LAT] = it.latitude
                this[Keys.PENDING_PROMPT_LON] = it.longitude
                this[Keys.PENDING_PROMPT_ACC] = it.accuracy
                this[Keys.PENDING_PROMPT_AT] = it.timestamp
            } ?: removePromptCandidate()
        }
    }

    /** The four keys of the candidate place go together — a partial clear would leave a coordinate
     *  wearing the previous ask's accuracy. */
    private fun MutablePreferences.removePromptCandidate() {
        remove(Keys.PENDING_PROMPT_LAT)
        remove(Keys.PENDING_PROMPT_LON)
        remove(Keys.PENDING_PROMPT_ACC)
        remove(Keys.PENDING_PROMPT_AT)
    }

    // ── Notifications ────────────────────────────────────────────────────────

    override val notifyParkingDetected: Boolean
        get() = get(Keys.NOTIFY_PARKING_DETECTED, true)

    override fun setNotifyParkingDetected(enabled: Boolean) = set(Keys.NOTIFY_PARKING_DETECTED, enabled)

    // ── Theme ────────────────────────────────────────────────────────────────

    override val themeMode: ThemeMode
        get() {
            val stored = get(Keys.THEME_MODE, ThemeMode.SYSTEM.name)
            return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
        }

    override fun setThemeMode(mode: ThemeMode) = set(Keys.THEME_MODE, mode.name)

    // ── Units ────────────────────────────────────────────────────────────────

    override val useImperialUnits: Boolean
        get() = get(Keys.USE_IMPERIAL_UNITS, localePrefersImperialUnits())

    override fun setUseImperialUnits(enabled: Boolean) = set(Keys.USE_IMPERIAL_UNITS, enabled)

    // ── Map ──────────────────────────────────────────────────────────────────

    override val defaultMapType: String
        get() = get(Keys.DEFAULT_MAP_TYPE, DEFAULT_MAP_TYPE)

    override fun setDefaultMapType(type: String) = set(Keys.DEFAULT_MAP_TYPE, type)

    // ── Keys ─────────────────────────────────────────────────────────────────

    private object Keys {
        val ONBOARDING_COMPLETED            = booleanPreferencesKey("onboarding_completed")
        val GPS_ACCURACY_DISCLAIMER_SEEN    = booleanPreferencesKey("gps_accuracy_disclaimer_seen")
        val LOCATION_PERMISSION_REQUESTED   = booleanPreferencesKey("location_permission_requested")
        val AUTO_DETECT_PARKING     = booleanPreferencesKey("auto_detect_parking")
        val FIRST_PARK_NUDGE_COUNT  = intPreferencesKey("first_park_nudge_count")
        val LAST_FIRST_PARK_NUDGE_AT = longPreferencesKey("last_first_park_nudge_at")
        val HAS_CONFIRMED_FIRST_PARK = booleanPreferencesKey("has_confirmed_first_park")
        val PENDING_NUDGE_CREATED_AT = longPreferencesKey("pending_park_nudge_created_at")
        val PENDING_NUDGE_SOURCE     = stringPreferencesKey("pending_park_nudge_source")
        val PENDING_NUDGE_VEHICLE_ID = stringPreferencesKey("pending_park_nudge_vehicle_id")
        val PENDING_PROMPT_SHOWN_AT  = longPreferencesKey("pending_prompt_window_shown_at")
        val PENDING_PROMPT_VEHICLE_NAME = stringPreferencesKey("pending_prompt_window_vehicle_name")
        // [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] Where the question is about. The ACCURACY
        // travels with the coordinate: a marker that cannot say how sure it is would be drawn as a
        // precise claim, which is the one thing an unconfirmed pin must not do.
        val PENDING_PROMPT_LAT = doublePreferencesKey("pending_prompt_window_lat")
        val PENDING_PROMPT_LON = doublePreferencesKey("pending_prompt_window_lon")
        val PENDING_PROMPT_ACC = floatPreferencesKey("pending_prompt_window_accuracy")
        val PENDING_PROMPT_AT = longPreferencesKey("pending_prompt_window_witnessed_at")
        val PENDING_PROMPT_STREET = stringPreferencesKey("pending_prompt_window_street")
        val NOTIFY_PARKING_DETECTED = booleanPreferencesKey("notify_parking_detected")
        // "notify_spot_freed" removed 2026-08-28: it gated a notification that never existed.
        // Stale values may linger in the DataStore file; they are inert. [SETTINGS-AUDIT-REMEDIATION-001]
        val THEME_MODE              = stringPreferencesKey("theme_mode")
        val USE_IMPERIAL_UNITS      = booleanPreferencesKey("use_imperial_units")
        val DEFAULT_MAP_TYPE        = stringPreferencesKey("default_map_type")
        // "selected_language" removed 2026-08-29: the in-app picker never applied anything and the
        // OS owns the app language now. Stale values linger in the file; they are inert.
        // [SETTINGS-LANGUAGE-LIVES-IN-THE-SYSTEM-001]
    }

    private companion object {
        const val DEFAULT_MAP_TYPE = "TERRAIN"
    }
}

private const val DATASTORE_NAME = "paparcar_prefs"

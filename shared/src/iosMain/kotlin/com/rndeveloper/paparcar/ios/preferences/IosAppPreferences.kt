package com.rndeveloper.paparcar.ios.preferences

import com.rndeveloper.paparcar.domain.spot.VouchedSpot
import com.rndeveloper.paparcar.domain.detection.PendingParkNudge
import com.rndeveloper.paparcar.domain.detection.PendingSpotRelease
import com.rndeveloper.paparcar.domain.detection.SpotReleaseLane
import com.rndeveloper.paparcar.localePrefersImperialUnits
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.onboarding.NearbyComplement
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.preferences.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults
import com.rndeveloper.paparcar.domain.analytics.AdMeasurementConsent

private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
private const val KEY_GPS_ACCURACY_DISCLAIMER_SEEN = "gps_accuracy_disclaimer_seen"
// "legal_consent_accepted" retired with the Android key, same reason: not a valid ad consent.
// [MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001]
private const val KEY_AD_MEASUREMENT_CONSENT = "ad_measurement_consent"
private const val KEY_LOCATION_PERMISSION_REQUESTED = "location_permission_requested"
private const val KEY_AUTO_DETECT_PARKING = "auto_detect_parking"
private const val KEY_PENDING_RELEASE_SPOT_ID = "pending_spot_release_id"
private const val KEY_PENDING_RELEASE_VEHICLE_ID = "pending_spot_release_vehicle_id"
private const val KEY_PENDING_RELEASE_AT = "pending_spot_release_at"
private const val KEY_PENDING_RELEASE_LANE = "pending_spot_release_lane"
private const val KEY_FIRST_PARK_NUDGE_COUNT = "first_park_nudge_count"
private const val KEY_LAST_FIRST_PARK_NUDGE_AT = "last_first_park_nudge_at"
private const val KEY_HAS_CONFIRMED_FIRST_PARK = "has_confirmed_first_park"
private const val KEY_FIRST_STEPS_DONE = "first_steps_done"
private const val KEY_FIRST_STEPS_DEFERRED = "first_steps_deferred"
private const val KEY_INTRODUCED_COMPLEMENTS = "introduced_complements"
private const val KEY_FIRST_STEPS_DISMISSED = "first_steps_dismissed"
private const val KEY_PENDING_NUDGE_CREATED_AT = "pending_park_nudge_created_at"
private const val KEY_PENDING_NUDGE_SOURCE = "pending_park_nudge_source"
private const val KEY_PENDING_NUDGE_VEHICLE_ID = "pending_park_nudge_vehicle_id"
private const val KEY_STILL_PARKED_QUESTION = "still_parked_question"
private const val KEY_PENDING_PROMPT_SHOWN_AT = "pending_prompt_window_shown_at"
private const val KEY_PENDING_PROMPT_VEHICLE_NAME = "pending_prompt_window_vehicle_name"
private const val KEY_PENDING_PROMPT_VEHICLE_ID = "pending_prompt_window_vehicle_id"
private const val KEY_NOTIFY_PARKING_DETECTED = "notify_parking_detected"
private const val KEY_NOTIFY_ZONE_SPOTS = "notify_zone_spots"
private const val KEY_NOTIFY_FUEL_PRICES = "notify_fuel_prices"
private const val KEY_NOTIFY_MONTH_RECAP = "notify_month_recap"
private const val KEY_LAST_MONTH_RECAP = "last_month_recap_announced"
private const val KEY_VOUCHED_SPOT = "vouched_spot" // [SPOT-A-SPACE-THE-COMMUNITY-GAVE-YOU-IS-ONE-YOU-VOUCHED-FOR-001]
/** ⛔ El MISMO separador y el MISMO número de campos que Android: un registro, un formato. */
private const val VOUCH_SEPARATOR = "|"
private const val VOUCH_FIELDS = 4
private const val KEY_SYNC_ROUTE_GEOMETRY = "sync_route_geometry" // [PRIV-A-TRACE-IS-NOT-A-STAT-001]
// [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001] What was last reported to the account doc.
private const val KEY_LAST_PUBLISHED_DETECTION_STATE = "last_published_detection_state"
private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_USE_IMPERIAL_UNITS = "use_imperial_units"
private const val KEY_DEFAULT_MAP_TYPE = "default_map_type"
private const val DEFAULT_MAP_TYPE = "NORMAL"

/**
 * The defaults that belong to the signed-in USER and die with their session — the same list the
 * Android store wipes, key string for key string.
 * [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001]
 *
 * Everything else stays on purpose: onboarding, the GPS disclaimer, the location-permission flag
 * and the ad-measurement consent describe this INSTALL; theme, units, map type and the detection toggles
 * describe this phone.
 */
private val USER_SCOPED_KEYS = listOf(
    KEY_FIRST_STEPS_DONE,
    KEY_FIRST_STEPS_DEFERRED,
    KEY_FIRST_STEPS_DISMISSED,
    // What the outgoing user was told about where the complements' data comes from — knowledge of
    // a person, not of this install. [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]
    KEY_INTRODUCED_COMPLEMENTS,
    KEY_HAS_CONFIRMED_FIRST_PARK,
    KEY_FIRST_PARK_NUDGE_COUNT,
    KEY_LAST_FIRST_PARK_NUDGE_AT,
    KEY_PENDING_NUDGE_CREATED_AT,
    KEY_PENDING_NUDGE_SOURCE,
    KEY_PENDING_NUDGE_VEHICLE_ID,
    KEY_STILL_PARKED_QUESTION,
    KEY_PENDING_PROMPT_SHOWN_AT,
    KEY_PENDING_PROMPT_VEHICLE_NAME,
    KEY_PENDING_RELEASE_SPOT_ID,
    KEY_PENDING_RELEASE_VEHICLE_ID,
    KEY_PENDING_RELEASE_AT,
    // The memory of having reported this account's detection state. Kept across a sign-out it would
    // make the NEXT account's first snapshot look already-sent and stay silent forever.
    // [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001]
    KEY_LAST_PUBLISHED_DETECTION_STATE,
)

/**
 * iOS implementation of [AppPreferences] backed by [NSUserDefaults].
 * Equivalent to Android's SharedPreferences.
 */
class IosAppPreferences : AppPreferences {

    private val userDefaults = NSUserDefaults.standardUserDefaults

    /**
     * [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001] The sign-out wipe —
     * see the interface for which keys belong to the USER and which to this install.
     *
     * The in-memory flows are reset alongside the stored values, and not left to be re-seeded on
     * the next launch: they are what Home is collecting RIGHT NOW, so a stale one would keep
     * serving the previous account's checklist to the screen the sign-out just returned to.
     */
    override fun clearUserScopedState() {
        USER_SCOPED_KEYS.forEach { userDefaults.removeObjectForKey(it) }
        stillParkedFlow.value = null
        firstStepsDoneFlow.value = emptySet()
        firstStepsDeferredFlow.value = emptySet()
        introducedComplementsFlow.value = emptySet()
        firstStepsDismissedFlow.value = false
        pendingParkNudgeFlow.value = null
        pendingPromptWindowFlow.value = null
    }

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

    // iOS has no ad lane today (NoAdMeasurementLane), so nothing asks; the value is still stored
    // like Android's so the contract is the same on both. When iOS gets one, ATT comes first.
    override val adMeasurementConsent: AdMeasurementConsent
        get() = userDefaults.stringForKey(KEY_AD_MEASUREMENT_CONSENT)
            ?.let { stored -> AdMeasurementConsent.entries.firstOrNull { it.name == stored } }
            ?: AdMeasurementConsent.UNASKED

    override fun setAdMeasurementConsent(consent: AdMeasurementConsent) {
        userDefaults.setObject(consent.name, forKey = KEY_AD_MEASUREMENT_CONSENT)
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

    // ── Guided first steps. [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] ──
    // Same single-process StateFlow pattern as [observeAutoDetectParking].

    /** Unknown names are DROPPED, not crashed on — a retired step reads as "not banked". */
    private fun storedFirstSteps(key: String): Set<FirstStep> =
        userDefaults.stringArrayForKey(key)
            .orEmpty()
            .mapNotNull { stored -> FirstStep.entries.firstOrNull { it.name == stored } }
            .toSet()

    private val firstStepsDoneFlow = MutableStateFlow(storedFirstSteps(KEY_FIRST_STEPS_DONE))
    private val firstStepsDeferredFlow = MutableStateFlow(storedFirstSteps(KEY_FIRST_STEPS_DEFERRED))
    private val firstStepsDismissedFlow = MutableStateFlow(userDefaults.boolForKey(KEY_FIRST_STEPS_DISMISSED))

    /** Same forgiving read as the steps: a retired complement is one that was never introduced. */
    private val introducedComplementsFlow = MutableStateFlow(
        userDefaults.stringArrayForKey(KEY_INTRODUCED_COMPLEMENTS)
            .orEmpty()
            .mapNotNull { stored -> NearbyComplement.entries.firstOrNull { it.name == stored } }
            .toSet(),
    )

    override fun observeFirstStepsDone(): Flow<Set<FirstStep>> = firstStepsDoneFlow.asStateFlow()

    override fun setFirstStepsDone(steps: Set<FirstStep>) {
        userDefaults.setObject(steps.map { it.name }, forKey = KEY_FIRST_STEPS_DONE)
        firstStepsDoneFlow.value = steps
    }

    // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] "Not yet" survives a relaunch here too;
    // a deferral the app forgets puts the user back on the same wall.
    override fun observeFirstStepsDeferred(): Flow<Set<FirstStep>> = firstStepsDeferredFlow.asStateFlow()

    override fun setFirstStepsDeferred(steps: Set<FirstStep>) {
        userDefaults.setObject(steps.map { it.name }, forKey = KEY_FIRST_STEPS_DEFERRED)
        firstStepsDeferredFlow.value = steps
    }

    // [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001] One fact, two readers: the lane's provenance line
    // and the checklist's EXPLORE_NEARBY step.
    override fun observeIntroducedComplements(): Flow<Set<NearbyComplement>> =
        introducedComplementsFlow.asStateFlow()

    override fun setIntroducedComplements(complements: Set<NearbyComplement>) {
        userDefaults.setObject(complements.map { it.name }, forKey = KEY_INTRODUCED_COMPLEMENTS)
        introducedComplementsFlow.value = complements
    }

    override fun observeFirstStepsDismissed(): Flow<Boolean> = firstStepsDismissedFlow.asStateFlow()

    override fun setFirstStepsDismissed(dismissed: Boolean) {
        userDefaults.setBool(dismissed, forKey = KEY_FIRST_STEPS_DISMISSED)
        firstStepsDismissedFlow.value = dismissed
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

    private val _dissolvedSharedCars = MutableStateFlow<Set<String>>(emptySet())
    override fun observeDissolvedSharedCars(): Flow<Set<String>> = _dissolvedSharedCars
    override fun addDissolvedSharedCar(vehicleId: String) { _dissolvedSharedCars.value += vehicleId }
    override fun clearDissolvedSharedCar(vehicleId: String) { _dissolvedSharedCars.value -= vehicleId }

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

    // ── Plaza published on a boarding, not yet a departure. [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] ──

    override fun pendingSpotRelease(): PendingSpotRelease? {
        val spotId = userDefaults.stringForKey(KEY_PENDING_RELEASE_SPOT_ID) ?: return null
        return PendingSpotRelease(
            spotId = spotId,
            vehicleId = userDefaults.stringForKey(KEY_PENDING_RELEASE_VEHICLE_ID),
            nominatedAtMs = userDefaults.integerForKey(KEY_PENDING_RELEASE_AT),
            // Ausente = sello escrito por un build anterior a los carriles: lo puede retirar
            // cualquiera. [DET-A-WITHDRAWAL-MUST-NAME-WHAT-IT-TAKES-BACK-001]
            lane = userDefaults.stringForKey(KEY_PENDING_RELEASE_LANE) ?: SpotReleaseLane.LEGACY,
        )
    }

    override fun setPendingSpotRelease(release: PendingSpotRelease) {
        userDefaults.setObject(release.spotId, forKey = KEY_PENDING_RELEASE_SPOT_ID)
        release.vehicleId
            ?.let { userDefaults.setObject(it, forKey = KEY_PENDING_RELEASE_VEHICLE_ID) }
            ?: userDefaults.removeObjectForKey(KEY_PENDING_RELEASE_VEHICLE_ID)
        userDefaults.setInteger(release.nominatedAtMs, forKey = KEY_PENDING_RELEASE_AT)
        userDefaults.setObject(release.lane, forKey = KEY_PENDING_RELEASE_LANE)
    }

    override fun clearPendingSpotRelease() {
        userDefaults.removeObjectForKey(KEY_PENDING_RELEASE_SPOT_ID)
        userDefaults.removeObjectForKey(KEY_PENDING_RELEASE_VEHICLE_ID)
        userDefaults.removeObjectForKey(KEY_PENDING_RELEASE_AT)
        userDefaults.removeObjectForKey(KEY_PENDING_RELEASE_LANE)
    }

    // ── Open "did you park?" question. [DET-ASK-STATE-001] ───────────────────

    private fun storedPendingPromptWindow(): PendingPromptWindow? {
        if (userDefaults.objectForKey(KEY_PENDING_PROMPT_SHOWN_AT) == null) return null
        return PendingPromptWindow(
            shownAtMs = userDefaults.integerForKey(KEY_PENDING_PROMPT_SHOWN_AT),
            vehicleName = userDefaults.stringForKey(KEY_PENDING_PROMPT_VEHICLE_NAME),
            // [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001] Sin sujeto si lo escribió un build viejo.
            vehicleId = userDefaults.stringForKey(KEY_PENDING_PROMPT_VEHICLE_ID),
        )
    }

    private val pendingPromptWindowFlow = MutableStateFlow(storedPendingPromptWindow())

    override fun pendingPromptWindow(): PendingPromptWindow? = pendingPromptWindowFlow.value
    private val stillParkedFlow = MutableStateFlow(
        com.rndeveloper.paparcar.domain.detection.StillParkedQuestion.decode(userDefaults.stringForKey(KEY_STILL_PARKED_QUESTION)),
    )
    override fun stillParkedQuestion() = stillParkedFlow.value
    override fun observeStillParkedQuestion() = stillParkedFlow.asStateFlow()
    override fun setStillParkedQuestion(question: com.rndeveloper.paparcar.domain.detection.StillParkedQuestion?) {
        question?.let { userDefaults.setObject(it.encode(), forKey = KEY_STILL_PARKED_QUESTION) }
            ?: userDefaults.removeObjectForKey(KEY_STILL_PARKED_QUESTION)
        stillParkedFlow.value = question
    }

    override fun observePendingPromptWindow(): Flow<PendingPromptWindow?> = pendingPromptWindowFlow.asStateFlow()

    override fun setPendingPromptWindow(window: PendingPromptWindow) {
        userDefaults.setInteger(window.shownAtMs, forKey = KEY_PENDING_PROMPT_SHOWN_AT)
        window.vehicleName
            ?.let { userDefaults.setObject(it, forKey = KEY_PENDING_PROMPT_VEHICLE_NAME) }
            ?: userDefaults.removeObjectForKey(KEY_PENDING_PROMPT_VEHICLE_NAME)
        window.vehicleId
            ?.let { userDefaults.setObject(it, forKey = KEY_PENDING_PROMPT_VEHICLE_ID) }
            ?: userDefaults.removeObjectForKey(KEY_PENDING_PROMPT_VEHICLE_ID)
        pendingPromptWindowFlow.value = window
    }

    override fun clearPendingPromptWindow() {
        userDefaults.removeObjectForKey(KEY_PENDING_PROMPT_SHOWN_AT)
        userDefaults.removeObjectForKey(KEY_PENDING_PROMPT_VEHICLE_NAME)
        userDefaults.removeObjectForKey(KEY_PENDING_PROMPT_VEHICLE_ID)
        pendingPromptWindowFlow.value = null
    }

    override val notifyParkingDetected: Boolean
        get() = if (userDefaults.objectForKey(KEY_NOTIFY_PARKING_DETECTED) == null) true
                else userDefaults.boolForKey(KEY_NOTIFY_PARKING_DETECTED)

    override fun setNotifyParkingDetected(enabled: Boolean) {
        userDefaults.setBool(enabled, forKey = KEY_NOTIFY_PARKING_DETECTED)
    }

    override val notifyZoneSpots: Boolean
        get() = if (userDefaults.objectForKey(KEY_NOTIFY_ZONE_SPOTS) == null) true
                else userDefaults.boolForKey(KEY_NOTIFY_ZONE_SPOTS)

    override fun setNotifyZoneSpots(enabled: Boolean) {
        userDefaults.setBool(enabled, forKey = KEY_NOTIFY_ZONE_SPOTS)
    }

    /** iOS has no FCM lane yet; a single emission is the honest shape rather than a fake stream. */
    override fun observeNotifyZoneSpots(): Flow<Boolean> = flowOf(notifyZoneSpots)

    override val notifyFuelPrices: Boolean
        get() = if (userDefaults.objectForKey(KEY_NOTIFY_FUEL_PRICES) == null) true
                else userDefaults.boolForKey(KEY_NOTIFY_FUEL_PRICES)

    override fun setNotifyFuelPrices(enabled: Boolean) {
        userDefaults.setBool(enabled, forKey = KEY_NOTIFY_FUEL_PRICES)
    }

    // [MONEY-A-CARD-THAT-NEVER-APPEARS-IS-NEVER-SOLD-001] El interruptor del resumen del mes.
    // Encendido por defecto, como sus tres hermanos: ausente ≠ apagado.
    override val notifyMonthRecap: Boolean
        get() = if (userDefaults.objectForKey(KEY_NOTIFY_MONTH_RECAP) == null) true
                else userDefaults.boolForKey(KEY_NOTIFY_MONTH_RECAP)

    override fun setNotifyMonthRecap(enabled: Boolean) {
        userDefaults.setBool(enabled, forKey = KEY_NOTIFY_MONTH_RECAP)
    }

    // [STATS-PLUS-THE-RECAP-KNOCKS-001] El mes ya contado, como `yyyymm`. Ausente = 0 = nunca.
    override val lastMonthRecapAnnounced: Int?
        get() = userDefaults.integerForKey(KEY_LAST_MONTH_RECAP).toInt().takeIf { it > 0 }

    override fun setLastMonthRecapAnnounced(yearMonth: Int) {
        userDefaults.setInteger(yearMonth.toLong(), forKey = KEY_LAST_MONTH_RECAP)
    }

    // [PRIV-A-TRACE-IS-NOT-A-STAT-001] Absent key = false: the trace never leaves by default.
    override val syncRouteGeometry: Boolean
        get() = userDefaults.boolForKey(KEY_SYNC_ROUTE_GEOMETRY)

    override fun setSyncRouteGeometry(enabled: Boolean) {
        userDefaults.setBool(enabled, forKey = KEY_SYNC_ROUTE_GEOMETRY)
    }

    /**
     * La plaza por la que el usuario dio fe, serializada en UNA cadena igual que en Android.
     * [SPOT-A-SPACE-THE-COMMUNITY-GAVE-YOU-IS-ONE-YOU-VOUCHED-FOR-001]
     *
     * ⚠️ El MISMO formato que el lado Android, y a propósito: es el mismo registro, y dos
     * serializaciones distintas para un dato que se lee en un solo sitio son dos formas de leerlo
     * mal. Una cadena que no se entiende se lee como null — deja la cifra pequeña, no revienta un
     * aparcamiento.
     */
    override val vouchedSpot: VouchedSpot?
        get() = userDefaults.stringForKey(KEY_VOUCHED_SPOT)?.takeIf { it.isNotBlank() }?.let { raw ->
            val parts = raw.split(VOUCH_SEPARATOR)
            if (parts.size != VOUCH_FIELDS) return null
            VouchedSpot(
                spotId = parts[0].ifBlank { return null },
                latitude = parts[1].toDoubleOrNull() ?: return null,
                longitude = parts[2].toDoubleOrNull() ?: return null,
                expiresAtMs = parts[3].toLongOrNull() ?: return null,
            )
        }

    override fun setVouchedSpot(spot: VouchedSpot?) {
        if (spot == null) {
            userDefaults.removeObjectForKey(KEY_VOUCHED_SPOT)
            return
        }
        userDefaults.setObject(
            listOf(spot.spotId, spot.latitude, spot.longitude, spot.expiresAtMs)
                .joinToString(VOUCH_SEPARATOR),
            forKey = KEY_VOUCHED_SPOT,
        )
    }

    // [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001] `stringForKey` already answers null when the
    // key was never written, which is the "never published" the publisher expects.
    override val lastPublishedDetectionState: String?
        get() = userDefaults.stringForKey(KEY_LAST_PUBLISHED_DETECTION_STATE)

    override fun setLastPublishedDetectionState(fingerprint: String) {
        userDefaults.setObject(fingerprint, forKey = KEY_LAST_PUBLISHED_DETECTION_STATE)
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

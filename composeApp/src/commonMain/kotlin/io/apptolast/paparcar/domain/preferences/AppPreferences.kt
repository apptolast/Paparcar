package io.apptolast.paparcar.domain.preferences

import kotlinx.coroutines.flow.Flow

interface AppPreferences {
    val isOnboardingCompleted: Boolean
    fun setOnboardingCompleted()

    val hasSeenGpsAccuracyDisclaimer: Boolean
    fun setGpsAccuracyDisclaimerSeen()

    /** True once we have ever fired the foreground-location request dialog. Lets the permissions
     *  screen tell a genuine first launch (offer the system dialog) apart from a permanently denied /
     *  revoked permission (jump straight to system settings). Android-only concept. [DET-READY-001m] */
    val hasRequestedLocationPermission: Boolean
    fun setLocationPermissionRequested()

    val autoDetectParking: Boolean
    fun setAutoDetectParking(enabled: Boolean)
    /** Reactive view of [autoDetectParking] so Home's detection banner and the Android arming
     *  orchestration update live when the user flips the Settings toggle. [DET-TOGGLE-001] */
    fun observeAutoDetectParking(): Flow<Boolean>

    // ── First-park nudge (cold-start reminder). [DET-TOGGLE-002] ──
    /** How many cold-start nudges have been shown — capped so we never nag. */
    val firstParkNudgeCount: Int
    fun setFirstParkNudgeCount(count: Int)
    /** Epoch millis of the last cold-start nudge (0 = never). Cooldown anchor. */
    val lastFirstParkNudgeAtMillis: Long
    fun setLastFirstParkNudgeAt(millis: Long)
    /** True once the user has had a first parking confirmed — auto-disables the cold-start nudge. */
    val hasConfirmedFirstPark: Boolean
    fun setHasConfirmedFirstPark()

    val notifyParkingDetected: Boolean
    fun setNotifyParkingDetected(enabled: Boolean)

    val notifySpotFreed: Boolean
    fun setNotifySpotFreed(enabled: Boolean)

    val themeMode: ThemeMode
    fun setThemeMode(mode: ThemeMode)

    val useImperialUnits: Boolean
    fun setUseImperialUnits(enabled: Boolean)

    /** Stores map type as a plain string ("TERRAIN" | "SATELLITE" | "HYBRID"). */
    val defaultMapType: String
    fun setDefaultMapType(type: String)

    /** BCP-47 language tag or "auto" to follow the system locale. */
    val selectedLanguage: String
    fun setSelectedLanguage(tag: String)
}

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

package io.apptolast.paparcar.domain.preferences

interface AppPreferences {
    val isOnboardingCompleted: Boolean
    fun setOnboardingCompleted()

    val autoDetectParking: Boolean
    fun setAutoDetectParking(enabled: Boolean)

    val notifyParkingDetected: Boolean
    fun setNotifyParkingDetected(enabled: Boolean)

    val notifySpotFreed: Boolean
    fun setNotifySpotFreed(enabled: Boolean)

    val hasVehicleRegistered: Boolean
    fun setVehicleRegistered()

    val themeMode: ThemeMode
    fun setThemeMode(mode: ThemeMode)

    val useImperialUnits: Boolean
    fun setUseImperialUnits(enabled: Boolean)

    /** Stores map type as a plain string ("NORMAL" | "SATELLITE" | "TERRAIN"). */
    val defaultMapType: String
    fun setDefaultMapType(type: String)
}

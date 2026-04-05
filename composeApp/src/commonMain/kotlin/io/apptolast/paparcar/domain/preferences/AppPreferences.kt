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
}

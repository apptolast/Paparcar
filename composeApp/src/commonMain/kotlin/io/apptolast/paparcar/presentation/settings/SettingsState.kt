package io.apptolast.paparcar.presentation.settings

data class SettingsState(
    val autoDetectParking: Boolean = true,
    val notifyParkingDetected: Boolean = true,
    val notifySpotFreed: Boolean = true,
    val appVersion: String = "1.0.0",
)

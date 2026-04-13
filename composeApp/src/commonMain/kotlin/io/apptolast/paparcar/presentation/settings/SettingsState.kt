package io.apptolast.paparcar.presentation.settings

import io.apptolast.paparcar.domain.model.UserProfile

data class SettingsState(
    val userProfile: UserProfile? = null,
    val autoDetectParking: Boolean = true,
    val notifyParkingDetected: Boolean = true,
    val notifySpotFreed: Boolean = true,
    val appVersion: String = "1.0.0",
)

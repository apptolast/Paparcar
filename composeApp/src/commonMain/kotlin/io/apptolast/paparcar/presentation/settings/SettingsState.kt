package io.apptolast.paparcar.presentation.settings

import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.domain.model.UserProfile

data class SettingsState(
    val userProfile: UserProfile? = null,
    val autoDetectParking: Boolean = true,
    val notifyParkingDetected: Boolean = true,
    val notifySpotFreed: Boolean = true,
    val appVersion: String = "1.0.0",
    val mapType: MapType = MapType.NORMAL,
    val showDeleteAccountConfirmation: Boolean = false,
    val isDeletingAccount: Boolean = false,
)

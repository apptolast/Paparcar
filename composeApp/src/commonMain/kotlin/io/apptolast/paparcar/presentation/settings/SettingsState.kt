package io.apptolast.paparcar.presentation.settings

import io.apptolast.paparcar.domain.model.UserProfile
import io.apptolast.paparcar.domain.permissions.RequiredPermission

data class SettingsState(
    val userProfile: UserProfile? = null,
    val autoDetectParking: Boolean = true,
    val notifyParkingDetected: Boolean = true,
    val notifySpotFreed: Boolean = true,
    val appVersion: String = "1.0.0",
    val showDeleteAccountConfirmation: Boolean = false,
    val isDeletingAccount: Boolean = false,

    // ── Detection & permissions (SETTINGS-REMODEL-001) ───────────────────────
    /** Detection-required permissions not yet granted (CORE + PRODUCER). Empty = all held. */
    val missingDetectionPermissions: Set<RequiredPermission> = emptySet(),
    /** System GPS master toggle. Detection can't work with location services off, even if the
     *  runtime permission is granted — surfaced in the health row alongside missing permissions. */
    val isLocationServicesEnabled: Boolean = true,
    /** Doze exemption (Android-only, DOZE-001). Optional "improve detection" row. */
    val isBatteryOptimizationExempt: Boolean = false,
    /** Active vehicle id — target for the "Configure Bluetooth" deep-link. Null = no vehicle yet. */
    val activeVehicleId: String? = null,
    /** The active vehicle has a paired car-Bluetooth device (`bluetoothDeviceId != null`). */
    val btDeviceConfigured: Boolean = false,
) {
    /** Detection permission health is green only when nothing is missing AND GPS is on. */
    val detectionHealthy: Boolean
        get() = missingDetectionPermissions.isEmpty() && isLocationServicesEnabled
}

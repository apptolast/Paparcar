package com.rndeveloper.paparcar.presentation.settings

import com.rndeveloper.paparcar.appVersion as platformAppVersion
import com.rndeveloper.paparcar.domain.model.DetectionReliabilityLevel
import com.rndeveloper.paparcar.domain.model.UserProfile
import com.rndeveloper.paparcar.domain.permissions.RequiredPermission

data class SettingsState(
    val userProfile: UserProfile? = null,
    val autoDetectParking: Boolean = true,
    val notifyParkingDetected: Boolean = true,
    // Single source: the platform build version. The old "1.0.0" literal was a second,
    // lying default that never matched the APK. [SETTINGS-AUDIT-REMEDIATION-001]
    val appVersion: String = platformAppVersion,
    val showDeleteAccountConfirmation: Boolean = false,
    val isDeletingAccount: Boolean = false,
    /** "Report a problem" consent dialog — says WHAT is shipped before anything leaves the
     *  device. [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001] */
    val showSendDiagnosticsConfirmation: Boolean = false,
    /** Upload in flight; the consent dialog stays up showing progress until it resolves. */
    val isSendingDiagnostics: Boolean = false,
    /**
     * What the user is writing about the failure. Survives dismissing the dialog and a failed
     * upload — it is cleared only once the report is actually sent. Losing four typed sentences to
     * a stray tap outside the dialog, or to the network, is what stops someone from trying again.
     * [SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001]
     */
    val diagnosticsMessage: String = "",

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
    /** How dependable detection is once permitted (single evaluator, [DET-RELIABILITY-001]).
     *  Only REDUCED gets proactive UI; GOOD is the pre-emission default so the health row never
     *  flashes amber before the first report arrives. */
    val detectionReliability: DetectionReliabilityLevel = DetectionReliabilityLevel.GOOD,
) {
    /** Detection permission health is green only when nothing is missing AND GPS is on. */
    val detectionHealthy: Boolean
        get() = missingDetectionPermissions.isEmpty() && isLocationServicesEnabled

    /** Permissions fine but the device environment untrusted — the amber reliability case.
     *  Permission health takes precedence: a hard blocker outranks a reliability warning. */
    val detectionReliabilityReduced: Boolean
        get() = detectionHealthy && detectionReliability == DetectionReliabilityLevel.REDUCED
}

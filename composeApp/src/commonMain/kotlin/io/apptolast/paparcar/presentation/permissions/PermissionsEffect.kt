package io.apptolast.paparcar.presentation.permissions

sealed class PermissionsEffect {
    /** CORE — request foreground location only (the minimum to use the map). [DET-READY-001i] */
    data object RequestStep1 : PermissionsEffect()
    /** PRODUCER sensors — activity recognition + notifications, requested together (footer flow). [DET-READY-001i] */
    data object RequestProducerSensors : PermissionsEffect()
    /** Activity recognition alone — per-card direct grant. [ONB-CARDS-001] */
    data object RequestActivityRecognition : PermissionsEffect()
    /** Notifications alone — per-card direct grant. [ONB-CARDS-001] */
    data object RequestNotifications : PermissionsEffect()
    data object RequestStep2BackgroundLocation : PermissionsEffect()
    data object RequestStepBluetooth : PermissionsEffect()
    data object RequestBatteryOptimizationExemption : PermissionsEffect()
    /** Launch the manufacturer's autostart / background-activity settings screen. */
    data object LaunchOemAutostartSettings : PermissionsEffect()
    /** Launch the OEM battery / power management settings (ColorOS Hans freeze). */
    data object LaunchOemBatterySettings : PermissionsEffect()
    data object OpenAppSettings : PermissionsEffect()
    data object OpenLocationSettings : PermissionsEffect()
    data object NavigateToHome : PermissionsEffect()
}

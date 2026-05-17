package io.apptolast.paparcar.presentation.vehicles

/**
 * Detection strategy currently associated with a vehicle, ready for rendering.
 *
 *  - [Bluetooth]: paired BT device is set; [deviceLabel] = last-5 of MAC or device name.
 *  - [ActivityRecognition]: no BT configured; using the AR coordinator strategy.
 *  - [Disabled]: auto-detection is turned off in Settings.
 */
sealed class VehicleDetectionStatus {
    data class Bluetooth(val deviceLabel: String) : VehicleDetectionStatus()
    data object ActivityRecognition : VehicleDetectionStatus()
    data object Disabled : VehicleDetectionStatus()
}

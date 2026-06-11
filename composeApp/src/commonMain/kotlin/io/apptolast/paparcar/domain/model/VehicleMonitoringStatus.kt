package io.apptolast.paparcar.domain.model

/**
 * Per-vehicle monitoring configuration.
 *
 * Invariant: Bluetooth supersedes Active/Inactive. When [Vehicle.bluetoothDeviceId]
 * is set, the vehicle is monitored via the deterministic BT-disconnect strategy
 * regardless of [Vehicle.isActive]. Active/Inactive only matter for vehicles
 * without a paired BT device (Coordinator strategy fallback).
 *
 *  - [Bluetooth]: BT device paired; tracked via BT disconnect.
 *  - [Active]: no BT; this is the vehicle currently owned by the Coordinator strategy.
 *  - [Inactive]: no BT and not the active Coordinator vehicle; not tracked.
 */
sealed class VehicleMonitoringStatus {
    data class Bluetooth(val deviceId: String) : VehicleMonitoringStatus()
    data object Active : VehicleMonitoringStatus()
    data object Inactive : VehicleMonitoringStatus()
}

fun Vehicle.monitoringStatus(): VehicleMonitoringStatus = when {
    bluetoothDeviceId != null -> VehicleMonitoringStatus.Bluetooth(bluetoothDeviceId)
    isActive                  -> VehicleMonitoringStatus.Active
    else                      -> VehicleMonitoringStatus.Inactive
}

/** Ascending rank for ordering: Bluetooth first, then Active, then Inactive. */
fun VehicleMonitoringStatus.sortRank(): Int = when (this) {
    is VehicleMonitoringStatus.Bluetooth -> 0
    VehicleMonitoringStatus.Active       -> 1
    VehicleMonitoringStatus.Inactive     -> 2
}

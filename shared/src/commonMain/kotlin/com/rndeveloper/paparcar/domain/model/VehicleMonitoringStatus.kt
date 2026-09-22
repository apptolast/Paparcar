package com.rndeveloper.paparcar.domain.model

import com.rndeveloper.paparcar.isBtStrategyRelevant

/**
 * Per-vehicle monitoring configuration.
 *
 * Invariant: Bluetooth supersedes Active/Inactive. When [Vehicle.bluetoothDeviceId]
 * is set, the vehicle is monitored via the deterministic BT-disconnect strategy
 * regardless of [Vehicle.isActive]. Active/Inactive only matter for vehicles
 * without a paired BT device (Coordinator strategy fallback).
 *
 * ⚠️ [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] A stored MAC only means a BT WATCH where the
 * deterministic BT lane exists (`isBtStrategyRelevant`, i.e. Android). On iOS the same MAC is
 * merely the car's identity for the audio-route sensor — the watch there is the Coordinator, so a
 * linked car reads Active/Inactive, not the blue "watched by Bluetooth". The blue tier must not be
 * promised where the lane cannot deliver it.
 *
 *  - [Bluetooth]: BT device paired AND a deterministic BT lane exists; tracked via BT disconnect.
 *  - [Active]: this is the vehicle currently owned by the Coordinator strategy.
 *  - [Inactive]: not the active Coordinator vehicle; not tracked.
 */
sealed class VehicleMonitoringStatus {
    data class Bluetooth(val deviceId: String) : VehicleMonitoringStatus()
    data object Active : VehicleMonitoringStatus()
    data object Inactive : VehicleMonitoringStatus()
}

fun Vehicle.monitoringStatus(): VehicleMonitoringStatus = when {
    bluetoothDeviceId != null && isBtStrategyRelevant ->
        VehicleMonitoringStatus.Bluetooth(bluetoothDeviceId)
    isActive -> VehicleMonitoringStatus.Active
    else     -> VehicleMonitoringStatus.Inactive
}

/** Ascending rank for ordering: Bluetooth first, then Active, then Inactive. */
fun VehicleMonitoringStatus.sortRank(): Int = when (this) {
    is VehicleMonitoringStatus.Bluetooth -> 0
    VehicleMonitoringStatus.Active       -> 1
    VehicleMonitoringStatus.Inactive     -> 2
}

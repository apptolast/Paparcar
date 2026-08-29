package com.rndeveloper.paparcar.domain.bluetooth

import com.rndeveloper.paparcar.domain.model.bluetooth.BluetoothDeviceInfo

/**
 * Platform-agnostic interface for querying Bluetooth state and bonded devices.
 *
 * Uses bonded (already-paired) devices only — no active scanning required.
 * This avoids the BLUETOOTH_SCAN permission and the battery cost of discovery.
 */
interface BluetoothScanner {
    /** Whether the device's Bluetooth adapter is currently enabled. */
    fun isBluetoothEnabled(): Boolean

    /**
     * Returns the list of Bluetooth devices currently bonded (paired) with this phone.
     * Requires BLUETOOTH_CONNECT permission on Android 12+ (API 31+).
     * Returns an empty list if Bluetooth is disabled or the permission is missing.
     */
    fun getBondedDevices(): List<BluetoothDeviceInfo>

    /**
     * True when the phone is currently CONNECTED (ACL link up) to the paired car of at least one of
     * [pairedVehicleIds] — not merely bonded. This is what hands detection to the deterministic
     * Bluetooth pipeline: only a connected car means "I'm driving THIS car", so the probabilistic
     * Coordinator is suppressed. A car that is only paired-and-enabled (you're driving a different,
     * non-BT car) must NOT hijack the strategy — the Coordinator + resident FGS handle that car.
     * [DET-BT-CONNECTED-NOT-PAIRED-001]
     */
    fun isConnectedToPairedCar(pairedVehicleIds: Set<String>): Boolean
}
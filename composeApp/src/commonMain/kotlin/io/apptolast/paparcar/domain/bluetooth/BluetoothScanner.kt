package io.apptolast.paparcar.domain.bluetooth

import io.apptolast.paparcar.domain.model.bluetooth.BluetoothDeviceInfo

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
}
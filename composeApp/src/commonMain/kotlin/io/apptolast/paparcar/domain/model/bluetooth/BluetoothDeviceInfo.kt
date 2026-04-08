package io.apptolast.paparcar.domain.model.bluetooth

/**
 * Represents a Bluetooth device visible/paired to the phone.
 *
 * @param address MAC address — stable unique ID for the device.
 * @param name    Friendly display name (e.g. "BMW Audio", "Volkswagen RCD510").
 * @param type    Classic / LE / Dual, for display purposes only.
 */
data class BluetoothDeviceInfo(
    val address: String,
    val name: String?,
    val type: BluetoothDeviceType,
)
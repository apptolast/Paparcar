package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.bluetooth.BluetoothScanner
import io.apptolast.paparcar.domain.model.bluetooth.BluetoothDeviceInfo

class FakeBluetoothScanner(
    var bluetoothEnabled: Boolean = true,
    var pairedDevices: List<BluetoothDeviceInfo> = emptyList(),
) : BluetoothScanner {
    override fun isBluetoothEnabled(): Boolean = bluetoothEnabled
    override fun getBondedDevices(): List<BluetoothDeviceInfo> = pairedDevices
}

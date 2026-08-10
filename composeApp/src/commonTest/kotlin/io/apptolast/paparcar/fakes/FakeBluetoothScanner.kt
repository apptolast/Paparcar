package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.bluetooth.BluetoothScanner
import io.apptolast.paparcar.domain.model.bluetooth.BluetoothDeviceInfo

class FakeBluetoothScanner(
    var bluetoothEnabled: Boolean = true,
    var pairedDevices: List<BluetoothDeviceInfo> = emptyList(),
    /** vehicleIds whose paired car is currently CONNECTED — drives [isConnectedToPairedCar].
     *  [DET-BT-CONNECTED-NOT-PAIRED-001] */
    var connectedVehicleIds: Set<String> = emptySet(),
) : BluetoothScanner {
    override fun isBluetoothEnabled(): Boolean = bluetoothEnabled
    override fun getBondedDevices(): List<BluetoothDeviceInfo> = pairedDevices
    override fun isConnectedToPairedCar(pairedVehicleIds: Set<String>): Boolean =
        connectedVehicleIds.any { it in pairedVehicleIds }
}

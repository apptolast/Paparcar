package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.bluetooth.BluetoothScanner
import com.rndeveloper.paparcar.domain.bluetooth.BtConnection
import com.rndeveloper.paparcar.domain.model.bluetooth.BluetoothDeviceInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeBluetoothScanner(
    var bluetoothEnabled: Boolean = true,
    var pairedDevices: List<BluetoothDeviceInfo> = emptyList(),
    connectedVehicleIds: Set<String> = emptySet(),
    /** Connect stamps by vehicleId, for the multi-car ranking. Absent = unstamped link. */
    var connectedAtMs: Map<String, Long> = emptyMap(),
) : BluetoothScanner {

    // Backed by a StateFlow so a test can move the ACL edge MID-STREAM: connecting to the car has to
    // push a new readiness on its own, which is the whole point of
    // [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001]. A plain var could only be read when
    // something else happened to emit — exactly the bug.
    private val connected = MutableStateFlow(connectedVehicleIds)

    /** vehicleIds whose paired car is currently CONNECTED. [DET-BT-CONNECTED-NOT-PAIRED-001] */
    var connectedVehicleIds: Set<String>
        get() = connected.value
        set(value) { connected.value = value }

    override fun isBluetoothEnabled(): Boolean = bluetoothEnabled
    override fun getBondedDevices(): List<BluetoothDeviceInfo> = pairedDevices
    override fun isConnectedToPairedCar(pairedVehicleIds: Set<String>): Boolean =
        connectedVehicleIds.any { it in pairedVehicleIds }

    override fun observeConnectedPairedCars(): Flow<List<BtConnection>> =
        connected.map { ids -> ids.map { BtConnection(it, connectedAtMs[it] ?: 0L) } }
}

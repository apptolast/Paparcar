@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.bluetooth

import com.rndeveloper.paparcar.domain.bluetooth.BluetoothScanner
import com.rndeveloper.paparcar.domain.bluetooth.BtConnection
import com.rndeveloper.paparcar.domain.model.bluetooth.BluetoothDeviceInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.darwin.NSObject

/**
 * iOS implementation of [BluetoothScanner].
 *
 * **Major API divergence from Android, by design of Apple's privacy model:**
 * iOS does NOT expose a system-wide list of bonded / paired Bluetooth devices.
 * Apps cannot enumerate Settings → Bluetooth. They can only discover:
 *
 * - BLE peripherals currently advertising specific service UUIDs
 *   (`CBCentralManager.scanForPeripherals(withServices:)`)
 * - Peripherals connected to the system right now, scoped to known services
 *   (`retrieveConnectedPeripheralsWithServices`)
 * - Peripherals the app has previously connected to and saved by UUID
 *   (`retrievePeripheralsWithIdentifiers`)
 *
 * None of these match Android's `getBondedDevices()`. Most cars use Classic
 * Bluetooth (HFP / A2DP), which iOS does not expose to third-party apps at all.
 *
 * Consequence: `getBondedDevices()` returns `emptyList()`. The "pair your car"
 * UX needs a different iOS design — e.g. ask the user for the device name and
 * persist it locally, or detect connection events via `EAAccessoryManager` for
 * MFi-certified accessories. That redesign belongs to the iOS UX phase, not
 * this contract port.
 *
 * `isBluetoothEnabled()` works correctly via [CBCentralManager.state]. Note the
 * manager publishes its initial state asynchronously via the delegate; for a
 * brief window after construction the state reads `Unknown` and this method
 * returns false. The delegate keeps the cached state fresh after that.
 */
class IosBluetoothScanner : BluetoothScanner {

    private val delegate = object : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) = Unit
    }

    private val central = CBCentralManager(delegate, queue = null)

    override fun isBluetoothEnabled(): Boolean = central.state == CBManagerStatePoweredOn

    override fun getBondedDevices(): List<BluetoothDeviceInfo> = emptyList()

    // iOS does not expose Classic (HFP/A2DP) car-connection state to third-party apps; the BT
    // strategy is Android-only for now, so the resolver always falls back to COORDINATOR on iOS.
    // [DET-BT-CONNECTED-NOT-PAIRED-001]
    override fun isConnectedToPairedCar(pairedVehicleIds: Set<String>): Boolean = false

    // Same reason, reactive form: with no car-connection edges to observe, the set is permanently
    // empty and the BT trip lane never lights up on iOS.
    // [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001]
    override fun observeConnectedPairedCars(): Flow<List<BtConnection>> = flowOf(emptyList())
}

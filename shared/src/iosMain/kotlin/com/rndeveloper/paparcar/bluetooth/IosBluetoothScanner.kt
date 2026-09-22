package com.rndeveloper.paparcar.bluetooth

import com.rndeveloper.paparcar.domain.bluetooth.BluetoothScanner
import com.rndeveloper.paparcar.domain.bluetooth.BtConnection
import com.rndeveloper.paparcar.domain.model.bluetooth.BluetoothDeviceInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * iOS implementation of [BluetoothScanner] — an honest full stub.
 *
 * **The BT detection lane cannot exist on iOS** (analysis with sources:
 * `docs/backlog/ios-a-bt-lane-needs-a-real-signal-001.md`):
 *
 * - Most cars use Classic Bluetooth (HFP / A2DP), which iOS does not expose to third-party
 *   apps at all — no connect/disconnect events, no paired-device list, no wake-from-suspended.
 * - CoreBluetooth only sees GATT-capable peripherals (BLE, or dual-mode via transport
 *   bridging); a car head unit is neither, and `CBPeer.identifier` is a random per-phone UUID,
 *   useless against the MAC we store in `Vehicle.bluetoothDeviceId`.
 * - `ExternalAccessory` requires an MFi accessory that declares our protocol string.
 * - `AVAudioSession` route info can *identify* the car's BT port while the process is alive,
 *   but never delivers the edge when suspended — evidence, not a trigger.
 *
 * Consequence: nothing on iOS may consume the BT adapter state, so this class must not touch
 * CoreBluetooth either. The previous revision instantiated a `CBCentralManager` just to answer
 * [isBluetoothEnabled] — and instantiating one is precisely what TRIGGERS the system Bluetooth
 * permission dialog, i.e. the app asked the user for a permission that unlocked nothing. Every
 * answer below is the constant the platform makes true:
 *
 * - [isBluetoothEnabled] `false`: only read inside `ParkingStrategyResolver.strategyFor`'s BT
 *   conjunction (dead on iOS via [isConnectedToPairedCar]) and `BluetoothConfigViewModel`
 *   (screen unreachable on iOS — its Settings entry hides behind `isBtStrategyRelevant`).
 * - [getBondedDevices] empty: iOS cannot enumerate Settings → Bluetooth.
 * - [isConnectedToPairedCar] `false` / [observeConnectedPairedCars] empty: no Classic BT
 *   connection edges exist for third-party apps. [DET-BT-CONNECTED-NOT-PAIRED-001]
 *
 * [IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001]
 */
class IosBluetoothScanner : BluetoothScanner {

    override fun isBluetoothEnabled(): Boolean = false

    override fun getBondedDevices(): List<BluetoothDeviceInfo> = emptyList()

    override fun isConnectedToPairedCar(pairedVehicleIds: Set<String>): Boolean = false

    // Same reason, reactive form: with no car-connection edges to observe, the set is permanently
    // empty and the BT trip lane never lights up on iOS.
    // [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001]
    override fun observeConnectedPairedCars(): Flow<List<BtConnection>> = flowOf(emptyList())
}

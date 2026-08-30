package com.rndeveloper.paparcar.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import com.rndeveloper.paparcar.domain.bluetooth.BluetoothScanner
import com.rndeveloper.paparcar.domain.bluetooth.BtConnection
import com.rndeveloper.paparcar.domain.model.bluetooth.BluetoothDeviceInfo
import com.rndeveloper.paparcar.domain.model.bluetooth.BluetoothDeviceType
import kotlinx.coroutines.flow.Flow

/**
 * Android implementation of [BluetoothScanner].
 *
 * Uses [BluetoothAdapter.getBondedDevices] — no active BLE scan needed.
 * BLUETOOTH_CONNECT permission (API 31+) is required to read device names
 * and MAC addresses; if missing the method returns an empty list gracefully.
 */
class AndroidBluetoothScanner(private val context: Context) : BluetoothScanner {

    private val adapter by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    override fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    override fun getBondedDevices(): List<BluetoothDeviceInfo> {
        if (!isBluetoothEnabled()) return emptyList()
        return runCatching {
            adapter?.bondedDevices.orEmpty().mapNotNull { device ->
                runCatching {
                    BluetoothDeviceInfo(
                        address = device.address,
                        name = device.name,
                        type = device.type.toBluetoothDeviceType(),
                    )
                }.getOrNull()
            }
        }.getOrElse { emptyList() }
    }

    /**
     * [DET-BT-CONNECTED-NOT-PAIRED-001] Connection ground truth from [BtConnectionStore], which the
     * manifest ACL receiver keeps current on every connect/disconnect edge (across process kills, via
     * SharedPreferences — a live profile-proxy poll is async and can't answer synchronously here).
     */
    override fun isConnectedToPairedCar(pairedVehicleIds: Set<String>): Boolean {
        if (pairedVehicleIds.isEmpty()) return false
        return BtConnectionStore.connectedVehicleIds(context).any { it in pairedVehicleIds }
    }

    /**
     * [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001] Same ground truth as above, pushed
     * instead of polled. The store is SharedPreferences (written by the manifest ACL receiver so it
     * survives OEM process kills), so its own change listener IS the connect/disconnect edge — no
     * second bookkeeping to keep in sync, and no BluetoothProfile proxy to hold open.
     */
    override fun observeConnectedPairedCars(): Flow<List<BtConnection>> =
        BtConnectionStore.observeConnected(context)

    private fun Int.toBluetoothDeviceType(): BluetoothDeviceType = when (this) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> BluetoothDeviceType.CLASSIC
        BluetoothDevice.DEVICE_TYPE_LE -> BluetoothDeviceType.LE
        BluetoothDevice.DEVICE_TYPE_DUAL -> BluetoothDeviceType.DUAL
        else -> BluetoothDeviceType.UNKNOWN
    }
}

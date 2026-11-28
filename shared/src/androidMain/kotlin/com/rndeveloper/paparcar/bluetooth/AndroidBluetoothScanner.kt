package com.rndeveloper.paparcar.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.domain.bluetooth.BluetoothScanner
import com.rndeveloper.paparcar.domain.bluetooth.BtConnection
import com.rndeveloper.paparcar.domain.model.bluetooth.BluetoothDeviceInfo
import com.rndeveloper.paparcar.domain.model.bluetooth.BluetoothDeviceType
import kotlinx.coroutines.flow.Flow

/**
 * Android implementation of [BluetoothScanner].
 *
 * Uses [BluetoothAdapter.getBondedDevices] — no active BLE scan needed.
 * Requires BLUETOOTH through API 30, BLUETOOTH_CONNECT from API 31.
 * Missing or revoked access yields false/empty results at this platform boundary.
 */
class AndroidBluetoothScanner(private val context: Context) : BluetoothScanner {

    private val adapter by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    @SuppressLint("MissingPermission")
    override fun isBluetoothEnabled(): Boolean {
        if (!hasBluetoothPermission()) return false
        return try {
            adapter?.isEnabled == true
        } catch (e: SecurityException) {
            PaparcarLogger.w(TAG, "Bluetooth state access denied")
            false
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

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
        }.getOrElse {
            PaparcarLogger.w(TAG, "Bonded Bluetooth devices unavailable")
            emptyList()
        }
    }

    /**
     * [DET-BT-CONNECTED-NOT-PAIRED-001] Connection ground truth from [BtConnectionStore], which the
     * manifest ACL receiver keeps current on every connect/disconnect edge (across process kills, via
     * SharedPreferences — a live profile-proxy poll is async and can't answer synchronously here).
     */
    override fun isConnectedToPairedCar(pairedVehicleIds: Set<String>): Boolean {
        if (pairedVehicleIds.isEmpty()) return false
        return BtConnectionStore.connectedVehicleIds(context).any {
            it in pairedVehicleIds && !BtConnectionStore.isTripFinished(context, it)
        }
    }

    /**
     * [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001] Same ground truth as above, pushed
     * instead of polled. The store is SharedPreferences (written by the manifest ACL receiver so it
     * survives OEM process kills), so its own change listener IS the connect/disconnect edge — no
     * second bookkeeping to keep in sync, and no BluetoothProfile proxy to hold open.
     */
    override fun observeConnectedPairedCars(): Flow<List<BtConnection>> =
        BtConnectionStore.observeConnected(context)

    private companion object {
        const val TAG = "AndroidBluetoothScanner"
    }

    private fun Int.toBluetoothDeviceType(): BluetoothDeviceType = when (this) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> BluetoothDeviceType.CLASSIC
        BluetoothDevice.DEVICE_TYPE_LE -> BluetoothDeviceType.LE
        BluetoothDevice.DEVICE_TYPE_DUAL -> BluetoothDeviceType.DUAL
        else -> BluetoothDeviceType.UNKNOWN
    }
}

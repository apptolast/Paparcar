package io.apptolast.paparcar.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import io.apptolast.paparcar.domain.bluetooth.BluetoothScanner
import io.apptolast.paparcar.domain.model.bluetooth.BluetoothDeviceInfo
import io.apptolast.paparcar.domain.model.bluetooth.BluetoothDeviceType

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

    private fun Int.toBluetoothDeviceType(): BluetoothDeviceType = when (this) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> BluetoothDeviceType.CLASSIC
        BluetoothDevice.DEVICE_TYPE_LE -> BluetoothDeviceType.LE
        BluetoothDevice.DEVICE_TYPE_DUAL -> BluetoothDeviceType.DUAL
        else -> BluetoothDeviceType.UNKNOWN
    }
}

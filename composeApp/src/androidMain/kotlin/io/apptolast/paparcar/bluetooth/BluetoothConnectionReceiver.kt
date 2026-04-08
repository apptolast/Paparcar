package io.apptolast.paparcar.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * System BroadcastReceiver that translates ACL Bluetooth events into parking-detection signals.
 *
 * Only reacts to the default vehicle's paired device address — all other devices are ignored.
 *
 * - [BluetoothDevice.ACTION_ACL_DISCONNECTED] → [BluetoothParkingDetector.onCarDisconnected]
 * - [BluetoothDevice.ACTION_ACL_CONNECTED]    → [BluetoothParkingDetector.onCarConnected]
 *
 * Registered in AndroidManifest with the BLUETOOTH_CONNECT permission guard so that the
 * system only delivers events to this receiver when the app holds the permission.
 */
class BluetoothConnectionReceiver : BroadcastReceiver(), KoinComponent {

    private val vehicleRepository: VehicleRepository by inject()
    private val detector: BluetoothParkingDetector by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED
            && action != BluetoothDevice.ACTION_ACL_DISCONNECTED
        ) return

        @Suppress("DEPRECATION")
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            ?: return
        val deviceAddress = runCatching { device.address }.getOrNull() ?: return

        val pending = goAsync()
        scope.launch {
            try {
                val defaultVehicle = vehicleRepository.observeDefaultVehicle().first()
                if (defaultVehicle?.bluetoothDeviceId == null) {
                    PaparcarLogger.d(TAG, "No BT pairing configured for default vehicle — ignoring event")
                    return@launch
                }
                if (!deviceAddress.equals(defaultVehicle.bluetoothDeviceId, ignoreCase = true)) {
                    PaparcarLogger.d(TAG, "Event from $deviceAddress — not the car device, ignoring")
                    return@launch
                }

                when (action) {
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> detector.onCarDisconnected(deviceAddress)
                    BluetoothDevice.ACTION_ACL_CONNECTED -> detector.onCarConnected(deviceAddress)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BluetoothConnectionReceiver"
    }
}

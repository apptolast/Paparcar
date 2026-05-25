package io.apptolast.paparcar.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * System BroadcastReceiver that translates ACL Bluetooth events into parking-detection signals.
 *
 * Reacts when the ACL event's device matches **any** of the user's vehicles' paired addresses
 * (resolved via [VehicleRepository.getVehicleByBluetoothDeviceId]). Events from unrelated
 * devices are ignored. The resolved vehicleId is forwarded so detection attaches the session
 * to the correct vehicle even when it is not the default one.
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

        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        } ?: return
        val deviceAddress = runCatching { device.address }.getOrNull() ?: return

        val pending = goAsync()
        scope.launch {
            try {
                val pairedVehicle = vehicleRepository.getVehicleByBluetoothDeviceId(deviceAddress)
                if (pairedVehicle == null) {
                    PaparcarLogger.d(TAG, "Event from $deviceAddress — no vehicle paired with this device, ignoring")
                    return@launch
                }

                when (action) {
                    BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                        detector.onCarDisconnected(deviceAddress, pairedVehicle.id)
                    BluetoothDevice.ACTION_ACL_CONNECTED ->
                        detector.onCarConnected(deviceAddress)
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

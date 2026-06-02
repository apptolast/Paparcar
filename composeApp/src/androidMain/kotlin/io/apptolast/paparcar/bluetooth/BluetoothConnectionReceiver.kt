package io.apptolast.paparcar.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * System BroadcastReceiver that translates ACL Bluetooth events into
 * [BluetoothDetectionService] start commands.
 *
 * **Minimum-work pattern.** This receiver does exactly two things and nothing more:
 * 1. Resolve the vehicleId paired to the event's BT device address (fast Room read).
 * 2. Delegate to [BluetoothDetectionService] — all long-running detection work lives there.
 *
 * Events from devices not paired to any user vehicle are ignored before any Service is started,
 * preventing spurious foreground-service launches from unrelated BT peripherals.
 *
 * - [BluetoothDevice.ACTION_ACL_DISCONNECTED] → `startForegroundService(ACTION_BT_DISCONNECTED)`
 * - [BluetoothDevice.ACTION_ACL_CONNECTED]    → `startService(ACTION_BT_CONNECTED)` (instant work,
 *   no foreground needed — the Service cancels the pending job and stops itself immediately)
 *
 * Registered in AndroidManifest with `exported=false` and a BLUETOOTH_CONNECT permission guard
 * so that the system only delivers events when the app holds the permission.
 */
class BluetoothConnectionReceiver : BroadcastReceiver(), KoinComponent {

    private val vehicleRepository: VehicleRepository by inject()

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

        val eventLabel = if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) "DISCONNECTED" else "CONNECTED"
        PaparcarLogger.d(TAG, "▶ BT $eventLabel device=$deviceAddress")

        val pending = goAsync()

        scope.launch {
            try {
                val pairedVehicle = vehicleRepository.getVehicleByBluetoothDeviceId(deviceAddress)
                if (pairedVehicle == null) {
                    PaparcarLogger.d(TAG, "  no vehicle paired with $deviceAddress — ignoring")
                    return@launch
                }
                PaparcarLogger.d(TAG, "  matched vehicle=${pairedVehicle.id} — starting BluetoothDetectionService ($eventLabel)")

                when (action) {
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val serviceIntent = Intent(context, BluetoothDetectionService::class.java).apply {
                            this.action = BluetoothDetectionService.ACTION_BT_DISCONNECTED
                            putExtra(BluetoothDetectionService.EXTRA_DEVICE_ADDRESS, deviceAddress)
                            putExtra(BluetoothDetectionService.EXTRA_VEHICLE_ID, pairedVehicle.id)
                        }
                        ContextCompat.startForegroundService(context, serviceIntent)
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        // startService (not foreground) — work is instant (cancel + stopSelf).
                        // If the detection Service is already running as FGS, onStartCommand
                        // is called on the existing instance; no 5-second constraint applies.
                        val serviceIntent = Intent(context, BluetoothDetectionService::class.java).apply {
                            this.action = BluetoothDetectionService.ACTION_BT_CONNECTED
                            putExtra(BluetoothDetectionService.EXTRA_DEVICE_ADDRESS, deviceAddress)
                        }
                        context.startService(serviceIntent)
                    }
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

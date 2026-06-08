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
import kotlinx.coroutines.cancel
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
 *
 * **Refactor 2026-06-08 [BT-BUG-102 + BT-BUG-106]:**
 *  - Per-delivery [CoroutineScope] instead of an instance field. The previous design
 *    created one scope per delivery on a property that was never cancelled — each
 *    onReceive leaked a SupervisorJob into the JVM. `pending.finish()` releases the
 *    ANR window but does not cancel the scope; explicit cancel after the work block
 *    closes the leak.
 *  - `device.address` access now logs SecurityException at warn level instead of being
 *    swallowed silently — BLUETOOTH_CONNECT revocation produces a visible trace.
 */
class BluetoothConnectionReceiver : BroadcastReceiver(), KoinComponent {

    private val vehicleRepository: VehicleRepository by inject()

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
        val deviceAddress = runCatching { device.address }
            .onFailure { e -> PaparcarLogger.w(TAG, "device.address threw — BLUETOOTH_CONNECT likely revoked: ${e.message}") }
            .getOrNull() ?: return

        val eventLabel = if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) "DISCONNECTED" else "CONNECTED"
        PaparcarLogger.d(TAG, "▶ BT $eventLabel device=$deviceAddress")

        val pending = goAsync()
        // [FIX BT-BUG-102] Scope is local to this delivery. The previous design held a
        // CoroutineScope on the receiver instance — each onReceive launched into it but
        // nothing ever cancelled the SupervisorJob, so completed jobs accumulated as
        // garbage children of a parent that lived forever. Local scope + explicit cancel
        // in the finally closes the leak.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            } catch (e: Throwable) {
                // Any unexpected throwable: don't let it escape the receiver (would crash the
                // app under goAsync). Log + continue to the finally block.
                PaparcarLogger.e(TAG, "  ✗ delivery handler threw", e)
            } finally {
                pending.finish()
                scope.cancel() // [FIX BT-BUG-102] tear down the per-delivery scope.
            }
        }
    }

    private companion object {
        const val TAG = "BluetoothConnectionReceiver"
    }
}

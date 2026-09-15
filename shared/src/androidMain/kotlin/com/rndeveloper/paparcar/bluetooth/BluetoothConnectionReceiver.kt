package com.rndeveloper.paparcar.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.rndeveloper.paparcar.detection.service.CoordinatorDetectionService
import com.rndeveloper.paparcar.domain.location.LocationDataSource
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * System BroadcastReceiver that translates ACL Bluetooth events into sentinel
 * ([com.rndeveloper.paparcar.detection.service.CoordinatorDetectionService]) start commands.
 * [DET-ONE-SERVICE-TWO-LANES-001]
 *
 * **Minimum-work pattern.** This receiver does exactly three things and nothing more:
 * 1. Resolve the vehicleId paired to the event's BT device address (fast Room read).
 * 2. Stamp the durable facts a broadcast is the only witness of (connect instant + origin fix).
 * 3. Forward ONE intent per edge to the sentinel's serialized intake — arbitration, the trip
 *    recorder, the disconnect hunt and the departure watch all resolve THERE.
 *
 * Events from devices not paired to any user vehicle are ignored before any Service is started,
 * preventing spurious foreground-service launches from unrelated BT peripherals.
 *
 * Both edges are `startForegroundService` — a DISCONNECTED runs the GPS hunt and a CONNECTED
 * always starts the trip recorder [TRIP-THE-TRACKER-RIDES-THE-SENTINEL-001] — and the sentinel
 * promotes for every action, so the 5-second promise is always kept.
 *
 * Registered in AndroidManifest with `exported="true"` and a BLUETOOTH_CONNECT permission guard.
 * The `true` is LOAD-BEARING — do not "fix" it to false: ACL broadcasts are sent by the
 * Bluetooth stack process (uid `bluetooth`, not `system`), which cannot deliver to non-exported
 * components of another app. `exported="false"` leaves this receiver permanently deaf and the
 * whole deterministic BT strategy dead. Exporting is safe: both ACL actions are protected
 * broadcasts (only the system/BT stack may send them) and the permission guard restricts
 * senders to BLUETOOTH_CONNECT holders. See [DET-BT-RECEIVER-EXPORT-001] in AndroidManifest.xml.
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
    private val appPreferences: AppPreferences by inject()

    // [DET-BT-A-RIDE-IS-MEASURED-NOT-TIMED-001] Origin of the ride, read passively at CONNECT.
    private val locationDataSource: LocationDataSource by inject()

    // [DET-A-BT-CONNECT-IS-A-DEPARTURE-001] Whether the car that just connected still holds a
    // plaza — the routing fact the sentinel's CONNECT handler wants ready-made.
    private val userParkingRepository: UserParkingRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED
            && action != BluetoothDevice.ACTION_ACL_DISCONNECTED
        ) return

        // Master gate: auto-detection switched OFF in Settings → ignore BT connect/disconnect, the
        // deterministic detection path must not arm either. Independent of permissions. [DET-TOGGLE-001]
        if (!appPreferences.autoDetectParking) {
            PaparcarLogger.d(TAG, "  ⊘ auto-detection OFF in Settings — ignoring BT event")
            return
        }

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
                PaparcarLogger.d(TAG, "  matched vehicle=${pairedVehicle.id} — forwarding $eventLabel to the sentinel")

                // [DET-ONE-SERVICE-TWO-LANES-001] The receiver computes NOTHING about detection any
                // more: arbitration [DET-TIERS-001], the trip recorder, the hunt and the departure
                // watch all resolve inside the sentinel's serialized intake. This receiver keeps
                // only what must happen at broadcast time — the durable stamps and the routing
                // facts — and forwards ONE intent per edge.
                when (action) {
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        // [DET-BT-CONNECTED-NOT-PAIRED-001] Car no longer connected → clear the live
                        // connection state the strategy resolver reads, so detection falls back to the
                        // Coordinator for the next (possibly non-BT) car.
                        BtConnectionStore.markDisconnected(context, pairedVehicle.id)
                        // ONE intent: the sentinel stops the recorder and runs the hunt, in order.
                        // Foreground start — the hunt holds GPS for minutes. [DET-ONE-SERVICE-TWO-LANES-001]
                        sendBtEdgeToSentinel(
                            context,
                            action = CoordinatorDetectionService.ACTION_BT_DISCONNECTED,
                            deviceAddress = deviceAddress,
                            vehicleId = pairedVehicle.id,
                        )
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        // [DET-BT-IDENTITY-GATE-001] Stamp that the user got into THIS car — the
                        // identity proof the parked-session safety net reads before it reconstructs
                        // a departure for a BT-paired vehicle. Disk-backed (survives the process
                        // kills the net wakes across); keyed by vehicleId, the join the net has.
                        //
                        // [DET-BT-A-RIDE-IS-MEASURED-NOT-TIMED-001] …and stamp WHERE that happened,
                        // so the disconnect can ask whether the car actually went anywhere. A cached
                        // last-known read: it requests no updates, adds no fix to the fused stream
                        // (so it cannot provoke a geofence EXIT) and costs no foreground service —
                        // the same passive read DET-AR-REARM-001 established. Bounded and swallowed:
                        // a receiver must not stall on the location stack, and a missing origin is a
                        // measurement we will report as missing, not a reason to drop the stamp.
                        val originFix = withTimeoutOrNull(ORIGIN_FIX_TIMEOUT_MS) {
                            runCatching { locationDataSource.getLastKnownLocation() }.getOrNull()
                        }
                        if (originFix == null) {
                            PaparcarLogger.w(TAG, "  ⚠ no cached fix at connect — this ride will not be measurable")
                        }
                        BtConnectionStore.recordConnected(
                            context,
                            pairedVehicle.id,
                            System.currentTimeMillis(),
                            originFix,
                        )
                        // [DET-BT-CONNECTED-NOT-PAIRED-001] Connected to THIS car → BLUETOOTH owns
                        // detection now; the resolver reads this live state to suppress the Coordinator.
                        BtConnectionStore.markConnected(context, pairedVehicle.id)

                        // [DET-A-BT-CONNECT-IS-A-DEPARTURE-001] Does this car still hold a plaza?
                        // Still asked HERE (the receiver already resolves the vehicle from a Room
                        // read) because it is a ROUTING fact the handler wants ready-made — but it
                        // no longer decides how the service starts: since the fusion the connect
                        // always starts the trip recorder, so the start is always foreground.
                        // A failed read is NOT "no session": that reading would silently give up
                        // the watch on a Room hiccup, so it errs toward watching. The sentinel
                        // re-checks and stands down cleanly if the plaza is really gone.
                        val holdsAPlaza = runCatching {
                            userParkingRepository.getActiveSessionByVehicle(pairedVehicle.id) != null
                        }.getOrElse {
                            PaparcarLogger.w(TAG, "  ⚠ parked-session lookup failed (${it.message}) — assuming a plaza is held")
                            true
                        }
                        // ONE intent: the sentinel arbitrates, starts the trip RECORDER (always —
                        // the ride this connect begins must end with a MEASURED route on its park)
                        // and opens the departure watch when a plaza is held. Always a foreground
                        // start: recording is long-running, ACL broadcasts are exempt from the
                        // background FGS-start restriction, and the garage-radio case is bounded by
                        // the recorder's own null-trip shutoff (BtTripShutoff) — never by this
                        // receiver guessing. [DET-ONE-SERVICE-TWO-LANES-001]
                        sendBtEdgeToSentinel(
                            context,
                            action = CoordinatorDetectionService.ACTION_BT_CONNECTED,
                            deviceAddress = deviceAddress,
                            vehicleId = pairedVehicle.id,
                            watchDeparture = holdsAPlaza,
                        )
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

    /** [DET-ONE-SERVICE-TWO-LANES-001] The one way a BT edge reaches the sentinel — both branches
     *  build their intent HERE, so they cannot drift in style or destination again. */
    private fun sendBtEdgeToSentinel(
        context: Context,
        action: String,
        deviceAddress: String,
        vehicleId: String,
        watchDeparture: Boolean? = null,
    ) {
        val intent = Intent(context, CoordinatorDetectionService::class.java).apply {
            this.action = action
            putExtra(CoordinatorDetectionService.EXTRA_BT_DEVICE_ADDRESS, deviceAddress)
            putExtra(CoordinatorDetectionService.EXTRA_BT_VEHICLE_ID, vehicleId)
            watchDeparture?.let { putExtra(CoordinatorDetectionService.EXTRA_BT_WATCH_DEPARTURE, it) }
        }
        ContextCompat.startForegroundService(context, intent)
    }

    private companion object {
        /** PARKDIAG prefix: FileAntilog only persists PARKDIAG-tagged lines — without it the whole
         *  BT path was invisible in field captures, like SIGMOTION before 2026-07-07. */
        const val TAG = "PARKDIAG/BTReceiver"

        /** [DET-BT-A-RIDE-IS-MEASURED-NOT-TIMED-001] Ceiling on the cached-fix read at CONNECT. The
         *  read is a memory hit in practice; the bound exists because this runs inside a
         *  BroadcastReceiver's `goAsync` window, where a hung location stack costs an ANR. */
        const val ORIGIN_FIX_TIMEOUT_MS = 2_000L
    }
}

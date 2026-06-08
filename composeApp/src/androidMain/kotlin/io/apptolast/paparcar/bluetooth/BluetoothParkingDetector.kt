package io.apptolast.paparcar.bluetooth

import android.location.Location
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Deterministic parking detector driven by Bluetooth connection events.
 *
 * Stateless: owns no [kotlinx.coroutines.CoroutineScope] and no [kotlinx.coroutines.Job].
 * The caller ([BluetoothDetectionService]) runs [detectParking] inside its own
 * [androidx.lifecycle.lifecycleScope] and cancels the coroutine when BT reconnects —
 * cooperative cancellation via [delay] and `first` handles the abort cleanly.
 *
 * **Disconnect → park flow:**
 * 1. Car BT disconnects.
 * 2. [BT_DISCONNECT_DEBOUNCE_MS] grace window — if BT reconnects the caller cancels
 *    this coroutine before [delay] returns (BT-005: oscillation / traffic-light guard).
 * 3. Sample GPS until a fix with accuracy ≤ [GPS_ACCURACY_THRESHOLD_M] is obtained,
 *    or [GPS_SAMPLE_TIMEOUT_MS] elapses (BT-005: GPS drift guard).
 * 4. Record the fix as the candidate parking location.
 * 5. Watch subsequent GPS updates — when the user has moved ≥ [DISTANCE_THRESHOLD_M]
 *    from the fix, the spot is confirmed with [ConfirmParkingUseCase].
 * 6. **[REFACTOR-300] Unified post-save notification** — after a successful save,
 *    [AppNotificationManager.showParkingSavedConfirm] posts the "Vehículo aparcado ·
 *    Cancelar" card so the user can revert if BT identified the event incorrectly
 *    (e.g. user was a passenger, or a neighbour's car was bonded by mistake).
 *
 * @param vehicleId  id of the vehicle whose paired BT device disconnected. The caller
 *   ([BluetoothConnectionReceiver]) resolves this from the device address before
 *   launching [detectParking] so the session attaches to the *actually parked* vehicle
 *   even in multi-vehicle BT configurations.
 */
class BluetoothParkingDetector(
    private val observeLocation: ObserveAdaptiveLocationUseCase,
    private val confirmParking: ConfirmParkingUseCase,
    // [REFACTOR-300] post-save notif owned here so the user can revert a BT auto-confirm.
    private val notificationPort: AppNotificationManager,
    private val vehicleRepository: VehicleRepository,
) {

    suspend fun detectParking(deviceAddress: String, vehicleId: String) {
        PaparcarLogger.d(TAG, "BT disconnected ($deviceAddress, vehicle=$vehicleId) — debouncing for reconnect check")

        // BT-005: wait before acting — brief stops and BT oscillation fire disconnect
        // events too. If BT reconnects, the Service cancels this coroutine here.
        delay(BT_DISCONNECT_DEBOUNCE_MS.milliseconds)

        PaparcarLogger.d(TAG, "Debounce passed — sampling GPS for parking fix")

        // BT-005: sample GPS until accuracy is good enough or timeout fires
        val parkingFix = withTimeoutOrNull(GPS_SAMPLE_TIMEOUT_MS.milliseconds) {
            observeLocation().first { it.accuracy <= GPS_ACCURACY_THRESHOLD_M }
        }

        if (parkingFix == null) {
            PaparcarLogger.w(TAG, "GPS fix timed out — skipping BT parking confirmation")
            return
        }

        PaparcarLogger.d(TAG, "Got parking fix at (${parkingFix.latitude}, ${parkingFix.longitude}), accuracy=${parkingFix.accuracy}m")

        // Watch subsequent GPS updates for the distance departure check
        observeLocation().first { current ->
            val dist = FloatArray(DISTANCE_RESULT_SIZE)
            Location.distanceBetween(
                parkingFix.latitude, parkingFix.longitude,
                current.latitude, current.longitude,
                dist,
            )
            dist[0] >= DISTANCE_THRESHOLD_M
        }

        PaparcarLogger.i(TAG, "User moved ≥${DISTANCE_THRESHOLD_M}m — confirming BT parking for vehicle=$vehicleId")
        // [REFACTOR-300] silent=true: detector owns the post-save notification via
        // showParkingSavedConfirm below; we DO NOT want ConfirmParkingUseCase to fire
        // the legacy showParkingSaved (would be the double-notif we just eliminated).
        confirmParking(parkingFix, PARKING_DETECTION_RELIABILITY, vehicleId = vehicleId, silent = true)
            .onSuccess { saved ->
                val vehicleName = runCatching {
                    vehicleRepository.observeActiveVehicle().firstOrNull()
                        ?.let { it.displayName(fallback = "").takeIf { n -> n.isNotBlank() } }
                }.getOrNull()
                notificationPort.showParkingSavedConfirm(
                    parkingId = saved.id,
                    vehicleName = vehicleName,
                    latitude = saved.location.latitude,
                    longitude = saved.location.longitude,
                )
            }
            .onFailure { e -> PaparcarLogger.e(TAG, "Failed to confirm parking", e) }
    }

    private companion object {
        const val TAG = "BluetoothParkingDetector"

        /** BT-005: Grace window before acting on disconnect (brief stop / oscillation debounce). */
        const val BT_DISCONNECT_DEBOUNCE_MS = 30_000L

        /** BT-005: Reject GPS fixes with accuracy worse than this (GPS drift guard). */
        const val GPS_ACCURACY_THRESHOLD_M = 50f

        /** BT-005: Give up waiting for a good GPS fix after this duration. */
        const val GPS_SAMPLE_TIMEOUT_MS = 60_000L

        /** Distance the user must walk from the fix before parking is auto-confirmed. */
        const val DISTANCE_THRESHOLD_M = 30f

        /**
         * Reliability reported to ConfirmParkingUseCase. BT is deterministic — a real
         * disconnect + 30 m walk is an unambiguous signal — so we use a value higher
         * than the activity-recognition-based vehicleExit path (0.90).
         *
         * **TODO-BT-CONFIG-P2:** move to [ParkingDetectionConfig.reliabilityBluetooth]
         * for parity with the other reliability constants. Left as a literal here for
         * surface-minimal v1.
         */
        const val PARKING_DETECTION_RELIABILITY = 0.95f

        /** Required size of the FloatArray passed to [Location.distanceBetween]. */
        const val DISTANCE_RESULT_SIZE = 1
    }
}

package io.apptolast.paparcar.bluetooth

import android.location.Location
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Deterministic parking detector driven by Bluetooth connection events.
 *
 * Stateless: owns no [kotlinx.coroutines.CoroutineScope] and no [kotlinx.coroutines.Job].
 * The caller ([BluetoothDetectionService]) runs [detectParking] inside its own
 * [androidx.lifecycle.lifecycleScope] and cancels the coroutine when BT reconnects —
 * cooperative cancellation via [delay] and [Flow.first] handles the abort cleanly.
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
 *
 * @param vehicleId  id of the vehicle whose paired BT device disconnected. The caller
 *   ([BluetoothConnectionReceiver]) resolves this from the device address before
 *   launching [detectParking] so the session attaches to the *actually parked* vehicle
 *   even in multi-vehicle BT configurations.
 */
class BluetoothParkingDetector(
    private val observeLocation: ObserveAdaptiveLocationUseCase,
    private val confirmParking: ConfirmParkingUseCase,
) {

    suspend fun detectParking(deviceAddress: String, vehicleId: String) {
        PaparcarLogger.d(TAG, "BT disconnected ($deviceAddress, vehicle=$vehicleId) — debouncing for reconnect check")

        // BT-005: wait before acting — brief stops and BT oscillation fire disconnect
        // events too. If BT reconnects, the Service cancels this coroutine here.
        delay(BT_DISCONNECT_DEBOUNCE_MS)

        PaparcarLogger.d(TAG, "Debounce passed — sampling GPS for parking fix")

        // BT-005: sample GPS until accuracy is good enough or timeout fires
        val parkingFix = withTimeoutOrNull(GPS_SAMPLE_TIMEOUT_MS) {
            observeLocation()
                .filter { it.accuracy <= GPS_ACCURACY_THRESHOLD_M }
                .first()
        }

        if (parkingFix == null) {
            PaparcarLogger.w(TAG, "GPS fix timed out — skipping BT parking confirmation")
            return
        }

        PaparcarLogger.d(TAG, "Got parking fix at (${parkingFix.latitude}, ${parkingFix.longitude}), accuracy=${parkingFix.accuracy}m")

        // Watch subsequent GPS updates for the distance departure check
        observeLocation()
            .filter { current ->
                val dist = FloatArray(DISTANCE_RESULT_SIZE)
                Location.distanceBetween(
                    parkingFix.latitude, parkingFix.longitude,
                    current.latitude, current.longitude,
                    dist,
                )
                dist[0] >= DISTANCE_THRESHOLD_M
            }
            .first()

        PaparcarLogger.i(TAG, "User moved ≥${DISTANCE_THRESHOLD_M}m — confirming BT parking for vehicle=$vehicleId")
        confirmParking(parkingFix, PARKING_DETECTION_RELIABILITY, vehicleId = vehicleId)
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

        /** Reliability reported to ConfirmParkingUseCase — BT is deterministic, very high. */
        const val PARKING_DETECTION_RELIABILITY = 0.95f

        /** Required size of the FloatArray passed to [Location.distanceBetween]. */
        const val DISTANCE_RESULT_SIZE = 1
    }
}

package io.apptolast.paparcar.bluetooth

import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.usecase.detection.BtParkVerdict
import io.apptolast.paparcar.domain.usecase.detection.EvaluateBtParkUseCase
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
 * 3. Sample GPS until [EvaluateBtParkUseCase] accepts a pin-grade STATIONARY candidate, or
 *    [GPS_SAMPLE_TIMEOUT_MS] elapses; a credible driving fix aborts outright — the BT drop
 *    happened mid-drive. [DET-AUDIT-002 T2]
 * 4. Record the fix as the candidate parking location.
 * 5. Walk-away watch (hard-bounded by [ParkingDetectionConfig.btWalkAwayTimeoutMs]): confirm
 *    with [ConfirmParkingUseCase] only when the displacement is WALKED (pedestrian rate);
 *    vehicle-rate displacement aborts. [DET-AUDIT-002 T2+T4]
 * 6. Post the legacy [AppNotificationManager.showParkingSaved] notification.
 *
 * **Why not the REVERT card?** BT detection is bound to the user's configured
 * `bluetoothDeviceId`, which uses a MAC address — not a model identifier. The
 * "neighbour's identical Toyota" case is impossible, and the remaining edge cases
 * (passenger in a paired vehicle, spurious BT drop while driving) are rare. The
 * REVERT card was overkill for a 0.95-reliability path; we use the simpler
 * tap-to-open-map notification instead. Users with a misfire can clean up from the
 * history screen. [BT-NOTIF-LEGACY-CLEANUP]
 *
 * @param vehicleId  id of the vehicle whose paired BT device disconnected. The caller
 *   ([BluetoothConnectionReceiver]) resolves this from the device address before
 *   launching [detectParking] so the session attaches to the *actually parked* vehicle
 *   even in multi-vehicle BT configurations.
 */
class BluetoothParkingDetector(
    private val observeLocation: ObserveAdaptiveLocationUseCase,
    private val confirmParking: ConfirmParkingUseCase,
    private val notificationPort: AppNotificationManager,
    private val config: ParkingDetectionConfig,
    private val evaluateBtPark: EvaluateBtParkUseCase,
    private val detectionEventLogger: DetectionEventLogger? = null,
) {

    suspend fun detectParking(deviceAddress: String, vehicleId: String) {
        PaparcarLogger.d(TAG, "BT disconnected ($deviceAddress, vehicle=$vehicleId) — debouncing for reconnect check")

        // BT-005: wait before acting — brief stops and BT oscillation fire disconnect
        // events too. If BT reconnects, the Service cancels this coroutine here.
        delay(BT_DISCONNECT_DEBOUNCE_MS.milliseconds)

        PaparcarLogger.d(TAG, "Debounce passed — sampling GPS for parking fix")

        // [DET-AUDIT-002 T2] Candidate fix must be pin-grade AND STATIONARY — the pure evaluator
        // decides. A credible driving fix means the BT drop happened mid-drive (head-unit battery
        // cut, interference): there is no parking, abort before any side effect. The old
        // accuracy-only gate pinned a "park" on the road and the car's own displacement then
        // satisfied the walk-away check → phantom park + phantom community spot (audit A2).
        var candidateAborted = false
        var candidate: GpsPoint? = null
        withTimeoutOrNull(GPS_SAMPLE_TIMEOUT_MS.milliseconds) {
            observeLocation().first { fix ->
                when (evaluateBtPark.evaluateCandidateFix(fix)) {
                    BtParkVerdict.DrivingAbort -> {
                        candidateAborted = true
                        true
                    }
                    BtParkVerdict.CandidateAccepted -> {
                        candidate = fix
                        true
                    }
                    else -> false
                }
            }
        }
        if (candidateAborted) {
            PaparcarLogger.w(TAG, "Credible driving during fix sampling — BT drop was mid-drive, aborting [DET-AUDIT-002 T2]")
            logRemote(sessionId = vehicleId, verdict = "bt_driving_abort")
            return
        }
        val parkingFix = candidate ?: run {
            PaparcarLogger.w(TAG, "GPS fix timed out — skipping BT parking confirmation")
            logRemote(sessionId = vehicleId, verdict = "bt_gps_timeout")
            return
        }

        PaparcarLogger.d(TAG, "Got parking fix at (${parkingFix.latitude}, ${parkingFix.longitude}), accuracy=${parkingFix.accuracy}m")

        // [DET-AUDIT-002 T2+T4] Walk-away watch, pure-evaluated (WALKED distance at pedestrian
        // rate — wheels covering it abort) and hard-bounded in time: without the ceiling, a
        // garage park (no usable GPS, never 30 m of measured walk) left this FGS + GPS pinned
        // indefinitely (audit A4, BUG-FGS-1xx class).
        var walkAborted = false
        val walkStartedAtMs = System.currentTimeMillis()
        val walkSettled = withTimeoutOrNull(config.btWalkAwayTimeoutMs.milliseconds) {
            observeLocation().first { current ->
                when (evaluateBtPark.evaluateWalkAway(parkingFix, current, System.currentTimeMillis() - walkStartedAtMs)) {
                    BtParkVerdict.DrivingAbort -> {
                        walkAborted = true
                        true
                    }
                    BtParkVerdict.WalkAwayConfirmed -> true
                    else -> false
                }
            }
        }
        if (walkSettled == null) {
            PaparcarLogger.w(TAG, "Walk-away watch expired after ${config.btWalkAwayTimeoutMs / 60_000} min — aborting cleanly (garage / no usable GPS) [DET-AUDIT-002 T4]")
            logRemote(sessionId = vehicleId, verdict = "bt_walkaway_timeout", fix = parkingFix)
            return
        }
        if (walkAborted) {
            PaparcarLogger.w(TAG, "Displacement at vehicle rate during walk-away — car still moving, aborting [DET-AUDIT-002 T2]")
            logRemote(sessionId = vehicleId, verdict = "bt_walkaway_driving_abort", fix = parkingFix)
            return
        }

        PaparcarLogger.i(TAG, "User walked ≥${config.btWalkAwayDistanceMeters}m — confirming BT parking for vehicle=$vehicleId")
        confirmParking(parkingFix, config.reliabilityBluetooth, vehicleId = vehicleId)
            .onSuccess { saved ->
                logRemote(sessionId = saved.id, verdict = "bt_park_confirmed", fix = parkingFix)
                // Legacy tap-to-open-map notification. [BT-NOTIF-LEGACY-CLEANUP]
                notificationPort.showParkingSaved(saved.location.latitude, saved.location.longitude)
            }
            .onFailure { e ->
                PaparcarLogger.e(TAG, "Failed to confirm parking", e)
                logRemote(sessionId = vehicleId, verdict = "bt_park_refused")
            }
    }

    /** Firestore-visible verdict trail — the BT path emitted ZERO remote telemetry before
     *  2026-07-07, so a field failure here could only be diagnosed with the phone on a cable. */
    private suspend fun logRemote(
        sessionId: String,
        verdict: String,
        fix: GpsPoint? = null,
    ) {
        runCatching {
            detectionEventLogger?.log(
                DetectionEvent.DepartureVerdict(
                    sessionId = sessionId,
                    timestampMs = System.currentTimeMillis(),
                    verdict = verdict,
                    source = "bt",
                    speedKmh = fix?.speed?.times(KMH_PER_MPS),
                    location = fix,
                )
            )
        }
    }

    private companion object {
        /** PARKDIAG prefix: FileAntilog only persists PARKDIAG-tagged lines. */
        const val TAG = "PARKDIAG/BTDetector"
        const val KMH_PER_MPS = 3.6f

        /** BT-005: Grace window before acting on disconnect (brief stop / oscillation debounce). */
        const val BT_DISCONNECT_DEBOUNCE_MS = 30_000L


        /** BT-005: Give up waiting for a good GPS fix after this duration. */
        const val GPS_SAMPLE_TIMEOUT_MS = 60_000L


    }
}

package com.rndeveloper.paparcar.bluetooth

import com.rndeveloper.paparcar.domain.detection.ArmEvidence
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.usecase.detection.BtCandidateHunt
import com.rndeveloper.paparcar.domain.usecase.detection.BtEngagement
import com.rndeveloper.paparcar.domain.usecase.detection.BtParkVerdict
import com.rndeveloper.paparcar.domain.usecase.detection.EvaluateBtParkUseCase
import com.rndeveloper.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Deterministic parking detector driven by Bluetooth connection events.
 *
 * Stateless: owns no [kotlinx.coroutines.CoroutineScope] and no [kotlinx.coroutines.Job].
 * The caller ([BluetoothDetectionService]) runs [detectParking] inside its own
 * [androidx.lifecycle.lifecycleScope] and cancels the coroutine when BT reconnects —
 * cooperative cancellation at `first` handles the abort cleanly.
 *
 * **Disconnect → park flow:**
 * 1. Car BT disconnects.
 * 2. Sample GPS **from that instant**, folding every fix into a [BtCandidateHunt]: the EARLIEST
 *    pin-grade STATIONARY fix becomes the candidate, and a credible driving fix aborts outright —
 *    the BT drop happened mid-drive. [DET-AUDIT-002 T2]
 * 3. [BT_DISCONNECT_DEBOUNCE_MS] grace window — nothing may ACT before it elapses; if BT reconnects
 *    the caller cancels this coroutine (BT-005: oscillation / traffic-light guard). The hunt keeps
 *    looking throughout, so the window is a floor on acting, never on observing.
 *    [DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001]
 * 4. Past the window, the candidate in hand IS the parking location. Without one, keep sampling to
 *    the [GPS_SAMPLE_TIMEOUT_MS] ceiling.
 * 5. Walk-away watch (hard-bounded by [ParkingDetectionConfig.btWalkAwayTimeoutMs]): confirm
 *    with [ConfirmParkingUseCase] only when the displacement is WALKED (pedestrian rate);
 *    vehicle-rate displacement aborts. [DET-AUDIT-002 T2+T4]
 * 6. Post the legacy [AppNotificationManager.showParkingSaved] notification.
 *
 * **Why the hunt starts at the disconnect.** A wait that exists so we do not ACT cannot also delay
 * what we OBSERVE: the car's position can only be lost with time, never recovered. Sleeping through
 * the debounce and pinning the first fix after it put the pin where the user's BODY was 30–90 s
 * later — 35–45 m of walk at pedestrian pace, and further still while
 * [ParkingDetectionConfig.stoppedSpeedThresholdMps] (1 m/s, below human walking pace) held out for a
 * standstill that only came at a crossing or a doorway. The location foreground service is already
 * promoted before this runs, so looking earlier costs no permission and no extra service — only the
 * seconds of GPS the event had already reserved. [DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001]
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

    suspend fun detectParking(deviceAddress: String, vehicleId: String, connectedAtMs: Long?) {
        PaparcarLogger.d(TAG, "BT disconnected ($deviceAddress, vehicle=$vehicleId) — debouncing for reconnect check")

        // [DET-BT-DISCONNECT-WITHOUT-RIDE-001] Was this the end of a RIDE, or just a car passing
        // within radio range? Asked before the debounce so a proximity blip costs nothing: no FGS
        // held for 15 minutes, no GPS stream, no pin. The engagement below could not confirm a
        // parking anyway — it can only nominate one.
        val disconnectedAtMs = System.currentTimeMillis()
        val engagement = evaluateBtPark.evaluateEngagement(connectedAtMs, disconnectedAtMs)
        if (engagement !is BtEngagement.Ride) {
            val shape = when (engagement) {
                is BtEngagement.ProximityOnly -> "proximity ${engagement.durationMs / 1000}s < ${config.btMinRideDurationMs / 1000}s"
                else -> "no connect on record"
            }
            PaparcarLogger.w(TAG, "BT engagement was not a ride ($shape) — asking instead of placing [DET-BT-DISCONNECT-WITHOUT-RIDE-001]")
            logRemote(sessionId = vehicleId, verdict = VERDICT_NO_RIDE_ASK)
            // The car IS somewhere near — that is exactly what the engagement proves — but WHERE it
            // ended up is the user's to say: the fix we could sample sits at the phone, and the
            // phone was not in the car. The durable nudge survives being slept through and the
            // confirmed pin keeps detection provenance. [DET-NUDGE-PERSIST-001]
            notificationPort.showMarkParkingNudge(source = NUDGE_SOURCE_BT_NO_RIDE, vehicleId = vehicleId)
            return
        }
        PaparcarLogger.d(TAG, "BT engagement ${engagement.durationMs / 1000}s — ride-shaped, continuing")

        PaparcarLogger.d(TAG, "Hunting the parking fix from the disconnect — debounce gates acting, not looking")

        // [DET-AUDIT-002 T2] Candidate fix must be pin-grade AND STATIONARY — the pure evaluator
        // decides. A credible driving fix means the BT drop happened mid-drive (head-unit battery
        // cut, interference): there is no parking, abort before any side effect. The old
        // accuracy-only gate pinned a "park" on the road and the car's own displacement then
        // satisfied the walk-away check → phantom park + phantom community spot (audit A2).
        //
        // [DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001] The hunt runs from the disconnect and keeps the
        // EARLIEST candidate, but settles no earlier than the BT-005 debounce: BT reconnect cancels
        // this coroutine at `first` exactly as it used to at `delay`. Two consequences worth naming:
        // driving is now watched over the first 30 s too (it was not), and the ceiling can expire
        // with a good candidate in hand — which is a park to save, not a timeout to report.
        var hunt = BtCandidateHunt(sinceMs = disconnectedAtMs)
        withTimeoutOrNull((BT_DISCONNECT_DEBOUNCE_MS + GPS_SAMPLE_TIMEOUT_MS).milliseconds) {
            observeLocation().first { fix ->
                val atMs = System.currentTimeMillis()
                hunt = evaluateBtPark.foldCandidateFix(hunt, fix, atMs)
                val debounced = atMs - disconnectedAtMs >= BT_DISCONNECT_DEBOUNCE_MS
                hunt.aborted || (hunt.candidate != null && debounced)
            }
        }
        if (hunt.aborted) {
            PaparcarLogger.w(TAG, "Credible driving during fix sampling — BT drop was mid-drive, aborting [DET-AUDIT-002 T2]")
            logRemote(sessionId = vehicleId, verdict = "bt_driving_abort")
            return
        }
        val candidate = hunt.candidate ?: run {
            PaparcarLogger.w(TAG, "GPS fix timed out — skipping BT parking confirmation")
            logRemote(sessionId = vehicleId, verdict = "bt_gps_timeout")
            return
        }
        val parkingFix = candidate.fix
        // The number this lane is judged by in the field: how much of the user's walk is baked into
        // the pin. Before the hunt started at the disconnect it could not be under 30 000 ms.
        val pinLagMs = candidate.atMs - disconnectedAtMs

        PaparcarLogger.d(TAG, "Got parking fix at (${parkingFix.latitude}, ${parkingFix.longitude}), accuracy=${parkingFix.accuracy}m, pin lag=${pinLagMs}ms")

        // [DET-AUDIT-002 T2+T4] Walk-away watch, pure-evaluated (WALKED distance at pedestrian
        // rate — wheels covering it abort) and hard-bounded in time: without the ceiling, a
        // garage park (no usable GPS, never 30 m of measured walk) left this FGS + GPS pinned
        // indefinitely (audit A4, BUG-FGS-1xx class).
        //
        // [DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001] The pedestrian rate is measured from the
        // CANDIDATE's instant, not from the start of this watch: the displacement is measured from
        // its fix, so the clock has to run from the same seal. They used to coincide by accident;
        // now the candidate can be half a minute older, and the walk it covered in the meantime
        // would read as a teleport and abort a real park.
        var walkAborted = false
        val walkSettled = withTimeoutOrNull(config.btWalkAwayTimeoutMs.milliseconds) {
            observeLocation().first { current ->
                when (evaluateBtPark.evaluateWalkAway(candidate, current, System.currentTimeMillis())) {
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
            // [DET-BT-TIMEOUT-SAVE-001] The watch expired with the STATIONARY pin-grade candidate
            // still standing: the car really parked and the user simply never covered 30 m (home
            // park, went straight inside — field 2026-08-06 01:46, Kamiq). Losing the OWN session
            // here was a regression vs the coordinator (which confirms home parks via steps+egress):
            // the walk-away guards COMMUNITY trust, not the user's private "where is my car".
            // Save the session at the reduced no-walk reliability; nothing community-facing is
            // published at confirm time (spots publish at departure, with their own guards).
            // A mid-drive BT drop cannot reach here: vehicle-rate displacement during the watch
            // aborts below, and a driving candidate never became [BtParkVerdict.CandidateAccepted].
            PaparcarLogger.w(TAG, "Walk-away watch expired after ${config.btWalkAwayTimeoutMs / 60_000} min — saving own session without walk corroboration [DET-BT-TIMEOUT-SAVE-001]")
            confirmParking(
                parkingFix,
                config.reliabilityBluetoothTimeoutSave,
                vehicleId = vehicleId,
                detectionPath = PATH_BLUETOOTH_TIMEOUT,
                // [DET-BT-DISCONNECT-WITHOUT-RIDE-001] Stamp WHAT armed this BT session. The lane
                // used to persist nothing, so a field pin could only be traced over a cable.
                armEvidence = ArmEvidence.BtRide(engagement.durationMs).label,
                // The user stayed within the walk-away radius for the whole watch, so the pin IS
                // an honest body position (±30 m) — unlike the egress-confirm case that bans
                // sealing at the pin. [DET-STEP-BUDGET-ORIGIN-001]
                sealPoint = parkingFix,
            )
                .onSuccess { saved ->
                    logRemote(sessionId = saved.id, verdict = "bt_timeout_save", fix = parkingFix, pinLagMs = pinLagMs)
                    notificationPort.showParkingSaved(saved.location.latitude, saved.location.longitude)
                }
                .onFailure { e ->
                    PaparcarLogger.e(TAG, "Failed to confirm timeout-save parking", e)
                    logRemote(sessionId = vehicleId, verdict = "bt_timeout_save_refused")
                }
            return
        }
        if (walkAborted) {
            PaparcarLogger.w(TAG, "Displacement at vehicle rate during walk-away — car still moving, aborting [DET-AUDIT-002 T2]")
            logRemote(sessionId = vehicleId, verdict = "bt_walkaway_driving_abort", fix = parkingFix)
            return
        }

        PaparcarLogger.i(TAG, "User walked ≥${config.btWalkAwayDistanceMeters}m — confirming BT parking for vehicle=$vehicleId")
        confirmParking(
            parkingFix,
            config.reliabilityBluetooth,
            vehicleId = vehicleId,
            detectionPath = PATH_BLUETOOTH,
            // [DET-BT-DISCONNECT-WITHOUT-RIDE-001] Same provenance stamp as the timeout-save branch.
            armEvidence = ArmEvidence.BtRide(engagement.durationMs).label,
            // The fix that settled the walk-away IS where the body is at confirm (≥30 m from the
            // car already) — the honest origin for the step baseline. [DET-STEP-BUDGET-ORIGIN-001]
            sealPoint = walkSettled,
        )
            .onSuccess { saved ->
                logRemote(sessionId = saved.id, verdict = "bt_park_confirmed", fix = parkingFix, pinLagMs = pinLagMs)
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
        pinLagMs: Long? = null,
    ) {
        runCatching {
            detectionEventLogger?.log(
                DetectionEvent.DepartureVerdict(
                    sessionId = sessionId,
                    timestampMs = System.currentTimeMillis(),
                    verdict = verdict,
                    source = "bt",
                    speedKmh = fix?.speed?.times(KMH_PER_MPS),
                    // [DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001] How old the pinned fix already was
                    // when the disconnect happened — the walk baked into the pin, in ms. Rides the
                    // DTO's existing "how old was this signal" column, the same reuse
                    // ActivityTransition, Cadence and the displacement witness already make, so the
                    // number reaches remote with no serializer surface change.
                    enterAgeMs = pinLagMs,
                    location = fix,
                )
            )
        }
    }

    private companion object {
        /** PARKDIAG prefix: FileAntilog only persists PARKDIAG-tagged lines. */
        const val TAG = "PARKDIAG/BTDetector"
        const val KMH_PER_MPS = 3.6f

        /** Pin provenance path for the deterministic Bluetooth strategy. [DET-PIN-PROVENANCE-001] */
        const val PATH_BLUETOOTH = "bt"

        /** Provenance path for a BT park saved on walk-away TIMEOUT (stationary candidate, no 30 m
         *  walk — the home-park case). Distinct so field forensics can tell the two apart at a
         *  glance. [DET-BT-TIMEOUT-SAVE-001] */
        const val PATH_BLUETOOTH_TIMEOUT = "bt_timeout"

        /** [DET-BT-DISCONNECT-WITHOUT-RIDE-001] Remote verdict for an engagement that proved
         *  presence but not a ride: the lane asked instead of placing. */
        const val VERDICT_NO_RIDE_ASK = "bt_no_ride_ask"

        /** Nudge provenance so the diagnostics can tell this ask from the coordinator's. */
        const val NUDGE_SOURCE_BT_NO_RIDE = "bt_no_ride"

        /** BT-005: Grace window before ACTING on a disconnect (brief stop / oscillation debounce).
         *  ⛔ Not a window before LOOKING: the hunt for the parked-car fix runs from the disconnect,
         *  because every second of it is metres of the user's walk baked into the pin.
         *  [DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001] */
        const val BT_DISCONNECT_DEBOUNCE_MS = 30_000L

        /** BT-005: Give up waiting for a good GPS fix this long after the debounce closes — so the
         *  hunt's whole ceiling is [BT_DISCONNECT_DEBOUNCE_MS] + this, unchanged at 90 s. */
        const val GPS_SAMPLE_TIMEOUT_MS = 60_000L


    }
}

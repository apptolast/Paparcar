package com.rndeveloper.paparcar.bluetooth

import com.rndeveloper.paparcar.domain.detection.btDriveAwayIsMeasured
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/** What the drive-away watch saw. [DET-A-BT-CONNECT-IS-A-DEPARTURE-001] */
sealed interface BtDepartureOutcome {
    /** The car measurably left the plaza. [fix] is the sample that proved it. */
    data class Measured(val fix: GpsPoint) : BtDepartureOutcome

    /** The watch expired without measuring a drive — the ordinary "got in and did not go" case. */
    data object NeverDrove : BtDepartureOutcome
}

/**
 * [DET-A-BT-CONNECT-IS-A-DEPARTURE-001] The departure twin of [BluetoothParkingDetector]: the paired
 * car CONNECTED while one of its plazas is still held, so watch whether it actually drives away.
 *
 * Stateless, exactly like its park-side twin — the caller ([BluetoothDetectionService]) owns the
 * scope and the foreground service, and cancels the coroutine when the car disconnects again.
 *
 * **Why this exists.** At CONNECT three things already happen and none of them watches a drive: the
 * arbitration lets Bluetooth override a running Coordinator session, the strategy resolver flips to
 * `BLUETOOTH`, and `resolvePostDetectionLifecycle` therefore answers `Stop` — the resident sentry
 * that WAS watching the parked car stands down at the exact moment the car is about to leave. The
 * safety-net check the CONNECT already enqueues is preparation (it re-seals the anchor and cures the
 * fence state), and preparation is all it can be: it runs while the user is still AT the car, so it
 * cannot witness a departure that has not happened yet. Between CONNECT and DISCONNECT, Bluetooth
 * owns detection and detects nothing.
 *
 * **What it does NOT do.** It does not confirm, publish or close anything. A CONNECT proves boarding
 * and never a drive, so this only supplies the ONE thing the lane lacked — an app-owned witness of
 * the moment the car pulls out, independent of a geofencing engine that starves when the phone is
 * still. The verdict, the publication and the session close stay in the common door
 * (`DepartureDetectionWorker` → `RunDepartureCheckUseCase`), which is what keeps this lane out of
 * the Coordinator's scoring while converging on its machinery — the same shape as
 * [com.rndeveloper.paparcar.domain.usecase.parking.ConfirmParkingUseCase] on the way in.
 *
 * Asymmetric failure: measuring nothing means standing down in silence. No plaza, no session
 * closed, no question asked — the car is exactly where the user left it and the geofence still
 * guards it.
 *
 * ## The case this watch could get wrong, and what stops it
 *
 * Board your own car, change your mind, get out and leave in a friend's. The phone then moves
 * beyond pedestrian reach of a plaza whose car never left — the false positive
 * [DET-BT-IDENTITY-GATE-001] exists to prevent on the safety-net lane, arriving by a different
 * door (this one dispatches straight to the departure worker and never consults that gate).
 *
 * What stops it is the ACL DISCONNECT: leaving the paired car drops the link within seconds — long
 * before a car could carry the phone past the pedestrian ceiling — and
 * [BluetoothDetectionService.handleDisconnected] cancels this job before doing anything else. The
 * guard is therefore the lane's own cancellation, not an extra check, which is why there isn't
 * one: a second guard here would be a duplicate of a race this lane already wins by construction.
 */
class BluetoothDepartureDetector(
    private val observeLocation: ObserveAdaptiveLocationUseCase,
    private val config: ParkingDetectionConfig,
    private val detectionEventLogger: DetectionEventLogger? = null,
) {

    /**
     * @param session the still-active parking of the vehicle that just connected.
     * @param connectedAtMs the measured instant of boarding, from [BtConnectionStore].
     */
    suspend fun watchForDeparture(session: UserParking, connectedAtMs: Long): BtDepartureOutcome {
        val fenceRadius = config.geofenceRadiusFor(session.sizeCategory, session.location.accuracy)
        PaparcarLogger.d(
            TAG,
            "boarded vehicle=${session.vehicleId} with plaza ${session.id.take(8)} still held — " +
                "watching for a measured drive-away (fence=${fenceRadius}m, ceiling=${config.btDepartureWatchTimeoutMs / 60_000}min)",
        )

        var proof: GpsPoint? = null
        withTimeoutOrNull(config.btDepartureWatchTimeoutMs.milliseconds) {
            observeLocation().first { fix ->
                val measured = btDriveAwayIsMeasured(
                    pin = session.location,
                    current = fix,
                    connectedAtMs = connectedAtMs,
                    fenceRadiusMeters = fenceRadius,
                    nowMs = System.currentTimeMillis(),
                    config = config,
                )
                if (measured) proof = fix
                measured
            }
        }

        val measuredFix = proof
        return if (measuredFix != null) {
            PaparcarLogger.i(
                TAG,
                "drive-away MEASURED ${(measuredFix.timestamp - connectedAtMs) / 1000}s after boarding — " +
                    "handing the departure to the common door, dated by the CONNECT",
            )
            logRemote(session, "bt_departure_measured", measuredFix, connectedAtMs)
            BtDepartureOutcome.Measured(measuredFix)
        } else {
            // The ordinary answer, and the honest one: the user got in and did not go anywhere the
            // GPS could see. Nothing is published and nothing is closed.
            PaparcarLogger.d(TAG, "watch expired with no measured drive — the car stays parked as far as we know")
            logRemote(session, "bt_departure_never_drove", fix = null, connectedAtMs = connectedAtMs)
            BtDepartureOutcome.NeverDrove
        }
    }

    /** Firestore-visible verdict trail, same lane and shape the park side already writes with
     *  `source = "bt"` — so a field trace can tell a BT-witnessed departure from a geofence one. */
    private suspend fun logRemote(
        session: UserParking,
        verdict: String,
        fix: GpsPoint?,
        connectedAtMs: Long,
    ) {
        runCatching {
            detectionEventLogger?.log(
                DetectionEvent.DepartureVerdict(
                    sessionId = session.geofenceId ?: session.id,
                    timestampMs = System.currentTimeMillis(),
                    verdict = verdict,
                    source = "bt",
                    speedKmh = fix?.speed?.times(KMH_PER_MPS),
                    // How long after boarding the drive became measurable — the number this lane
                    // will be calibrated by. Rides the existing "how old was this signal" column,
                    // same reuse the park side makes. [DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001]
                    enterAgeMs = fix?.let { it.timestamp - connectedAtMs },
                    location = fix,
                )
            )
        }
    }

    private companion object {
        /** PARKDIAG prefix: FileAntilog only persists PARKDIAG-tagged lines. */
        const val TAG = "PARKDIAG/BTDeparture"
        const val KMH_PER_MPS = 3.6f
    }
}

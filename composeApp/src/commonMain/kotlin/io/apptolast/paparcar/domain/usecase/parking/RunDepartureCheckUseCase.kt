@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.detection.DepartureConfirmationListener
import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger
import io.apptolast.paparcar.domain.service.DepartureEventBus
import io.apptolast.paparcar.domain.usecase.location.GetOneLocationUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock

/**
 * Outcome of one departure-check attempt — the caller (WorkManager on Android) translates it
 * to its retry vocabulary. [DET-SOLID-001]
 */
sealed class DepartureCheckOutcome {
    /** Signals inconclusive and attempts remain — retry with backoff. */
    data object Retry : DepartureCheckOutcome()
    /** Definitively not a departure (rejected, or attempts exhausted without any vehicle
     *  signal — the walking-near-the-car guard [BUG-WALK-DEPART-001]). Nothing released. */
    data object Dismissed : DepartureCheckOutcome()
    /** Departure confirmed and all side-effects processed (spot published, session cleared). */
    data object Processed : DepartureCheckOutcome()
    /** Departure confirmed but processing failed — retry so the session is never left open. */
    data object ProcessFailedRetry : DepartureCheckOutcome()
}

/**
 * One full departure-check attempt for a geofence exit: sample speed → decide → (on confirm)
 * upgrade the live detection session + process all departure side-effects.
 *
 * Extracted from `DepartureDetectionWorker.doWork` so the retry/fallthrough/upgrade sequence —
 * the seam where walking-exit false positives used to live — is a pure-domain, commonTest-testable
 * unit. The worker is reduced to input parsing + `Result` translation. [DET-SOLID-001]
 */
class RunDepartureCheckUseCase(
    private val detectParkingDeparture: DetectParkingDepartureUseCase,
    private val processConfirmedDeparture: ProcessConfirmedDepartureUseCase,
    private val getOneLocation: GetOneLocationUseCase,
    private val departureEventBus: DepartureEventBus,
    private val departureConfirmationListener: DepartureConfirmationListener,
    private val detectionEventLogger: DetectionEventLogger? = null,
) {
    /**
     * @param attempt 0-based attempt counter from the scheduler (WorkManager's `runAttemptCount`).
     */
    suspend operator fun invoke(
        geofenceId: String,
        exitTimestampMs: Long,
        attempt: Int,
    ): DepartureCheckOutcome {
        val speedKmh = getOneLocation()?.speed?.times(KMH_PER_MPS)

        val decision = detectParkingDeparture(
            geofenceId = geofenceId,
            exitTimestampMs = exitTimestampMs,
            currentSpeedKmh = speedKmh,
        )

        // [DET-SOLID-001] Observability: every attempt's verdict, traced by geofenceId.
        runCatching {
            detectionEventLogger?.log(
                DetectionEvent.DepartureVerdict(
                    sessionId = geofenceId,
                    timestampMs = Clock.System.now().toEpochMilliseconds(),
                    verdict = decision::class.simpleName ?: "UNKNOWN",
                    source = "worker",
                    attempt = attempt,
                    speedKmh = speedKmh,
                    enterAgeMs = departureEventBus.lastVehicleEnteredAt?.let { exitTimestampMs - it },
                )
            )
        }

        if (decision == DepartureDecision.Rejected) return DepartureCheckOutcome.Dismissed

        if (decision == DepartureDecision.Inconclusive && attempt < MAX_INCONCLUSIVE_ATTEMPTS) {
            return DepartureCheckOutcome.Retry
        }
        // Attempts exhausted. Fall through only if IN_VEHICLE_ENTER was recorded after parking
        // was confirmed — covers slow garage exits where speed never crosses the departure
        // threshold. Without any vehicle signal, dismiss to avoid false positives from the user
        // walking near the car. [BUG-WALK-DEPART-001]
        if (decision == DepartureDecision.Inconclusive && departureEventBus.lastVehicleEnteredAt == null) {
            PaparcarLogger.d(TAG, "attempts exhausted with no vehicle signal — dismissed (geof=$geofenceId)")
            return DepartureCheckOutcome.Dismissed
        }

        // [DET-G-05] The departure is confirmed. If the GEOFENCE_EXIT armed the coordinator
        // UNVERIFIED (no vehicle evidence at arm time — AR ENTER delivers up to ~2 min late),
        // upgrade the live session so its confirm paths unlock: the drive provably happened.
        departureConfirmationListener.notifyDepartureConfirmed()

        return processConfirmedDeparture(geofenceId).fold(
            onSuccess = { DepartureCheckOutcome.Processed },
            onFailure = { DepartureCheckOutcome.ProcessFailedRetry },
        )
    }

    companion object {
        private const val TAG = "RunDepartureCheckUseCase"
        private const val KMH_PER_MPS = 3.6f

        /**
         * Maximum attempts allowed to stay [DepartureCheckOutcome.Retry] on an inconclusive
         * decision. With the worker's EXPONENTIAL backoff starting at 15s the retries fire at
         * ~15s, ~30s, ~60s — a ~2 min window for AR delivery and for the vehicle to accelerate
         * past the departure threshold.
         */
        const val MAX_INCONCLUSIVE_ATTEMPTS = 3
    }
}

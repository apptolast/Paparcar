@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.detection.DepartureConfirmationListener
import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
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
    private val config: ParkingDetectionConfig,
    private val detectionEventLogger: DetectionEventLogger? = null,
    /** Injectable clock so the freshness gate is testable with fixed timestamps. */
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    /**
     * @param attempt      0-based attempt counter from the scheduler (WorkManager's `runAttemptCount`).
     * @param preconfirmed true when the parked-state reconcile already PROVED the departure
     *        (fresh anchor + step budget: displacement without the steps to walk it). The trip is
     *        over by then — the user is stationary at the destination — so re-sampling live speed
     *        here would veto a real departure; skip the decision, keep the processing machinery.
     *        [DET-RECONCILE-001]
     */
    suspend operator fun invoke(
        geofenceId: String,
        exitTimestampMs: Long,
        attempt: Int,
        preconfirmed: Boolean = false,
    ): DepartureCheckOutcome {
        if (!preconfirmed) {
            // Fresh fix only: this samples CURRENT speed — a cached fix answers "how fast was the
            // phone some minutes ago", which wastes attempts and skews the verdict. [DET-RECONCILE-001]
            // The fix travels WHOLE (speed + accuracy): speed alone is not evidence, the decision
            // applies the canonical credible-driving rule. [DET-EXIT-TRUST-001]
            val fix = getOneLocation(maxAgeMs = config.freshFixMaxAgeMs)
            val speedKmh = fix?.speed?.times(KMH_PER_MPS)

            val decision = detectParkingDeparture(
                geofenceId = geofenceId,
                exitTimestampMs = exitTimestampMs,
                currentFix = fix,
            )
            PaparcarLogger.d(TAG, "attempt=$attempt geof=${geofenceId.take(8)} speed=${speedKmh}km/h acc=${fix?.accuracy}m → ${decision::class.simpleName}")

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

            if (decision is DepartureDecision.Inconclusive && attempt < MAX_INCONCLUSIVE_ATTEMPTS) {
                return DepartureCheckOutcome.Retry
            }
            // Attempts exhausted. Fall through only on an ADMISSIBLE boarding — an IN_VEHICLE_ENTER
            // stamped after THIS session began and within the window of the exit (the decision
            // computes that, it holds the session) — covers slow garage exits where speed never
            // crosses the departure threshold. A raw bus null-check here accepted a re-delivered
            // ENTER from the trip that CREATED the parking and erased a correct session while the
            // user walked away (field 2026-07-08 18:54). Without admissible vehicle evidence,
            // dismiss. [BUG-WALK-DEPART-001][DET-SESSION-BIRTH-001]
            if (decision is DepartureDecision.Inconclusive && !decision.admissibleBoarding) {
                PaparcarLogger.d(TAG, "attempts exhausted with no admissible vehicle signal — dismissed (geof=$geofenceId)")
                return DepartureCheckOutcome.Dismissed
            }
        } else {
            PaparcarLogger.d(TAG, "preconfirmed by parked-state reconcile — skipping live speed re-check (geof=${geofenceId.take(8)})")
            runCatching {
                detectionEventLogger?.log(
                    DetectionEvent.DepartureVerdict(
                        sessionId = geofenceId,
                        timestampMs = Clock.System.now().toEpochMilliseconds(),
                        verdict = "Preconfirmed",
                        source = "worker",
                        attempt = attempt,
                    )
                )
            }
        }

        // [DET-G-05] The departure is confirmed. If the GEOFENCE_EXIT armed the coordinator
        // UNVERIFIED (no vehicle evidence at arm time — AR ENTER delivers up to ~2 min late),
        // upgrade the live session so its confirm paths unlock: the drive provably happened.
        departureConfirmationListener.notifyDepartureConfirmed()

        // [DET-RECONCILE-001] Freshness gate: a departure recovered long after the fact (offline
        // device, frozen worker — Redmi 2026-07-06 processed 5 h late) still converges the local
        // state, but the freed spot is long gone — advertising it would sell ghosts.
        val exitAgeMs = nowMs() - exitTimestampMs
        val publishSpot = exitAgeMs <= config.spotPublishMaxAgeMs
        if (!publishSpot) {
            PaparcarLogger.d(TAG, "stale departure (age=${exitAgeMs / 60_000}min) — clearing WITHOUT publishing (geof=${geofenceId.take(8)})")
        }

        return processConfirmedDeparture(geofenceId, publishSpot = publishSpot).fold(
            onSuccess = { DepartureCheckOutcome.Processed },
            onFailure = { DepartureCheckOutcome.ProcessFailedRetry },
        )
    }

    companion object {
        private const val TAG = "PARKDIAG/Depart"
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

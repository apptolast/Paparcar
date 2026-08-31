@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.detection.DepartureConfirmationListener
import com.rndeveloper.paparcar.domain.detection.DepartureProof
import com.rndeveloper.paparcar.domain.detection.DetectionRuntimeState
import com.rndeveloper.paparcar.domain.detection.freedSpotIsStillThere
import com.rndeveloper.paparcar.domain.detection.provenanceLabel
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.service.DepartureEventBus
import com.rndeveloper.paparcar.domain.usecase.location.GetOneLocationUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
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
    /**
     * Departure confirmed and all side-effects processed (spot published, session cleared).
     *
     * [DET-A-JUST-DEPARTED-CAR-IS-NOT-NO-SESSION-001] When this attempt MEASURED the departure and
     * no live detection existed to upgrade, [followTrip] carries the follower handoff the caller
     * must arm — clearing the last parked session of the active car with nobody following the trip
     * is what left the detector deaf on 2026-08-31 (two parks lost). Null when a live session
     * already follows the trip, or when nothing was measured NOW (preconfirmed reconcile / boarding
     * fall-through — the trip may be long over).
     */
    data class Processed(val followTrip: DepartureFollowHandoff? = null) : DepartureCheckOutcome()
    /** Departure confirmed but processing failed — retry so the session is never left open. */
    data object ProcessFailedRetry : DepartureCheckOutcome()
}

/**
 * [DET-A-JUST-DEPARTED-CAR-IS-NOT-NO-SESSION-001] What the follower arm needs from the attempt that
 * measured the departure: the fence whose pin was just cleared (the trip's real anchor) and the
 * measurement itself, which becomes `ArmEvidence.DepartureFollowed` — persisted provenance, so a
 * field trace can tell this arm from a sentry wake without a logcat attached.
 */
data class DepartureFollowHandoff(
    val geofenceId: String,
    val speedKmh: Float,
    val accuracyM: Float?,
)

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
    /** [DET-A-JUST-DEPARTED-CAR-IS-NOT-NO-SESSION-001] Whether a live detection session already
     *  follows this trip — the [DepartureCheckOutcome.Processed.followTrip] gate. No default, per
     *  [DET-A-DOUBT-FIELD-MUST-NOT-DEFAULT-TO-CERTAINTY-001]: a default here would silently answer
     *  "someone is following" forever. */
    private val detectionRuntime: DetectionRuntimeState,
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
        // [DET-HANDOFF-NOT-MANUAL-001 §B] How well this departure ends up being proven decides what
        // may be COMMITTED — the spot always, the user's car only on measured driving. Starts at
        // Deduced and is upgraded only by a fresh fix at credible driving speed: the
        // parked-state reconcile (`preconfirmed`) infers from where the PHONE is, and the
        // attempts-exhausted boarding fall-through rests on an AR ENTER, and both are equally
        // satisfied by a bicycle (field 2026-08-19).
        var proof = DepartureProof.Deduced
        // [DET-A-JUST-DEPARTED-CAR-IS-NOT-NO-SESSION-001] The measurement behind a Witnessed proof,
        // kept for the follower handoff. Only THIS attempt's fresh fix may fill it: the watchdog and
        // the preconfirmed reconcile never reach this branch, and that is the gate — a departure
        // committed without a fix measured NOW says nothing about whether a trip is still live.
        var witnessedSpeedKmh: Float? = null
        var witnessedAccuracyM: Float? = null
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
            // [DET-DEPART-PROOF-001] Carry the inconclusive reason into the verdict label so the
            // field telemetry distinguishes "no evidence yet" from "credible speed rejected as
            // the exit's own echo" — the shape that published a phantom spot on 2026-07-27.
            val verdictLabel = when {
                decision is DepartureDecision.Inconclusive && decision.reason != null ->
                    "Inconclusive(${decision.reason})"
                else -> decision::class.simpleName ?: "UNKNOWN"
            }
            PaparcarLogger.d(TAG, "attempt=$attempt geof=${geofenceId.take(8)} speed=${speedKmh}km/h acc=${fix?.accuracy}m src=${fix?.provenanceLabel() ?: "-"} → $verdictLabel")

            // [DET-SOLID-001] Observability: every attempt's verdict, traced by geofenceId.
            runCatching {
                detectionEventLogger?.log(
                    DetectionEvent.DepartureVerdict(
                        sessionId = geofenceId,
                        timestampMs = Clock.System.now().toEpochMilliseconds(),
                        verdict = verdictLabel,
                        source = "worker",
                        attempt = attempt,
                        speedKmh = speedKmh,
                        enterAgeMs = departureEventBus.lastVehicleEnteredAt?.let { exitTimestampMs - it },
                    )
                )
            }

            // [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001] A refuted departure must also take back
            // what its own arm was granted on trust. The live coordinator may be running seeded
            // "already driving" by THIS exit's evidence, with every anti-walking guard disarmed.
            if (decision == DepartureDecision.Rejected) {
                departureConfirmationListener.notifyDepartureDismissed(geofenceId)
                return DepartureCheckOutcome.Dismissed
            }

            // The only branch that MEASURED the car moving: a fresh fix at credible driving speed,
            // independent of the exit's own echo. [DET-HANDOFF-NOT-MANUAL-001 §B]
            if (decision == DepartureDecision.Confirmed) {
                proof = DepartureProof.Witnessed
                witnessedSpeedKmh = speedKmh
                witnessedAccuracyM = fix?.accuracy
            }

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
                departureConfirmationListener.notifyDepartureDismissed(geofenceId)
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
        // [DET-WATCHDOG-DEPARTURE-KNOWS-NO-HOUR-001] The rule moved out to `freedSpotIsStillThere`
        // unchanged: this used to be the ONLY caller that asked it, and the watchdog close — which
        // holds no exit instant at all — published on the default instead.
        val now = nowMs()
        val exitAgeMs = now - exitTimestampMs
        val publishSpot = freedSpotIsStillThere(exitAtMs = exitTimestampMs, nowMs = now, config = config)
        if (!publishSpot) {
            PaparcarLogger.d(TAG, "stale departure (age=${exitAgeMs / 60_000}min) — clearing WITHOUT publishing (geof=${geofenceId.take(8)})")
        }

        // [DET-A-JUST-DEPARTED-CAR-IS-NOT-NO-SESSION-001] A measured departure with nobody following
        // the trip hands over a follower. Field 2026-08-31 21:22:44 (Oppo): this very confirm ran
        // 6 s after the live session's own abort, cleared the active car's only parked session, and
        // the detector went deaf — the real AR ENTER died in NoSession at 21:28 and 39 sentry wakes
        // stood down all night. The Redmi on the SAME trip was saved only because a sentry wake
        // re-armed 100 s before its clear: this makes that ordering accident a guarantee. The
        // `isRunning` read is advisory (the arm site re-checks); nothing here arms — the caller does,
        // through the service's own door, with this measurement as the arm's evidence.
        val followTrip = witnessedSpeedKmh
            ?.takeIf { proof == DepartureProof.Witnessed && !detectionRuntime.isRunning.value }
            ?.let { measured ->
                PaparcarLogger.d(
                    TAG,
                    "measured departure with no live session — handing the trip to a follower " +
                        "(speed=${measured}km/h acc=${witnessedAccuracyM}m geof=${geofenceId.take(8)}) " +
                        "[DET-A-JUST-DEPARTED-CAR-IS-NOT-NO-SESSION-001]",
                )
                DepartureFollowHandoff(geofenceId, measured, witnessedAccuracyM)
            }

        return processConfirmedDeparture(geofenceId, publishSpot = publishSpot, proof = proof).fold(
            onSuccess = { DepartureCheckOutcome.Processed(followTrip) },
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

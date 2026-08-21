package io.apptolast.paparcar.domain.diagnostics

import io.apptolast.paparcar.domain.detection.GeofenceRegistrationFailure
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingReleaseReason

/**
 * A single diagnostic event in a parking-detection session.
 *
 * **Two purposes (DET-LOG / HANDOFF §4):**
 *  1. **Field diagnosis.** Streamed to a remote store (Firestore, gated by a debug flag) so a
 *     detection issue reproduced on the road can be analysed without Android Studio attached.
 *  2. **Replay fixture.** The ordered list of events for a session IS the trace that drives the
 *     pure `EvaluateParkingDecisionUseCase` tests in Fase D — record the Prague drive, replay it,
 *     assert `Rejected`.
 *
 * Every event is tagged with its [sessionId] (so the remote store can route it to the right
 * session document and a fixture is just the events filtered by that id) and a wall-clock
 * [timestampMs]. [location] carries the best-known GPS context when available — receivers that
 * fire without a fix leave it null.
 *
 * Discriminator fields ([ActivityTransition.activity], [Decision.outcome], …) are plain strings
 * on purpose: the wire format must tolerate new values appearing without breaking deserialization,
 * and analysis is done downstream. The canonical literals are centralised by the instrumentation
 * helpers (DET-LOG-03), not enforced here.
 */
sealed interface DetectionEvent {

    /** Identifies the detection session this event belongs to. */
    val sessionId: String

    /** Wall-clock epoch-ms of the event. */
    val timestampMs: Long

    /** Best-known GPS context at the moment of the event, or null when none is available. */
    val location: GpsPoint?

    /** A detection session opened: which [strategy] owns it, the [vehicleType] profile, and the
     *  [evidence] label behind the arm ("verified_departure" / "self_observed" / …). [DET-SOLID-001] */
    data class SessionStarted(
        override val sessionId: String,
        override val timestampMs: Long,
        val strategy: String,
        val vehicleType: String? = null,
        val evidence: String? = null,
        override val location: GpsPoint? = null,
    ) : DetectionEvent

    /** A detection session closed with a terminal [outcome] (e.g. confirmed / aborted / cancelled). */
    data class SessionEnded(
        override val sessionId: String,
        override val timestampMs: Long,
        val outcome: String,
        override val location: GpsPoint? = null,
    ) : DetectionEvent

    /** An Activity-Recognition transition: [activity] (IN_VEHICLE / STILL …) × [transition] (ENTER / EXIT). */
    data class ActivityTransition(
        override val sessionId: String,
        override val timestampMs: Long,
        val activity: String,
        val transition: String,
        override val location: GpsPoint? = null,
        /** [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] How stale the transition already was when the
         *  session noticed it. AR reports the TRUE transition moment and delivers it up to ~2 min
         *  later, and that gap is not cosmetic: the human-powered verdict arbitrates cycling
         *  against boarding by true time, so a trace that only carries delivery times cannot be
         *  used to check the verdict afterwards. Rides the DTO's existing `enterAgeMs` column,
         *  which already means exactly this for the departure lane — no serializer surface change. */
        val trueTimeAgeMs: Long? = null,
    ) : DetectionEvent

    /** A geofence [event] (e.g. EXIT) or error for [geofenceId]. */
    data class Geofence(
        override val sessionId: String,
        override val timestampMs: Long,
        val event: String,
        val geofenceId: String?,
        override val location: GpsPoint? = null,
    ) : DetectionEvent

    /** A Bluetooth ACL [event] (CONNECTED / DISCONNECTED) for [deviceAddress]. */
    data class Bluetooth(
        override val sessionId: String,
        override val timestampMs: Long,
        val event: String,
        val deviceAddress: String?,
        override val location: GpsPoint? = null,
    ) : DetectionEvent

    /** A raw GPS fix consumed by the coordinator, with the running [stoppedDurationMs] at that
     *  point. The replay input stream for the Fase D pure-decision tests. [DET-LOG-04] */
    data class LocationFix(
        override val sessionId: String,
        override val timestampMs: Long,
        override val location: GpsPoint?,
        val stoppedDurationMs: Long,
    ) : DetectionEvent

    /** A pedestrian-step counter update: current [stepCount] and whether the vehicle is [stopped]. */
    data class Step(
        override val sessionId: String,
        override val timestampMs: Long,
        val stepCount: Int,
        val stopped: Boolean,
        override val location: GpsPoint? = null,
    ) : DetectionEvent

    /** Candidate-phase lifecycle: [action] (OPENED / DISCARDED / CONFIRMED) at the given [phase]. */
    data class Candidate(
        override val sessionId: String,
        override val timestampMs: Long,
        val action: String,
        val phase: String?,
        override val location: GpsPoint? = null,
    ) : DetectionEvent

    /** A confirmation [outcome] with the [pathLabel] that produced it and the output [confidence].
     *  [distanceMeters]/[radiusMeters] carry the guard's numbers when the outcome is a spatial
     *  judgement (egress-mismatch distance, saved zone radius) — the field forensics that used to
     *  live only in logcat. [DET-FROZEN-COUNTER-001] */
    data class Decision(
        override val sessionId: String,
        override val timestampMs: Long,
        val outcome: String,
        val pathLabel: String?,
        val confidence: Float? = null,
        val distanceMeters: Double? = null,
        val radiusMeters: Float? = null,
        override val location: GpsPoint? = null,
        /** [DET-PROMPT-STATES-ITS-REASON-001] WHY the verdict came out this way, when the outcome
         *  string alone cannot say. Six distinct causes degrade a confirm into `CONFIRM_DEGRADED_
         *  PROMPT`, and a trace that only carries the outcome cannot tell them apart — field
         *  2026-08-20 lost a parking spot to a veto nobody could name afterwards. Rides the existing
         *  `reason` column (`HonestClose`, `Released`, `GeofenceRegistration` already use it), so
         *  there is no serializer surface change, and the `outcome` string stays as it always was:
         *  renaming it would break every saved trace that quotes it. */
        val reason: String? = null,
    ) : DetectionEvent

    /** [DET-FROZEN-COUNTER-001] The honest-close ladder ran after a silent abort: its [verdict]
     *  ("closed_approximate_pin" / "closed_approximate_zone" / "silent"), the [reason] behind it
     *  (trip_proven / walk_explains / frozen_counter / mute_counter / …), and every number the
     *  decision weighed. One event per evaluated abort, logged under the aborted session's id —
     *  an approximate pin can never again appear in the field with no remote trace of why. */
    data class HonestClose(
        override val sessionId: String,
        override val timestampMs: Long,
        val verdict: String,
        val reason: String,
        /** Stale pin → abort fix, meters. */
        val distanceMeters: Double? = null,
        /** Step-seal origin → abort fix, meters (displacement the body actually made). */
        val walkDistanceMeters: Double? = null,
        /** Cumulative-counter delta since the stale pin's seal (null = mute). */
        val stepsDelta: Long? = null,
        /** Steps the walk budget demanded before calling the displacement "walked". */
        val requiredSteps: Int? = null,
        /** Steps the aborting session's own detector counted — the liveness witness. */
        val sessionStepEvents: Int? = null,
        /** Max speed (km/h) the aborting session measured. */
        val sessionMaxSpeedKmh: Float? = null,
        /** Radius of the saved approximate zone, when the verdict opened one. */
        val radiusMeters: Float? = null,
        /** Last witnessed position → abort fix, meters — the spatio-temporal coherence the abort
         *  fix was held to (null = no witness available). [DET-UNWITNESSED-DISPLACEMENT-001] */
        val witnessDistanceMeters: Double? = null,
        /** Age (ms) of that witness at the abort moment. */
        val witnessAgeMs: Long? = null,
        override val location: GpsPoint? = null,
    ) : DetectionEvent

    // ── Departure / correction observability [DET-SOLID-001] ─────────────────
    // These fire OUTSIDE a coordinator session; sessionId is the traced entity's id
    // (geofenceId / parkingId) by convention so downstream analysis can join them.

    /** A departure-evidence [verdict] (VERIFIED / UNVERIFIED / CONFIRMED / INCONCLUSIVE / REJECTED)
     *  from [source] ("pre-arm" verifier or the departure "worker" attempt [attempt]). */
    data class DepartureVerdict(
        override val sessionId: String,
        override val timestampMs: Long,
        val verdict: String,
        val source: String,
        val attempt: Int? = null,
        val speedKmh: Float? = null,
        val enterAgeMs: Long? = null,
        override val location: GpsPoint? = null,
    ) : DetectionEvent

    /** A confirmed departure was processed: whether the spot was [published] (private zones
     *  suppress) and the session [sessionCleared]. */
    data class DepartureProcessed(
        override val sessionId: String,
        override val timestampMs: Long,
        val published: Boolean,
        val sessionCleared: Boolean,
        override val location: GpsPoint? = null,
    ) : DetectionEvent

    /**
     * [DET-HANDOFF-NOT-MANUAL-001 §B.3] A provisionally-published spot was WITHDRAWN: the trip the
     * departure had been deduced from ended without ever measuring a drive.
     *
     * The app's own admission that it published something it could not back up — the mirror image
     * of [Reverted], and the numerator of the retracted-rate §B.5 uses to decide which classes of
     * departure deserve to publish as confirmed. [sessionAgeMs] is how long the ghost was live.
     */
    data class SpotRetracted(
        override val sessionId: String,
        override val timestampMs: Long,
        val sessionAgeMs: Long? = null,
        override val location: GpsPoint? = null,
    ) : DetectionEvent

    /** The user reverted a saved park [sessionAgeMs] after it was confirmed — a user-labelled
     *  FALSE POSITIVE, the highest-value signal detection telemetry can produce. */
    data class Reverted(
        override val sessionId: String,
        override val timestampMs: Long,
        val sessionAgeMs: Long? = null,
        override val location: GpsPoint? = null,
    ) : DetectionEvent

    /** The user released a parking session. [reason] = WHY it closed (departure vs deleted record),
     *  [published] = whether the freed spot was reported to the community. WHO is implicit in the
     *  uid-namespaced diagnostics path; WHICH session is [sessionId]; FROM WHERE is [location].
     *  Closes the release-observability gap. [VEH-ACTIVE-FENCE-001] [PARK-DELETE-NO-DECLARE-001] */
    data class Released(
        override val sessionId: String,
        override val timestampMs: Long,
        val published: Boolean,
        val reason: ParkingReleaseReason = ParkingReleaseReason.DEPARTURE_PUBLISHED,
        override val location: GpsPoint? = null,
    ) : DetectionEvent

    /** An orphan geofence (registered but with no active session) was detected and removed. */
    data class OrphanCleaned(
        override val sessionId: String,
        override val timestampMs: Long,
        override val location: GpsPoint? = null,
    ) : DetectionEvent

    /** [DET-SUPERSEDE-001] A running detection session was cancelled and replaced because a new arm
     *  trigger fired [distanceMeters] from its anchor — beyond the fence, so the running session was
     *  a zombie relative to the new park (field 2026-07-12, WA YUKI blocked by a spurious fence ~100 m
     *  away). [ageMs] is how long the superseded session had been running. */
    data class SessionSuperseded(
        override val sessionId: String,
        override val timestampMs: Long,
        val distanceMeters: Double,
        val ageMs: Long? = null,
        override val location: GpsPoint? = null,
    ) : DetectionEvent

    /** Outcome of registering a geofence for an active session — [success] false means the
     *  session⟺geofence invariant is broken until the janitor's restore pass repairs it. */
    data class GeofenceRegistration(
        override val sessionId: String,
        override val timestampMs: Long,
        val success: Boolean,
        val radiusMeters: Float? = null,
        override val location: GpsPoint? = null,
        /** [DET-FENCE-REREGISTER-BY-CAUSE-001 §D] WHO asked for this registration ("cure",
         *  "janitor") — the two lanes re-register the same fences for different reasons and with
         *  different safeguards, and a remote trace that cannot tell them apart cannot say which
         *  one is opening the INSIDE/OUTSIDE blind window. */
        val source: String? = null,
        /** [DET-FENCE-REREGISTER-BY-CAUSE-001 §D] Why it failed, when it did. Null on success. */
        val failure: GeofenceRegistrationFailure? = null,
    ) : DetectionEvent

    /** The safety net woke up [gapMs] after its previous heartbeat — far beyond its cadence —
     *  while a session was ACTIVE: the OEM froze/killed background execution (or extended Doze
     *  starved the scheduler) for that whole window. Per-manufacturer kill telemetry. [OEM-KILL-001] */
    data class BackgroundKillSuspected(
        override val sessionId: String,
        override val timestampMs: Long,
        val gapMs: Long? = null,
        override val location: GpsPoint? = null,
    ) : DetectionEvent

    /** The platform itself reported (Android 16+ `ApplicationStartInfo.wasForceStopped()`) that the
     *  app was force-stopped before the current process start while a session was active. Unlike
     *  [BackgroundKillSuspected] — a heartbeat-gap heuristic that cannot tell an OEM kill from deep
     *  Doze — this is a CONFIRMED kill: force-stop wipes registered geofences, pending intents and
     *  alarms, so a departure during that window was undetectable. [OEM-KILL-001] */
    data class ForceStopConfirmed(
        override val sessionId: String,
        override val timestampMs: Long,
        override val location: GpsPoint? = null,
    ) : DetectionEvent

    /** [DET-RESIDENT-FGS-001] Resident-SENTRY lifecycle telemetry: the service [event]ed the
     *  resident idle watcher ([ENTERED] after a park, with the epilogue reason as [signal]), handed
     *  it over to a live tracking job ([WOKE], with the arm trigger as [signal]), or was found dead
     *  ([KILLED] — the durable residency stamp outlived the process without a deliberate exit).
     *  [gapMs] is the dark window since the last safety-net heartbeat when the kill was detected on
     *  the periodic lane; [residencyMs] is time spent in SENTRY before the transition — the per-OEM
     *  survival metric of the residency experiment. [WAKE_COOLDOWN] marks the walking-abort storm
     *  damper engaging ([signal] carries streak + quiet period) — the field explanation for why
     *  the arm-session cadence suddenly stops. [DET-SENTRY-COOLDOWN-001] sessionId is the watched
     *  parking's geofenceId (out-of-session convention above). */
    data class Sentry(
        override val sessionId: String,
        override val timestampMs: Long,
        val event: String,
        val signal: String? = null,
        val gapMs: Long? = null,
        val residencyMs: Long? = null,
        override val location: GpsPoint? = null,
    ) : DetectionEvent {
        companion object {
            const val ENTERED = "entered"
            const val WOKE = "woke"
            const val KILLED = "killed"
            const val WAKE_COOLDOWN = "wake_cooldown"
        }
    }
}

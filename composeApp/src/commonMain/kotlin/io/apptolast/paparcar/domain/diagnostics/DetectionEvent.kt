package io.apptolast.paparcar.domain.diagnostics

import io.apptolast.paparcar.domain.model.GpsPoint

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

    /** A detection session opened: which [strategy] owns it and the [vehicleType] profile. */
    data class SessionStarted(
        override val sessionId: String,
        override val timestampMs: Long,
        val strategy: String,
        val vehicleType: String? = null,
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

    /** A confirmation [outcome] with the [pathLabel] that produced it and the output [confidence]. */
    data class Decision(
        override val sessionId: String,
        override val timestampMs: Long,
        val outcome: String,
        val pathLabel: String?,
        val confidence: Float? = null,
        override val location: GpsPoint? = null,
    ) : DetectionEvent
}

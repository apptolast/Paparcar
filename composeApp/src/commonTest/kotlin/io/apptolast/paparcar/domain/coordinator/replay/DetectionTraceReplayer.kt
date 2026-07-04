package io.apptolast.paparcar.domain.coordinator.replay

import io.apptolast.paparcar.domain.model.GpsPoint

/**
 * [DET-SOLID-001][C4] A recorded detection trace, replayable against the REAL
 * `CoordinatorParkingDetector`. Fixtures are generated from the Firestore diagnostics events
 * (`diagnostics/{uid}/sessions/{id}/events`, types LOCATION_FIX / STEP) via
 * `tools/trace2fixture/trace2fixture.py`, or hand-authored from a session dump.
 *
 * **Every field bug becomes a permanent fixture**: record the trace, replay it, assert the
 * corrected outcome — the regression can never silently return.
 */
data class TraceEvent(
    val tMs: Long,
    val kind: Kind,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
) {
    enum class Kind { FIX, STEP }
}

/** Replays [events] in timestamp order: FIX → the location flow, STEP → the step source, with
 *  the injected clock advanced to each event's time BEFORE it is delivered. */
class DetectionTraceReplayer(private val events: List<TraceEvent>) {

    /** Current virtual time — wire this into the detector's `clock` lambda. */
    var nowMs: Long = events.firstOrNull()?.tMs ?: 0L
        private set

    suspend fun replay(
        emitFix: suspend (GpsPoint) -> Unit,
        emitStep: suspend () -> Unit,
    ) {
        events.sortedBy { it.tMs }.forEach { event ->
            nowMs = event.tMs
            when (event.kind) {
                TraceEvent.Kind.FIX -> emitFix(
                    GpsPoint(
                        latitude = event.lat,
                        longitude = event.lon,
                        accuracy = event.accuracy,
                        timestamp = event.tMs,
                        speed = event.speed,
                    )
                )
                TraceEvent.Kind.STEP -> emitStep()
            }
        }
    }
}

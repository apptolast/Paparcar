package io.apptolast.paparcar.domain.coordinator.ingestion

import io.apptolast.paparcar.domain.model.GpsPoint

/**
 * [DET-SOLID-001][C4][IOS-F0-02] A recorded detection trace, replayable against the REAL
 * `CoordinatorParkingDetector`. Promoted from commonTest: the trace is now a first-class
 * domain model with a dual role —
 *
 *  - **Regression harness** (commonTest): fixtures are generated from the Firestore diagnostics
 *    events (`diagnostics/{uid}/sessions/{id}/events`, types LOCATION_FIX / STEP) via
 *    `tools/trace2fixture/trace2fixture.py`, or hand-authored from a session dump.
 *    **Every field bug becomes a permanent fixture**: record the trace, replay it, assert the
 *    corrected outcome — the regression can never silently return.
 *  - **Wake-and-query ingestion** (iOS port, `docs/IOS-IMPLEMENTATION-PLAN.md` §4): on each OS
 *    wake the orchestrator composes a trace from historical queries (motion activity, pedometer,
 *    side-records) and batch-feeds it through the SAME evaluators — replay parity with Android
 *    by construction.
 *
 * Events with equal [tMs] are delivered in LIST order (the sort is stable) — author traces so
 * that an [Kind.ACTIVITY] event meant to precede a same-timestamp fix appears before it.
 */
data class TraceEvent(
    val tMs: Long,
    val kind: Kind,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    /** Only meaningful when [kind] == [Kind.ACTIVITY]. */
    val activity: Activity? = null,
) {
    enum class Kind { FIX, STEP, ACTIVITY }

    /**
     * AR transitions as the field delivers them — the IN_VEHICLE lane of
     * `ActivityRecognitionManager` [DET-G-01][DET-AR-FIRST-001].
     *
     * [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001] The AR lane is part of the trace, not scenery:
     * [VEHICLE_EXIT] is what routes the confidence scorer into its Medium-capped fast path, and
     * [BICYCLE_ENTER] is the stamp that can veto a whole session as human-powered. A replay that
     * drops them cannot reproduce either outcome.
     */
    enum class Activity { VEHICLE_ENTER, VEHICLE_EXIT, BICYCLE_ENTER }
}

/**
 * Replays [events] in timestamp order: FIX → the location flow, STEP → the step source,
 * ACTIVITY → the AR lane — with the injected clock advanced to each event's time BEFORE it
 * is delivered.
 */
class DetectionTraceIngestion(private val events: List<TraceEvent>) {

    /** Current virtual time — wire this into the detector's `clock` lambda. Starts at the
     *  EARLIEST event (not the first listed) so the clock never runs backwards when a trace
     *  is composed out of order (e.g. a prepended ACTIVITY event). */
    var nowMs: Long = events.minOfOrNull { it.tMs } ?: 0L
        private set

    suspend fun replay(
        emitFix: suspend (GpsPoint) -> Unit,
        emitStep: suspend () -> Unit,
        // The activity's own time travels with it: a stamp like BICYCLE_ENTER is judged against
        // when it was OBSERVED, not against whatever the caller's clock reads on delivery.
        emitActivity: suspend (activity: TraceEvent.Activity, trueTimeMs: Long) -> Unit = { _, _ -> },
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
                TraceEvent.Kind.ACTIVITY -> event.activity?.let { emitActivity(it, event.tMs) }
            }
        }
    }
}

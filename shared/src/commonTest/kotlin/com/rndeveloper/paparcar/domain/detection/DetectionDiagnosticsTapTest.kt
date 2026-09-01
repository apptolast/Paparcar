package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [09 §7] The single emitter.
 *
 * Two properties, and both have an incident behind them: an event must never land under a session it
 * does not belong to, and a one-per-session marker must never leak into the next session.
 */
class DetectionDiagnosticsTapTest {

    private class RecordingLogger : DetectionEventLogger {
        val events = mutableListOf<DetectionEvent>()
        override suspend fun log(event: DetectionEvent) { events += event }
    }

    private fun decision(sid: String) = DetectionEvent.Decision(sid, 1_000L, outcome = "X", pathLabel = null)

    @Test
    fun should_drop_events_that_belong_to_no_session() = runTest {
        val logger = RecordingLogger()
        val tap = DetectionDiagnosticsTap(logger)
        tap.emit(::decision)
        assertTrue(logger.events.isEmpty(), "between sessions there is nothing to attribute an event to")
    }

    @Test
    fun should_stamp_every_event_with_the_open_session() = runTest {
        val logger = RecordingLogger()
        val tap = DetectionDiagnosticsTap(logger)
        tap.open("session-1")
        tap.emit(::decision)
        assertEquals(1, logger.events.size)
        assertEquals("session-1", (logger.events.single() as DetectionEvent.Decision).sessionId)
    }

    /** A closed session stops speaking. Its epilogue is over; anything later belongs to nobody. */
    @Test
    fun should_stop_emitting_once_the_session_closes() = runTest {
        val logger = RecordingLogger()
        val tap = DetectionDiagnosticsTap(logger)
        tap.open("session-1")
        tap.close()
        tap.emit(::decision)
        assertTrue(logger.events.isEmpty())
    }

    /**
     * [DET-MOTOR-PROOF-001] A one-per-session marker fires once and then stays quiet — the whole
     * reason it is a LATCH and not an equality on the counter.
     */
    @Test
    fun should_latch_a_marker_once_per_session() {
        val tap = DetectionDiagnosticsTap(RecordingLogger())
        tap.open("session-1")
        assertTrue(tap.latchOnce(DetectionDiagnosticsTap.Latch.PEDAL_CADENCE))
        assertFalse(tap.latchOnce(DetectionDiagnosticsTap.Latch.PEDAL_CADENCE))
    }

    /**
     * …and it starts clean in the NEXT session. As a loose `var` in the loop this was true only
     * because the variable was declared inside `invoke`; as owned state it has to be said, and the
     * saying is what stops the next person moving it up a scope.
     */
    @Test
    fun should_forget_its_markers_when_a_new_session_opens() {
        val tap = DetectionDiagnosticsTap(RecordingLogger())
        tap.open("session-1")
        tap.latchOnce(DetectionDiagnosticsTap.Latch.PEDAL_CADENCE)
        tap.open("session-2")
        assertTrue(
            tap.latchOnce(DetectionDiagnosticsTap.Latch.PEDAL_CADENCE),
            "a marker from a previous drive must not silence this one",
        )
    }

    // ── Rising edge [DET-EDGE-MARKERS-TO-THE-TAP-001] ─────────────────────────
    // The form `latchOnce` cannot express: one line per RISE, re-armed on fall — a departure per
    // drive. A latch here would have swallowed the second exit of the session.

    @Test
    fun should_claimTheEdgeOnceOnRiseAndStayQuietWhileHigh_when_theSignalHolds() {
        val tap = DetectionDiagnosticsTap(RecordingLogger())
        tap.open("session-1")
        assertTrue(tap.risingEdge(DetectionDiagnosticsTap.Edge.VEHICLE_EXIT, high = true))
        assertFalse(tap.risingEdge(DetectionDiagnosticsTap.Edge.VEHICLE_EXIT, high = true))
    }

    @Test
    fun should_reArmAndClaimAgain_when_theSignalFallsAndRises() {
        val tap = DetectionDiagnosticsTap(RecordingLogger())
        tap.open("session-1")
        assertTrue(tap.risingEdge(DetectionDiagnosticsTap.Edge.VEHICLE_EXIT, high = true))
        assertFalse(tap.risingEdge(DetectionDiagnosticsTap.Edge.VEHICLE_EXIT, high = false))
        assertTrue(
            tap.risingEdge(DetectionDiagnosticsTap.Edge.VEHICLE_EXIT, high = true),
            "a hint that cleared (driving away) and set again is a SECOND exit and must log again",
        )
    }

    @Test
    fun should_forgetARisenEdge_when_aNewSessionOpens() {
        val tap = DetectionDiagnosticsTap(RecordingLogger())
        tap.open("session-1")
        tap.risingEdge(DetectionDiagnosticsTap.Edge.VEHICLE_EXIT, high = true)
        tap.open("session-2")
        assertTrue(tap.risingEdge(DetectionDiagnosticsTap.Edge.VEHICLE_EXIT, high = true))
    }

    // ── Value dedup [DET-EDGE-MARKERS-TO-THE-TAP-001] ─────────────────────────
    // The other form `latchOnce` cannot express: re-emit when the stamp CHANGES, including from
    // the empty seed — an INHERITED AR stamp must log on the first observation with its true age,
    // and a second boarding must re-emit.

    @Test
    fun should_claimTheFirstObservedStamp_when_theSeedIsEmpty() {
        val tap = DetectionDiagnosticsTap(RecordingLogger())
        tap.open("session-1")
        assertTrue(
            tap.valueChanged(DetectionDiagnosticsTap.ValueMark.BICYCLE_RIDE_STAMP, 5_000L),
            "a stamp inherited from before the session must log on the first fix",
        )
        assertFalse(tap.valueChanged(DetectionDiagnosticsTap.ValueMark.BICYCLE_RIDE_STAMP, 5_000L))
    }

    @Test
    fun should_claimAgain_when_theStampChanges() {
        val tap = DetectionDiagnosticsTap(RecordingLogger())
        tap.open("session-1")
        tap.valueChanged(DetectionDiagnosticsTap.ValueMark.VEHICLE_RIDE_STAMP, 5_000L)
        assertTrue(
            tap.valueChanged(DetectionDiagnosticsTap.ValueMark.VEHICLE_RIDE_STAMP, 9_000L),
            "a second boarding is a NEW stamp and must re-emit — latchOnce would swallow it",
        )
    }

    @Test
    fun should_forgetClaimedStamps_when_aNewSessionOpens() {
        val tap = DetectionDiagnosticsTap(RecordingLogger())
        tap.open("session-1")
        tap.valueChanged(DetectionDiagnosticsTap.ValueMark.BICYCLE_RIDE_STAMP, 5_000L)
        tap.open("session-2")
        assertTrue(tap.valueChanged(DetectionDiagnosticsTap.ValueMark.BICYCLE_RIDE_STAMP, 5_000L))
    }
}

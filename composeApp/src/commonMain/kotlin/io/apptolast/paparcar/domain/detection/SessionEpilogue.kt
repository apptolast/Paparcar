package io.apptolast.paparcar.domain.detection

import io.apptolast.paparcar.domain.detection.physics.SessionOutcome
import io.apptolast.paparcar.domain.detection.state.DetectionSessionState
import io.apptolast.paparcar.domain.model.GpsPoint

/**
 * [DET-HONEST-CLOSE-001][DET-FROZEN-COUNTER-001] **What a finished session leaves behind**, captured
 * at the last instant it still exists.
 *
 * The detection service does not stop working when `invoke` returns: on the two silent aborts it
 * runs the honest-close ladder, which needs to know where the session died, what it measured and
 * under which diagnostics id. Those five facts used to be five `@Volatile` fields set one by one in
 * the `finally`, each with its own comment explaining that it must be read BEFORE `reset()` wipes
 * the state.
 *
 * Five fields with the same lifetime, the same reason and the same deadline are one value. Written
 * once, in one statement, at the one moment the session state is both complete and still alive —
 * which is also the moment the plan has owed since P1.11 and P2.2, because it is what finally lets
 * `outcome` and `completed` live INSIDE the state instead of beside it.
 *
 * ⚠️ **A superseded session never writes one.** Its `finally` must not touch anything the successor
 * owns, and that includes this: the epilogue on file belongs to the last session that actually owned
 * the singleton. [DET-AUDIT-002 T8]
 *
 * @property outcome The terminal label, and the same one the `SessionEnded` event carried.
 * @property lastFix Position at the ending — the last processed fix, or the stop anchor as fallback.
 *   Null when no fix was ever seen. The honest-close ladder's candidate new spot.
 * @property sessionId Diagnostics id of the session, so post-session actors log under its trace.
 * @property stepEvents Steps the session's own detector counted. In the two abort outcomes the
 *   ladder runs on, the count is never reset (no driving ever happened), so the terminal value IS
 *   the session's full pedestrian testimony — and the cumulative counter's liveness witness.
 * @property maxSpeedMps Max GPS speed the session PROVED. Measured movement outranks the step
 *   inference in the ladder.
 */
data class SessionEpilogue(
    val outcome: SessionOutcome = SessionOutcome.Ended,
    val lastFix: GpsPoint? = null,
    val sessionId: String? = null,
    val stepEvents: Int = 0,
    val maxSpeedMps: Float = 0f,
) {
    companion object {
        /**
         * Read a session's ending off the state it is about to lose.
         *
         * @param sessionId the diagnostics id, which the state does not carry — it is the loop's
         *   claim on the singleton, not a fact about the trip.
         */
        fun of(state: DetectionSessionState, sessionId: String?): SessionEpilogue = SessionEpilogue(
            outcome = state.session.outcome,
            // `previousFix` is the last fix processed; the anchor is the stop fallback.
            lastFix = state.session.previousFix ?: state.anchorTrust.anchor,
            sessionId = sessionId,
            stepEvents = state.egress.stepCount,
            maxSpeedMps = state.drive.provenMaxSpeedMps,
        )
    }
}

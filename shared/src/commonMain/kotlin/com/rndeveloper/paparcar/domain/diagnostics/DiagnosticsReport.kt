package com.rndeveloper.paparcar.domain.diagnostics

/**
 * A user-initiated problem report: what the user says went wrong, plus device identity and a gzip
 * snapshot of the recent local detection log ([LocalDiagnosticsLog]). Keyed by the uid so a field
 * bug can be matched to its trip without a cable. [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
 */
data class DiagnosticsReport(
    val userId: String,
    val createdAtMs: Long,
    val deviceModel: String,
    val appVersion: String,
    val osVersion: String,
    /**
     * What the user wrote, already normalised (trimmed, capped at [MAX_MESSAGE_CHARS]). Empty when
     * they sent the evidence without a word — allowed on purpose, see
     * [com.rndeveloper.paparcar.domain.usecase.diagnostics.SendDiagnosticsReportUseCase].
     * [SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001]
     */
    val message: String,
    /** Gzip of the recent parkdiag log, or null on platforms without a local log (iOS). */
    val logGzip: ByteArray?,
) {
    companion object {
        /**
         * Ceiling of [message], in characters. Domain policy, not a UI detail: the text field caps
         * typing AND the use case re-applies it, so a report built by any other path (iOS, a
         * worker, a test) still cannot ship an unbounded string into a Firestore header doc.
         *
         * 500 fits the three things that make a report actionable — what you expected, what
         * happened, roughly when — and is short enough that nobody pastes a log in here: the log
         * already travels in its own chunked lane.
         * [SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001]
         */
        const val MAX_MESSAGE_CHARS = 500
    }
}

/** Uploads a [DiagnosticsReport] to the backend. Implemented in data (Firestore, chunked). */
interface DiagnosticsReportUploader {
    suspend fun upload(report: DiagnosticsReport): Result<Unit>
}

/**
 * Platform snapshot of the local detection log (`parkdiag.log`). Null when there is nothing to
 * ship (no file yet, or platform without the sink). Android-only today; not bound on iOS.
 */
interface LocalDiagnosticsLog {
    suspend fun snapshotGzip(): ByteArray?
}

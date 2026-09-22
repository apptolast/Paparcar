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
    /** Gzip del `parkdiag` reciente, o null cuando no hay nada que mandar (todavía sin fichero, o
     *  el snapshot falló). ⛔ Ya NO significa «iOS»: desde
     *  [IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001] las dos plataformas escriben su log. */
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
 * ship (no file yet, or the snapshot failed).
 *
 * Implementado en las DOS plataformas desde [IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001]
 * (`AndroidLocalDiagnosticsLog` / `IosLocalDiagnosticsLog`), sobre el mismo fichero y las mismas
 * dos generaciones — ver [ParkdiagLogFiles]. Sigue resolviéndose con `getOrNull()` en el grafo:
 * una plataforma futura sin sumidero no debe tumbar el informe, sólo viajar sin log.
 */
interface LocalDiagnosticsLog {
    suspend fun snapshotGzip(): ByteArray?
}

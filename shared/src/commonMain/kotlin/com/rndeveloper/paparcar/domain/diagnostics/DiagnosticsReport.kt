package com.rndeveloper.paparcar.domain.diagnostics

/**
 * A user-initiated problem report: device identity plus a gzip snapshot of the recent local
 * detection log ([LocalDiagnosticsLog]). Keyed by the uid so a field bug can be matched to its
 * trip without a cable. [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
 */
data class DiagnosticsReport(
    val userId: String,
    val createdAtMs: Long,
    val deviceModel: String,
    val appVersion: String,
    val osVersion: String,
    /** Gzip of the recent parkdiag log, or null on platforms without a local log (iOS). */
    val logGzip: ByteArray?,
)

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

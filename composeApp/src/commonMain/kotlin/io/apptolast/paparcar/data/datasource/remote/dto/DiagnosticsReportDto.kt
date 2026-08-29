package io.apptolast.paparcar.data.datasource.remote.dto

import kotlinx.serialization.Serializable

/**
 * Header doc of a user-initiated problem report at
 * `diagnostics_reports/{userId}/reports/{createdAtMs}`. [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
 * The shipped log travels in the `chunks` subcollection ([ReportChunkDto]); [chunkCount] and
 * [gzipBytes] let the reader verify the reassembly is complete.
 */
@Serializable
data class DiagnosticsReportDto(
    val userId: String,
    val createdAt: Long,
    val deviceModel: String,
    val appVersion: String,
    val osVersion: String,
    /** Number of chunk docs written; 0 = report without a local log (fresh install / iOS). */
    val chunkCount: Int,
    /** Total gzip size in bytes across all chunks, before base64. */
    val gzipBytes: Int,
)

/** One base64 piece of the gzipped log, at `…/chunks/{index}`. */
@Serializable
data class ReportChunkDto(
    val index: Int,
    val data: String,
)

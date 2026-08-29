package com.rndeveloper.paparcar.data.datasource.remote

import dev.gitlive.firebase.firestore.FirebaseFirestore
import com.rndeveloper.paparcar.data.datasource.remote.DiagnosticsFirestoreSchema.COLLECTION_REPORTS
import com.rndeveloper.paparcar.data.datasource.remote.DiagnosticsFirestoreSchema.COLLECTION_REPORTS_ROOT
import com.rndeveloper.paparcar.data.datasource.remote.DiagnosticsFirestoreSchema.COLLECTION_REPORT_CHUNKS
import com.rndeveloper.paparcar.data.datasource.remote.dto.DiagnosticsReportDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.ReportChunkDto
import com.rndeveloper.paparcar.domain.diagnostics.DiagnosticsReport
import com.rndeveloper.paparcar.domain.diagnostics.DiagnosticsReportUploader
import kotlin.io.encoding.Base64

/**
 * Ships a problem report to `diagnostics_reports/{uid}/reports/{createdAtMs}` with the gzipped
 * local log split into base64 chunk docs. Firestore (not Storage) on purpose: pap-26 has no
 * Storage bucket and provisioning one requires the Blaze plan, while chunked docs reuse the
 * existing SDK, the per-owner rules model and the account-deletion sweep.
 * [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
 *
 * The header is written FIRST: if a chunk write dies midway, the header's [DiagnosticsReportDto.chunkCount]
 * says what was expected, so a partial upload is visible instead of silently passing for complete.
 */
class FirestoreDiagnosticsReportUploader(
    private val firestore: FirebaseFirestore,
) : DiagnosticsReportUploader {

    override suspend fun upload(report: DiagnosticsReport): Result<Unit> = runCatching {
        val gzip = report.logGzip ?: ByteArray(0)
        val chunkCount = (gzip.size + CHUNK_RAW_BYTES - 1) / CHUNK_RAW_BYTES

        val reportDoc = firestore.collection(COLLECTION_REPORTS_ROOT)
            .document(report.userId)
            .collection(COLLECTION_REPORTS)
            .document(report.createdAtMs.toString())

        reportDoc.set(
            DiagnosticsReportDto(
                userId = report.userId,
                createdAt = report.createdAtMs,
                deviceModel = report.deviceModel,
                appVersion = report.appVersion,
                osVersion = report.osVersion,
                chunkCount = chunkCount,
                gzipBytes = gzip.size,
            ),
        )
        for (index in 0 until chunkCount) {
            val from = index * CHUNK_RAW_BYTES
            val until = minOf(from + CHUNK_RAW_BYTES, gzip.size)
            reportDoc.collection(COLLECTION_REPORT_CHUNKS)
                .document(index.toString())
                .set(ReportChunkDto(index = index, data = Base64.encode(gzip, from, until)))
        }
    }

    private companion object {
        /** Raw gzip bytes per chunk doc: base64 inflates ×4/3 → ~800 KB per doc, safely under
         *  Firestore's 1 MiB document ceiling. */
        const val CHUNK_RAW_BYTES = 600_000
    }
}

package io.apptolast.paparcar.logging

import android.content.Context
import io.apptolast.paparcar.domain.diagnostics.LocalDiagnosticsLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Snapshot of the recent [FileAntilog] history for a problem report: the newest rotation
 * (`parkdiag.log.1`) plus the active file, concatenated oldest-first so the reader gets one
 * chronological stream. Two generations ≈ up to 10 MB raw (≈ days of PARKDIAG traffic) — enough
 * to cover the trip the user is complaining about without shipping the whole 30 MB archive.
 * Gzip typically compresses this log ~10:1, so the upload is ~1 MB worst case.
 * [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
 */
class AndroidLocalDiagnosticsLog(context: Context) : LocalDiagnosticsLog {

    private val dir: File = context.filesDir

    override suspend fun snapshotGzip(): ByteArray? = withContext(Dispatchers.IO) {
        val generations = listOf(
            File(dir, "${FileAntilog.BASE_NAME}.1"),
            File(dir, FileAntilog.BASE_NAME),
        ).filter { it.exists() && it.length() > 0L }
        if (generations.isEmpty()) return@withContext null

        runCatching {
            val out = ByteArrayOutputStream()
            GZIPOutputStream(out).use { gz ->
                generations.forEach { file ->
                    file.inputStream().use { it.copyTo(gz) }
                }
            }
            out.toByteArray()
        }.getOrNull()
    }
}

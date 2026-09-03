@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.diagnostics

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.diagnostics.DeviceInfoProvider
import com.rndeveloper.paparcar.domain.diagnostics.DiagnosticsReport
import com.rndeveloper.paparcar.domain.diagnostics.DiagnosticsReportUploader
import com.rndeveloper.paparcar.domain.diagnostics.LocalDiagnosticsLog

/**
 * "Report a problem" from Settings: snapshots the recent local detection log and uploads it,
 * stamped with the uid and device identity, so a field bug can be matched to its trip without a
 * cable. A missing local log (fresh install, iOS) still uploads the report header — the complaint
 * itself is signal, and it says which uid to remote-enable next. [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
 *
 * The user's own words travel in the SAME header doc as the device metadata, never inside the
 * gzipped log: the header is what a reader lists first, so the complaint has to be readable without
 * reassembling a megabyte of chunks. It is also what turns hours of trace into a bounded search —
 * "the pin landed on the previous street, around 9:15" says which session to open.
 * [SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001]
 */
class SendDiagnosticsReportUseCase(
    private val authRepository: AuthRepository,
    private val uploader: DiagnosticsReportUploader,
    private val localLog: LocalDiagnosticsLog?,
    private val deviceInfo: DeviceInfoProvider,
) {
    /**
     * @param message what the user typed. Blank is allowed and does NOT block the upload: someone
     *  who cannot put the failure into words still ships the evidence, which is the part that
     *  cannot be recovered later. Normalised here — trimmed and capped at
     *  [DiagnosticsReport.MAX_MESSAGE_CHARS] — so the cap holds even when the caller is not the
     *  dialog that already limits typing.
     */
    suspend operator fun invoke(message: String = ""): Result<Unit> = runCatching {
        val userId = authRepository.getCurrentSession()?.userId
            ?: error("No active session")

        val report = DiagnosticsReport(
            userId = userId,
            createdAtMs = kotlin.time.Clock.System.now().toEpochMilliseconds(),
            deviceModel = deviceInfo.deviceModel,
            appVersion = deviceInfo.appVersion,
            osVersion = deviceInfo.osVersion,
            message = message.trim().take(DiagnosticsReport.MAX_MESSAGE_CHARS),
            logGzip = localLog?.snapshotGzip(),
        )
        uploader.upload(report).getOrThrow()
    }
}

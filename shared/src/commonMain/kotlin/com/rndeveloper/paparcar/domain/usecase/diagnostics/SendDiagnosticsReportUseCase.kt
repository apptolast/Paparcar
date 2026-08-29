@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.diagnostics

import com.apptolast.customlogin.domain.AuthRepository
import com.rndeveloper.paparcar.domain.diagnostics.DeviceInfoProvider
import com.rndeveloper.paparcar.domain.diagnostics.DiagnosticsReport
import com.rndeveloper.paparcar.domain.diagnostics.DiagnosticsReportUploader
import com.rndeveloper.paparcar.domain.diagnostics.LocalDiagnosticsLog

/**
 * "Report a problem" from Settings: snapshots the recent local detection log and uploads it,
 * stamped with the uid and device identity, so a field bug can be matched to its trip without a
 * cable. A missing local log (fresh install, iOS) still uploads the report header — the complaint
 * itself is signal, and it says which uid to remote-enable next. [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
 */
class SendDiagnosticsReportUseCase(
    private val authRepository: AuthRepository,
    private val uploader: DiagnosticsReportUploader,
    private val localLog: LocalDiagnosticsLog?,
    private val deviceInfo: DeviceInfoProvider,
) {
    suspend operator fun invoke(): Result<Unit> = runCatching {
        val userId = authRepository.getCurrentSession()?.userId
            ?: error("No active session")

        val report = DiagnosticsReport(
            userId = userId,
            createdAtMs = kotlin.time.Clock.System.now().toEpochMilliseconds(),
            deviceModel = deviceInfo.deviceModel,
            appVersion = deviceInfo.appVersion,
            osVersion = deviceInfo.osVersion,
            logGzip = localLog?.snapshotGzip(),
        )
        uploader.upload(report).getOrThrow()
    }
}

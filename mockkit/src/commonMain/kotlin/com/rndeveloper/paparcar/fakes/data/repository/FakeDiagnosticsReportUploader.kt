package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.diagnostics.DiagnosticsReport
import com.rndeveloper.paparcar.domain.diagnostics.DiagnosticsReportUploader

class FakeDiagnosticsReportUploader : DiagnosticsReportUploader {
    override suspend fun upload(report: DiagnosticsReport): Result<Unit> = Result.success(Unit)
}

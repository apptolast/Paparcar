package io.apptolast.paparcar.fakes.data.repository

import io.apptolast.paparcar.domain.diagnostics.DiagnosticsReport
import io.apptolast.paparcar.domain.diagnostics.DiagnosticsReportUploader

class FakeDiagnosticsReportUploader : DiagnosticsReportUploader {
    override suspend fun upload(report: DiagnosticsReport): Result<Unit> = Result.success(Unit)
}

package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.diagnostics.DiagnosticsReport
import com.rndeveloper.paparcar.domain.diagnostics.DiagnosticsReportUploader
import com.rndeveloper.paparcar.domain.diagnostics.LocalDiagnosticsLog

class FakeDiagnosticsReportUploader : DiagnosticsReportUploader {

    var uploadCallCount = 0
        private set
    var lastReport: DiagnosticsReport? = null
        private set
    var uploadResult: Result<Unit> = Result.success(Unit)

    override suspend fun upload(report: DiagnosticsReport): Result<Unit> {
        uploadCallCount++
        lastReport = report
        return uploadResult
    }
}

/** @param snapshot what the device log yields; null models "no log yet" / a platform without one. */
class FakeLocalDiagnosticsLog(
    private val snapshot: ByteArray? = byteArrayOf(1, 2, 3),
) : LocalDiagnosticsLog {

    var snapshotCallCount = 0
        private set

    override suspend fun snapshotGzip(): ByteArray? {
        snapshotCallCount++
        return snapshot
    }
}

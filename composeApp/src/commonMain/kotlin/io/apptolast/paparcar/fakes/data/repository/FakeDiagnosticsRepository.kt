package io.apptolast.paparcar.fakes.data.repository

import io.apptolast.paparcar.domain.repository.DiagnosticsRepository

class FakeDiagnosticsRepository : DiagnosticsRepository {
    override suspend fun deleteAllData(userId: String): Result<Unit> = Result.success(Unit)
}

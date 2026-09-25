package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.repository.DiagnosticsRepository

class FakeDiagnosticsRepository : DiagnosticsRepository {
    override suspend fun deleteAllData(userId: String): Result<Unit> = Result.success(Unit)
}

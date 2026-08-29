package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.repository.DiagnosticsRepository

class FakeDiagnosticsRepository : DiagnosticsRepository {

    var deleteAllDataCallCount = 0
        private set
    var deleteAllUserId: String? = null
        private set
    var deleteAllDataResult: Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(userId: String): Result<Unit> {
        deleteAllDataCallCount++
        deleteAllUserId = userId
        return deleteAllDataResult
    }
}

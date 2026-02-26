package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.UserParkingSession
import io.apptolast.paparcar.domain.repository.UserParkingRepository

class SaveUserParkingUseCase(
    private val repository: UserParkingRepository,
) {
    suspend operator fun invoke(session: UserParkingSession): Result<Unit> =
        repository.saveSession(session)
}

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkingSession
import io.apptolast.paparcar.domain.repository.UserParkingRepository

class GetAllParkingSessionsUseCase(
    private val repository: UserParkingRepository,
) {
    suspend operator fun invoke(): List<ParkingSession> = repository.getAllSessions()
}

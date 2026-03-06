package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.repository.UserParkingRepository

class GetAllUserParkingsUseCase(
    private val repository: UserParkingRepository,
) {
    suspend operator fun invoke(): List<UserParking> = repository.getAllSessions()
}

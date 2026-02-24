package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.repository.UserParkingRepository

class ClearUserParkingUseCase(
    private val repository: UserParkingRepository,
) {
    suspend operator fun invoke(): Result<Unit> =
        repository.clearActive()
}

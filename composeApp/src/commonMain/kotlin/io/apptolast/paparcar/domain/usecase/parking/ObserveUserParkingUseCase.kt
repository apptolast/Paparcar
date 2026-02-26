package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.UserParkingSession
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import kotlinx.coroutines.flow.Flow

class ObserveUserParkingUseCase(
    private val repository: UserParkingRepository,
) {
    operator fun invoke(): Flow<UserParkingSession?> = repository.observeActiveSession()
}

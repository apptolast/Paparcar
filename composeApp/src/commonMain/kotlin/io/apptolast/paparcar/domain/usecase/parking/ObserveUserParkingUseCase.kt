package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.ParkingSession
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import kotlinx.coroutines.flow.Flow

class ObserveUserParkingUseCase(
    private val repository: UserParkingRepository,
) {
    operator fun invoke(): Flow<ParkingSession?> = repository.observeActiveSession()
}

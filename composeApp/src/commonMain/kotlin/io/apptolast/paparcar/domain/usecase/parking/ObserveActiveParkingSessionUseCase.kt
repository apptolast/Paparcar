package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observes the currently active parking session as a reactive stream.
 * Emits the session when it starts and null when it ends.
 */
class ObserveActiveParkingSessionUseCase(
    private val userParkingRepository: UserParkingRepository,
) {
    operator fun invoke(): Flow<UserParking?> =
        userParkingRepository.observeActiveSession()
}

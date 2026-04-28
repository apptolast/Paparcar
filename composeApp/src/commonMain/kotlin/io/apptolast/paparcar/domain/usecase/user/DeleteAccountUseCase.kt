package io.apptolast.paparcar.domain.usecase.user

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.domain.repository.SpotRepository
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.UserProfileRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository

class DeleteAccountUseCase(
    private val authRepository: AuthRepository,
    private val userParkingRepository: UserParkingRepository,
    private val vehicleRepository: VehicleRepository,
    private val userProfileRepository: UserProfileRepository,
    private val spotRepository: SpotRepository,
) {
    suspend operator fun invoke(): Result<Unit> = runCatching {
        val userId = authRepository.getCurrentSession()?.userId
            ?: error("No active session")

        userParkingRepository.deleteAllData(userId).getOrThrow()
        vehicleRepository.deleteAllData(userId).getOrThrow()
        spotRepository.clearCache().getOrThrow()
        userProfileRepository.deleteAllData(userId).getOrThrow()
        authRepository.deleteAccount().getOrThrow()
    }
}

package io.apptolast.paparcar.domain.usecase.user

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.domain.model.UserProfile
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.UserProfileRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger

class GetOrCreateUserProfileUseCase(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository,
    private val userParkingRepository: UserParkingRepository,
) {
    suspend operator fun invoke(): Result<UserProfile> = runCatching {
        val session = authRepository.getCurrentSession()
            ?: error("no authenticated session")

        userProfileRepository.getOrCreateProfile(session).getOrThrow().also {
            // Populate Room from Firestore if this is a new device / fresh install
            userParkingRepository.syncParkingHistoryFromRemote(session.userId)
                .onFailure { e -> PaparcarLogger.w(TAG, "parking history sync failed", e) }
        }
    }

    private companion object {
        const val TAG = "GetOrCreateUserProfileUseCase"
    }
}

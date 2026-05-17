package io.apptolast.paparcar.domain.usecase.user

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.domain.model.UserProfile
import io.apptolast.paparcar.domain.repository.UserProfileRepository

/**
 * Returns the existing user profile from Firestore or creates one from the active
 * session. Pure: no Room sync, no side effects beyond the profile doc itself —
 * user-scoped table sync lives in [BootstrapUserDataUseCase].
 */
class GetOrCreateUserProfileUseCase(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository,
) {
    suspend operator fun invoke(): Result<UserProfile> = runCatching {
        val session = authRepository.getCurrentSession()
            ?: error("no authenticated session")
        userProfileRepository.getOrCreateProfile(session).getOrThrow()
    }
}

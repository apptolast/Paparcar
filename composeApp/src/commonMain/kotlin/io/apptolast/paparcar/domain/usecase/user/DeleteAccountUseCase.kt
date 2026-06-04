package io.apptolast.paparcar.domain.usecase.user

import com.apptolast.customlogin.domain.AuthRepository
import io.apptolast.paparcar.domain.repository.SpotRepository
import io.apptolast.paparcar.domain.repository.UserScopedRepository

/**
 * Cascading account deletion. Clears all user-scoped repositories in order,
 * then wipes the community spot cache, then deletes the auth account.
 *
 * [userScopedRepos] is an ordered list — currently wired in DomainModule as:
 * UserParkingRepository, VehicleRepository, UserProfileRepository, ZoneRepository.
 * Order matters: [getOrThrow] is fail-fast, so a failure stops the remaining repos.
 * Adding a new user-scoped repository requires both implementing [UserScopedRepository]
 * AND adding it to the list in DomainModule. [GDPR right-to-erasure]
 */
class DeleteAccountUseCase(
    private val authRepository: AuthRepository,
    private val userScopedRepos: List<UserScopedRepository>,
    private val spotRepository: SpotRepository,
) {
    suspend operator fun invoke(): Result<Unit> = runCatching {
        val userId = authRepository.getCurrentSession()?.userId
            ?: error("No active session")

        userScopedRepos.forEach { it.deleteAllData(userId).getOrThrow() }
        spotRepository.clearCache().getOrThrow()
        authRepository.deleteAccount().getOrThrow()
    }
}

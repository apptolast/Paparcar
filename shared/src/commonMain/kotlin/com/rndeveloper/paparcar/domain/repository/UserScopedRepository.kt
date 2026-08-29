package com.rndeveloper.paparcar.domain.repository

/**
 * Marker contract for repositories that hold user-specific data.
 * All implementors must support cascading deletion of every record
 * belonging to a given user — called during account deletion to
 * satisfy GDPR right-to-erasure requirements.
 *
 * @see com.rndeveloper.paparcar.domain.usecase.user.DeleteAccountUseCase
 */
interface UserScopedRepository {
    suspend fun deleteAllData(userId: String): Result<Unit>
}

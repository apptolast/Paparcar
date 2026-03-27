package io.apptolast.paparcar.domain.repository

import com.apptolast.customlogin.domain.model.UserSession
import io.apptolast.paparcar.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserProfileRepository {
    /** Returns the cached profile or creates a new one in Firestore + Room on first login. */
    suspend fun getOrCreateProfile(session: UserSession): Result<UserProfile>

    /** Observe the locally cached profile for the given user. */
    fun observeProfile(userId: String): Flow<UserProfile?>
}

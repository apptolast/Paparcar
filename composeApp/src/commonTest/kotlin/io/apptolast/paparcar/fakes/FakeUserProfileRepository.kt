package io.apptolast.paparcar.fakes

import com.apptolast.customlogin.domain.model.UserSession
import io.apptolast.paparcar.domain.model.UserProfile
import io.apptolast.paparcar.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeUserProfileRepository : UserProfileRepository {

    private val profiles = mutableMapOf<String, UserProfile>()
    private val _profileFlow = MutableStateFlow<Map<String, UserProfile>>(emptyMap())

    var getOrCreateCallCount = 0
        private set
    var getOrCreateResult: Result<UserProfile> = Result.success(defaultProfile())
    var observedUserId: String? = null

    override suspend fun getOrCreateProfile(session: UserSession): Result<UserProfile> {
        getOrCreateCallCount++
        getOrCreateResult.getOrNull()?.let {
            profiles[session.userId] = it
            _profileFlow.value = profiles.toMap()
        }
        return getOrCreateResult
    }

    override fun observeProfile(userId: String): Flow<UserProfile?> {
        observedUserId = userId
        return _profileFlow.map { it[userId] }
    }

    companion object {
        fun defaultProfile(userId: String = "user-123") = UserProfile(
            userId = userId,
            email = "test@paparcar.io",
            displayName = "Test User",
            photoUrl = null,
            createdAt = 0L,
            updatedAt = 0L,
        )
    }
}

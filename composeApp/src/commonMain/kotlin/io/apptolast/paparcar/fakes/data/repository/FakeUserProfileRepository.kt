@file:OptIn(ExperimentalTime::class)

package io.apptolast.paparcar.fakes.data.repository

import com.apptolast.customlogin.domain.model.UserSession
import io.apptolast.paparcar.domain.model.UserProfile
import io.apptolast.paparcar.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class FakeUserProfileRepository : UserProfileRepository {
    private val mockProfile = UserProfile(
        userId = "mock_user_001",
        email = "rene@paparcar.mock",
        displayName = "Rene Dev",
        photoUrl = null,
        createdAt = Clock.System.now().toEpochMilliseconds(),
        updatedAt = Clock.System.now().toEpochMilliseconds(),
        defaultVehicleId = "mock_vehicle_001"
    )

    override suspend fun getOrCreateProfile(session: UserSession): Result<UserProfile> =
        Result.success(mockProfile)

    override fun observeProfile(userId: String): Flow<UserProfile?> =
        MutableStateFlow(mockProfile).asStateFlow()

    override suspend fun deleteAllData(userId: String): Result<Unit> = Result.success(Unit)
}

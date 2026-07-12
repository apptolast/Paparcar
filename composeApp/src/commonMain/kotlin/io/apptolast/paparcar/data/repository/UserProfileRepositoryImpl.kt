@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.data.repository

import com.apptolast.customlogin.domain.model.UserSession
import io.apptolast.paparcar.data.datasource.local.room.UserProfileDao
import io.apptolast.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import io.apptolast.paparcar.data.datasource.remote.dto.UserProfileDto
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.domain.model.UserProfile
import io.apptolast.paparcar.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

class UserProfileRepositoryImpl(
    private val remoteDataSource: RemoteUserProfileDataSource,
    private val profileDao: UserProfileDao,
) : UserProfileRepository {

    override suspend fun getOrCreateProfile(session: UserSession): Result<UserProfile> =
        runCatching {
            // 1. Remote-first: pull the latest snapshot so any change made from
            //    another device (display name, photo, defaultVehicleId) lands in
            //    Room before the splash decides where to navigate. Wrapped in
            //    withTimeoutOrNull so a network that HANGS (not just fails) can't block
            //    the splash forever — a failure OR a timeout falls through to cached state
            //    in step 2. [AUDIT-DATA-001 M9]
            val remoteDto = withTimeoutOrNull(REMOTE_PROFILE_TIMEOUT_MS) {
                runCatching { remoteDataSource.getProfile(session.userId) }.getOrNull()
            }
            if (remoteDto != null) {
                profileDao.insertOrUpdate(remoteDto.toEntity())
                return@runCatching remoteDto.toDomain()
            }

            // 2. Offline fallback: serve whatever Room has.
            profileDao.getProfile(session.userId)?.toDomain()?.let { return@runCatching it }

            // 3. Truly new user (or first login after data wipe): create the doc
            //    in Firestore + cache locally. defaultVehicleId starts null and
            //    gets populated when the user registers their first vehicle.
            val now = Clock.System.now().toEpochMilliseconds()
            val dto = UserProfileDto(
                userId = session.userId,
                email = session.email,
                displayName = session.displayName,
                photoUrl = session.photoUrl,
                createdAt = now,
                updatedAt = now,
            )
            remoteDataSource.createOrUpdateProfile(dto)
            profileDao.insertOrUpdate(dto.toEntity())
            dto.toDomain()
        }

    override fun observeProfile(userId: String): Flow<UserProfile?> =
        profileDao.observeProfile(userId).map { it?.toDomain() }

    override suspend fun deleteAllData(userId: String): Result<Unit> = runCatching {
        remoteDataSource.deleteUserData(userId)
        profileDao.deleteByUser(userId)
    }

    private companion object {
        /** [AUDIT-DATA-001 M9] Splash-blocking ceiling on the remote profile fetch: past this the
         *  bootstrap serves cached state rather than waiting on a stalled network. */
        const val REMOTE_PROFILE_TIMEOUT_MS = 8_000L
    }
}

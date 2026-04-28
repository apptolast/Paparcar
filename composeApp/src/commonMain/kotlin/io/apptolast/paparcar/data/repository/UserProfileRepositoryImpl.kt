@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.data.repository

import com.apptolast.customlogin.domain.model.UserSession
import io.apptolast.paparcar.data.datasource.local.room.UserProfileDao
import io.apptolast.paparcar.data.datasource.remote.UserProfileDataSource
import io.apptolast.paparcar.data.datasource.remote.dto.UserProfileDto
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.domain.model.UserProfile
import io.apptolast.paparcar.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class UserProfileRepositoryImpl(
    private val remoteDataSource: UserProfileDataSource,
    private val profileDao: UserProfileDao,
) : UserProfileRepository {

    override suspend fun getOrCreateProfile(session: UserSession): Result<UserProfile> =
        runCatching {
            // 1. Local cache hit
            profileDao.getProfile(session.userId)?.toDomain()?.let { return@runCatching it }

            // 2. Remote fetch — existing user on a new device
            remoteDataSource.getProfile(session.userId)?.let { dto ->
                profileDao.insertOrUpdate(dto.toEntity())
                return@runCatching dto.toDomain()
            }

            // 3. First login — create document in Firestore + cache locally
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
}

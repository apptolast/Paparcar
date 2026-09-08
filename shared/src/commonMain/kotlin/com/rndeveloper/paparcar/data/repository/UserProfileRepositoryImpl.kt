@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.data.repository

import com.apptolast.baselogin.domain.model.UserSession
import com.rndeveloper.paparcar.data.datasource.local.room.UserProfileDao
import com.rndeveloper.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import com.rndeveloper.paparcar.data.datasource.remote.dto.UserProfileDto
import com.rndeveloper.paparcar.data.mapper.toDomain
import com.rndeveloper.paparcar.data.mapper.toEntity
import com.rndeveloper.paparcar.domain.model.UserProfile
import com.rndeveloper.paparcar.domain.repository.UserProfileRepository
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
                // 1b. The doc is a snapshot of the FIRST sign-in; the session is what Google says
                //     TODAY. Reconcile identity fields (photo, name, email) so an account change
                //     eventually lands in Firestore + Room. Field-targeted write, best-effort:
                //     a failure (or timeout — same splash ceiling as the fetch) still serves the
                //     merged profile locally, and the next launch retries because the remote doc
                //     stays behind the session. [PROFILE-PHOTO-STUCK-AND-STALE-001]
                val reconciled = remoteDto.reconciledWith(session)
                if (reconciled != remoteDto) {
                    withTimeoutOrNull(REMOTE_PROFILE_TIMEOUT_MS) {
                        runCatching {
                            remoteDataSource.updateIdentityFields(
                                userId = session.userId,
                                email = reconciled.email,
                                displayName = reconciled.displayName,
                                photoUrl = reconciled.photoUrl,
                                updatedAt = reconciled.updatedAt,
                            )
                        }
                    }
                }
                profileDao.insertOrUpdate(reconciled.toEntity())
                return@runCatching reconciled.toDomain()
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

    override suspend fun setFirstStepsDismissedAt(userId: String, dismissedAtMs: Long?): Result<Unit> =
        runCatching { remoteDataSource.updateFirstStepsDismissedAt(userId, dismissedAtMs) }

    override suspend fun deleteAllData(userId: String): Result<Unit> = runCatching {
        remoteDataSource.deleteUserData(userId)
        profileDao.deleteByUser(userId)
    }

    /** The session only OVERWRITES, never ERASES: a blank/null value from the provider (e.g. an
     *  account with no photo) keeps whatever the remote doc already has. Returns `this` unchanged
     *  when nothing differs, so callers can compare by equality. */
    private fun UserProfileDto.reconciledWith(session: UserSession): UserProfileDto {
        val mergedEmail = session.email?.takeIf { it.isNotBlank() } ?: email
        val mergedDisplayName = session.displayName?.takeIf { it.isNotBlank() } ?: displayName
        val mergedPhotoUrl = session.photoUrl?.takeIf { it.isNotBlank() } ?: photoUrl
        if (mergedEmail == email && mergedDisplayName == displayName && mergedPhotoUrl == photoUrl) {
            return this
        }
        return copy(
            email = mergedEmail,
            displayName = mergedDisplayName,
            photoUrl = mergedPhotoUrl,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        )
    }

    private companion object {
        /** [AUDIT-DATA-001 M9] Splash-blocking ceiling on the remote profile fetch: past this the
         *  bootstrap serves cached state rather than waiting on a stalled network. */
        const val REMOTE_PROFILE_TIMEOUT_MS = 8_000L
    }
}

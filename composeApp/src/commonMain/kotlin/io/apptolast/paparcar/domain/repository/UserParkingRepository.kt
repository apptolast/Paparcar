package io.apptolast.paparcar.domain.repository

import io.apptolast.paparcar.domain.model.UserParkingSession
import kotlinx.coroutines.flow.Flow

interface UserParkingRepository {
    suspend fun saveSession(session: UserParkingSession): Result<Unit>
    suspend fun getActiveSession(): UserParkingSession?
    fun observeActiveSession(): Flow<UserParkingSession?>
    suspend fun getAllSessions(): List<UserParkingSession>
    suspend fun clearActive(): Result<Unit>
}

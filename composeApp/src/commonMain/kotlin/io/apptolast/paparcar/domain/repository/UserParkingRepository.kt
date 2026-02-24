package io.apptolast.paparcar.domain.repository

import io.apptolast.paparcar.domain.model.ParkingSession
import kotlinx.coroutines.flow.Flow

interface UserParkingRepository {
    suspend fun saveSession(session: ParkingSession): Result<Unit>
    suspend fun getActiveSession(): ParkingSession?
    fun observeActiveSession(): Flow<ParkingSession?>
    suspend fun getAllSessions(): List<ParkingSession>
    suspend fun clearActive(): Result<Unit>
}

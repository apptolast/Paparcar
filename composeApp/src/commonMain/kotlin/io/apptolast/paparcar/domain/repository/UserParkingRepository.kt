package io.apptolast.paparcar.domain.repository

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.UserParking
import kotlinx.coroutines.flow.Flow

interface UserParkingRepository {
    suspend fun saveSession(session: UserParking): Result<Unit>
    suspend fun getActiveSession(): UserParking?
    fun observeActiveSession(): Flow<UserParking?>
    fun observeAllSessions(): Flow<List<UserParking>>
    suspend fun clearActive(): Result<Unit>
    /** In-place update of address+POI for an existing session. Does not affect [isActive]. */
    suspend fun updateLocationInfo(
        id: String,
        address: AddressInfo?,
        placeInfo: PlaceInfo?,
    ): Result<Unit>
}

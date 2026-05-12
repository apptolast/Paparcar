package io.apptolast.paparcar.data.datasource.remote

import io.apptolast.paparcar.data.datasource.remote.dto.AddressDto
import io.apptolast.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import io.apptolast.paparcar.data.datasource.remote.dto.PlaceInfoDto
import io.apptolast.paparcar.data.datasource.remote.dto.UserProfileDto

interface RemoteUserProfileDataSource {
    suspend fun getProfile(userId: String): UserProfileDto?
    suspend fun createOrUpdateProfile(profile: UserProfileDto)
    /** Field-targeted update — avoids overwriting the whole profile doc. */
    suspend fun updateDefaultVehicleId(userId: String, vehicleId: String?)
    suspend fun saveParkingSession(userId: String, session: ParkingHistoryDto)
    suspend fun updateParkingSessionActiveFlag(userId: String, sessionId: String, isActive: Boolean)
    suspend fun updateParkingSessionLocation(userId: String, sessionId: String, address: AddressDto?, placeInfo: PlaceInfoDto?)
    suspend fun getParkingHistory(userId: String): List<ParkingHistoryDto>
    suspend fun deleteUserData(userId: String)
}

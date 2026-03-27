package io.apptolast.paparcar.data.datasource.remote

import io.apptolast.paparcar.data.datasource.remote.dto.AddressDto
import io.apptolast.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import io.apptolast.paparcar.data.datasource.remote.dto.PlaceInfoDto
import io.apptolast.paparcar.data.datasource.remote.dto.UserProfileDto

interface UserProfileDataSource {
    suspend fun getProfile(userId: String): UserProfileDto?
    suspend fun createOrUpdateProfile(profile: UserProfileDto)
    suspend fun saveParkingSession(userId: String, session: ParkingHistoryDto)
    suspend fun updateParkingSessionLocation(userId: String, sessionId: String, address: AddressDto?, placeInfo: PlaceInfoDto?)
    suspend fun getParkingHistory(userId: String): List<ParkingHistoryDto>
}

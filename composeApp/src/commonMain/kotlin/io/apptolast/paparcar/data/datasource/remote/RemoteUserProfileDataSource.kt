package io.apptolast.paparcar.data.datasource.remote

import io.apptolast.paparcar.data.datasource.remote.dto.AddressDto
import io.apptolast.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import io.apptolast.paparcar.data.datasource.remote.dto.PlaceInfoDto
import io.apptolast.paparcar.data.datasource.remote.dto.UserProfileDto
import io.apptolast.paparcar.data.datasource.remote.dto.VehicleDto

interface RemoteUserProfileDataSource {
    suspend fun getProfile(userId: String): UserProfileDto?
    suspend fun createOrUpdateProfile(profile: UserProfileDto)
    /** Field-targeted update — avoids overwriting the whole profile doc. */
    suspend fun updateDefaultVehicleId(userId: String, vehicleId: String?)

    // ─── Parking History ──────────────────────────────────────────────────────

    suspend fun saveParkingSession(userId: String, session: ParkingHistoryDto)
    suspend fun updateParkingSessionActiveFlag(userId: String, sessionId: String, isActive: Boolean)
    suspend fun updateParkingSessionLocation(userId: String, sessionId: String, address: AddressDto?, placeInfo: PlaceInfoDto?)
    suspend fun getParkingHistory(userId: String): List<ParkingHistoryDto>

    // ─── Vehicles ─────────────────────────────────────────────────────────────

    suspend fun getVehicles(userId: String): List<VehicleDto>
    suspend fun saveVehicle(userId: String, vehicle: VehicleDto)
    suspend fun deleteVehicle(userId: String, vehicleId: String)
    suspend fun updateVehicleActiveFlag(userId: String, vehicleId: String, isActive: Boolean)

    suspend fun deleteUserData(userId: String)
}

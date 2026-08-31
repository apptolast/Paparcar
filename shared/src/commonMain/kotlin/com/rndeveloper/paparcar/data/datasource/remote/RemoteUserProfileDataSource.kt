package com.rndeveloper.paparcar.data.datasource.remote

import com.rndeveloper.paparcar.data.datasource.remote.dto.AddressDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.PlaceInfoDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.UserProfileDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.VehicleDto

interface RemoteUserProfileDataSource {
    suspend fun getProfile(userId: String): UserProfileDto?
    suspend fun createOrUpdateProfile(profile: UserProfileDto)
    /** Field-targeted update — avoids overwriting the whole profile doc. */
    suspend fun updateDefaultVehicleId(userId: String, vehicleId: String?)

    // ─── Parking History ──────────────────────────────────────────────────────

    suspend fun saveParkingSession(userId: String, session: ParkingHistoryDto)
    suspend fun clearParkingSessionActiveFlag(userId: String, sessionId: String)
    suspend fun updateParkingSessionAddressAndPlace(userId: String, sessionId: String, address: AddressDto?, placeInfo: PlaceInfoDto?)
    suspend fun getParkingHistory(userId: String): List<ParkingHistoryDto>

    /** Deletes every parking session of [vehicleId]. Called when the vehicle itself is deleted:
     *  docs left behind would be pulled back into Room by the inbound reconcile.
     *  [VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001] */
    suspend fun deleteParkingSessionsForVehicle(userId: String, vehicleId: String)

    // ─── Vehicles ─────────────────────────────────────────────────────────────

    suspend fun getVehicles(userId: String): List<VehicleDto>
    suspend fun saveVehicle(userId: String, vehicle: VehicleDto)
    suspend fun deleteVehicle(userId: String, vehicleId: String)
    suspend fun updateVehicleActiveFlag(userId: String, vehicleId: String, isActive: Boolean)
    /** Field-targeted update of the paired BT MAC. Pass null to remove the pairing. */
    suspend fun updateVehicleBluetoothDevice(userId: String, vehicleId: String, deviceAddress: String?)

    suspend fun deleteUserData(userId: String)
}

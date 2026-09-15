package com.rndeveloper.paparcar.data.datasource.remote

import com.rndeveloper.paparcar.data.datasource.remote.dto.AddressDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.PlaceInfoDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarInviteDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarMemberDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarSessionDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarStateDto
import kotlinx.coroutines.flow.Flow

/** Firestore side of a shared car: `sharedCars/{carId}` and `sharedCarInvites/{code}`.
 *  [VEH-SHARED-001] */
interface RemoteSharedCarDataSource {

    fun observeCar(carId: String): Flow<SharedCarDto?>
    fun observeState(carId: String): Flow<SharedCarStateDto?>
    fun observeMembers(carId: String): Flow<List<SharedCarMemberDto>>
    fun observeSessions(carId: String): Flow<List<SharedCarSessionDto>>

    suspend fun getCar(carId: String): SharedCarDto?
    suspend fun getMembers(carId: String): List<SharedCarMemberDto>

    suspend fun createCar(car: SharedCarDto, owner: SharedCarMemberDto)
    /** Removes the mirror, its state, its members and its sessions. The owner's own car and
     *  personal history are NOT touched — they never lived here. */
    suspend fun deleteCar(carId: String)

    suspend fun getState(carId: String): SharedCarStateDto?
    suspend fun putState(carId: String, state: SharedCarStateDto)
    /** Flips "still parked" without touching the position: the car keeps its last known spot. */
    suspend fun setStateParked(carId: String, isParked: Boolean, updatedAtMs: Long)
    suspend fun putSession(carId: String, session: SharedCarSessionDto)
    suspend fun closeSession(carId: String, sessionId: String, endedAtMs: Long, updatedAtMs: Long)
    suspend fun updateSessionAddress(
        carId: String,
        sessionId: String,
        address: AddressDto?,
        placeInfo: PlaceInfoDto?,
        updatedAtMs: Long,
    )

    suspend fun putMember(carId: String, member: SharedCarMemberDto)
    /** Publishes whether this member's routes are visible, so the others can be told why a line is
     *  missing instead of guessing. */
    suspend fun setMemberSharesRoutes(carId: String, uid: String, sharesRoutes: Boolean)
    suspend fun deleteMember(carId: String, uid: String)

    suspend fun putInvite(invite: SharedCarInviteDto)
    /** Points the car at its one live code, so the owner can read it again and revoke it later. */
    suspend fun setActiveInvite(carId: String, code: String?, expiresAtMs: Long?)
    suspend fun getInvite(code: String): SharedCarInviteDto?
    suspend fun claimInvite(code: String, uid: String)
    suspend fun deleteInvitesFor(carId: String)
}

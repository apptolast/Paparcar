package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.data.datasource.local.room.SharedCarDao
import com.rndeveloper.paparcar.data.datasource.local.room.SharedCarEntity
import com.rndeveloper.paparcar.data.datasource.local.room.UserProfileDao
import com.rndeveloper.paparcar.data.datasource.local.room.UserProfileEntity
import com.rndeveloper.paparcar.data.datasource.remote.RemoteSharedCarDataSource
import com.rndeveloper.paparcar.data.datasource.remote.dto.AddressDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.PlaceInfoDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarInviteDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarMemberDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarSessionDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.SharedCarStateDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory `sharedCars/` + `sharedCarInvites/`. [VEH-SHARED-001] */
class FakeSharedCarRemote : RemoteSharedCarDataSource {

    val cars = MutableStateFlow<Map<String, SharedCarDto>>(emptyMap())
    val states = MutableStateFlow<Map<String, SharedCarStateDto>>(emptyMap())
    val members = MutableStateFlow<Map<String, List<SharedCarMemberDto>>>(emptyMap())
    val sessions = MutableStateFlow<Map<String, List<SharedCarSessionDto>>>(emptyMap())
    val invites = MutableStateFlow<Map<String, SharedCarInviteDto>>(emptyMap())

    override fun observeCar(carId: String): Flow<SharedCarDto?> = cars.map { it[carId] }
    override fun observeState(carId: String): Flow<SharedCarStateDto?> = states.map { it[carId] }
    override fun observeMembers(carId: String): Flow<List<SharedCarMemberDto>> =
        members.map { it[carId].orEmpty() }
    override fun observeSessions(carId: String): Flow<List<SharedCarSessionDto>> =
        sessions.map { it[carId].orEmpty() }

    override suspend fun getCar(carId: String): SharedCarDto? = cars.value[carId]
    override suspend fun getMembers(carId: String): List<SharedCarMemberDto> = members.value[carId].orEmpty()

    override suspend fun createCar(car: SharedCarDto, owner: SharedCarMemberDto) {
        cars.value = cars.value + (car.carId to car)
        members.value = members.value + (car.carId to listOf(owner))
    }

    override suspend fun deleteCar(carId: String) {
        cars.value = cars.value - carId
        members.value = members.value - carId
        sessions.value = sessions.value - carId
        states.value = states.value - carId
        deleteInvitesFor(carId)
    }

    override suspend fun getState(carId: String): SharedCarStateDto? = states.value[carId]

    override suspend fun putState(carId: String, state: SharedCarStateDto) {
        states.value = states.value + (carId to state)
    }

    override suspend fun setStateParked(carId: String, isParked: Boolean, updatedAtMs: Long) {
        states.value[carId]?.let { current ->
            states.value = states.value + (carId to current.copy(isParked = isParked, updatedAtMs = updatedAtMs))
        }
    }

    override suspend fun putSession(carId: String, session: SharedCarSessionDto) {
        val rest = sessions.value[carId].orEmpty().filterNot { it.sessionId == session.sessionId }
        sessions.value = sessions.value + (carId to rest + session)
    }

    override suspend fun closeSession(carId: String, sessionId: String, endedAtMs: Long, updatedAtMs: Long) {
        sessions.value = sessions.value + (
            carId to sessions.value[carId].orEmpty().map {
                if (it.sessionId == sessionId) {
                    it.copy(isActive = false, endedAtMs = endedAtMs, updatedAtMs = updatedAtMs)
                } else it
            }
            )
    }

    override suspend fun updateSessionAddress(
        carId: String,
        sessionId: String,
        address: AddressDto?,
        placeInfo: PlaceInfoDto?,
        updatedAtMs: Long,
    ) {
        sessions.value = sessions.value + (
            carId to sessions.value[carId].orEmpty().map {
                if (it.sessionId == sessionId) {
                    it.copy(address = address, placeInfo = placeInfo, updatedAtMs = updatedAtMs)
                } else it
            }
            )
    }

    override suspend fun putMember(carId: String, member: SharedCarMemberDto) {
        val rest = members.value[carId].orEmpty().filterNot { it.uid == member.uid }
        members.value = members.value + (carId to rest + member)
    }

    override suspend fun setMemberSharesRoutes(carId: String, uid: String, sharesRoutes: Boolean) {
        members.value = members.value + (
            carId to members.value[carId].orEmpty().map {
                if (it.uid == uid) it.copy(sharesRoutes = sharesRoutes) else it
            }
            )
    }

    override suspend fun deleteMember(carId: String, uid: String) {
        members.value = members.value + (carId to members.value[carId].orEmpty().filterNot { it.uid == uid })
    }

    override suspend fun putInvite(invite: SharedCarInviteDto) {
        invites.value = invites.value + (invite.code to invite)
    }

    override suspend fun setActiveInvite(carId: String, code: String?, expiresAtMs: Long?) {
        cars.value[carId]?.let { car ->
            cars.value = cars.value + (carId to car.copy(inviteCode = code, inviteExpiresAtMs = expiresAtMs))
        }
    }

    override suspend fun getInvite(code: String): SharedCarInviteDto? = invites.value[code]

    override suspend fun claimInvite(code: String, uid: String) {
        invites.value[code]?.let { invites.value = invites.value + (code to it.copy(claimedByUid = uid)) }
    }

    override suspend fun deleteInvitesFor(carId: String) {
        invites.value = invites.value.filterValues { it.carId != carId }
    }
}

/** In-memory `shared_cars` Room table. */
class FakeSharedCarDao : SharedCarDao {
    private val rows = MutableStateFlow<Map<String, SharedCarEntity>>(emptyMap())

    override suspend fun upsert(car: SharedCarEntity) {
        rows.value = rows.value + (car.carId to car)
    }

    override fun observeById(carId: String): Flow<SharedCarEntity?> = rows.map { it[carId] }
    override fun observeAll(): Flow<List<SharedCarEntity>> = rows.map { it.values.toList() }
    override suspend fun getById(carId: String): SharedCarEntity? = rows.value[carId]
    override suspend fun deleteById(carId: String) { rows.value = rows.value - carId }
    override suspend fun deleteAll() { rows.value = emptyMap() }
}

/** In-memory `user_profile` Room table — only what the shared car reads: the person's name. */
class FakeUserProfileDao(profile: UserProfileEntity? = null) : UserProfileDao {
    private val rows = MutableStateFlow<Map<String, UserProfileEntity>>(
        profile?.let { mapOf(it.userId to it) }.orEmpty(),
    )

    override suspend fun insertOrUpdate(profile: UserProfileEntity) {
        rows.value = rows.value + (profile.userId to profile)
    }

    override suspend fun getProfile(userId: String): UserProfileEntity? = rows.value[userId]
    override fun observeProfile(userId: String): Flow<UserProfileEntity?> = rows.map { it[userId] }

    override suspend fun updateDefaultVehicleId(userId: String, vehicleId: String?) {
        rows.value[userId]?.let { rows.value = rows.value + (userId to it.copy(defaultVehicleId = vehicleId)) }
    }

    override suspend fun deleteByUser(userId: String) { rows.value = rows.value - userId }
    override suspend fun deleteAll() { rows.value = emptyMap() }
}

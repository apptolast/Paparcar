package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.SharedCar
import com.rndeveloper.paparcar.domain.model.SharedCarInvite
import com.rndeveloper.paparcar.domain.model.SharedCarMember
import com.rndeveloper.paparcar.domain.model.SharedCarRole
import com.rndeveloper.paparcar.domain.model.SharedCarSession
import com.rndeveloper.paparcar.domain.model.SharedCarState
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.repository.SharedCarRepository
import com.rndeveloper.paparcar.domain.sharedcar.SharedCarInviteCode
import com.rndeveloper.paparcar.fakes.MockScenario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The group of a car, without a backend. [VEH-SHARED-001]
 *
 * Scenario-aware like every other mock fake: [MockScenario.sharedCarJoined] decides whether the
 * mock user is the OWNER of the group (the Plus story: a code to read out loud, somebody to remove)
 * or a MEMBER who joined somebody else's car — which is the reading that cannot be reached from the
 * owner's side and is exactly the one worth seeing in hand.
 */
class FakeSharedCarRepository(private val scenario: MockScenario? = null) : SharedCarRepository {

    private val sharing = MutableStateFlow(false)
    private val sharesRoutes = MutableStateFlow(true)
    private val invite = MutableStateFlow<SharedCarInvite?>(null)

    private val joined get() = scenario?.sharedCarJoined

    private fun car(isMemberReading: Boolean): SharedCar = SharedCar(
        carId = MOCK_CAR_ID,
        ownerUid = if (isMemberReading) "mock_user_002" else "mock_user_001",
        ownerName = if (isMemberReading) "Marta" else "You",
        displayName = "Family car",
        sizeCategory = VehicleSize.MEDIUM_SUV,
        createdAtMs = MOCK_CREATED_AT_MS,
        activeInviteCode = invite.value?.code,
        activeInviteExpiresAtMs = invite.value?.expiresAtMs,
        state = SharedCarState(
            sessionId = "mock_shared_session",
            location = GpsPoint(40.4176, -3.6982, 8f, MOCK_PARKED_AT_MS, 0f),
            parkedAtMs = MOCK_PARKED_AT_MS,
            authorUid = "mock_user_002",
            authorName = "Marta",
            address = AddressInfo(
                street = "Leeson Street, 14",
                city = "Madrid",
                region = null,
                country = "España",
            ),
            isParked = true,
            updatedAtMs = MOCK_PARKED_AT_MS,
        ),
    )

    override fun observeSharedCar(carId: String): Flow<SharedCar?> =
        combine(sharing, invite, joined ?: MutableStateFlow(false)) { isSharing, _, isJoined ->
            when {
                isJoined -> car(isMemberReading = true)
                isSharing -> car(isMemberReading = false)
                else -> null
            }
        }

    override fun observeSharedCars(): Flow<List<SharedCar>> =
        observeSharedCar(MOCK_CAR_ID).map { listOfNotNull(it) }

    override fun observeMembers(carId: String): Flow<List<SharedCarMember>> =
        (joined ?: MutableStateFlow(false)).map { isJoined ->
            if (isJoined) {
                listOf(
                    SharedCarMember("mock_user_002", "Marta", role = SharedCarRole.OWNER),
                    SharedCarMember("mock_user_001", "You"),
                )
            } else {
                listOf(
                    SharedCarMember("mock_user_001", "You", role = SharedCarRole.OWNER),
                    SharedCarMember("mock_user_003", "Nacho"),
                )
            }
        }

    override fun observeSessions(carId: String, limit: Int): Flow<List<SharedCarSession>> =
        MutableStateFlow(
            listOf(
                SharedCarSession(
                    sessionId = "mock_shared_session",
                    authorUid = "mock_user_002",
                    authorName = "Marta",
                    location = GpsPoint(40.4176, -3.6982, 8f, MOCK_PARKED_AT_MS, 0f),
                    parkedAtMs = MOCK_PARKED_AT_MS,
                    updatedAtMs = MOCK_PARKED_AT_MS,
                ),
            ),
        )

    override suspend fun shareVehicle(vehicle: Vehicle): Result<SharedCar> {
        sharing.value = true
        return Result.success(car(isMemberReading = false))
    }

    override suspend fun stopSharing(carId: String): Result<Unit> {
        sharing.value = false
        invite.value = null
        return Result.success(Unit)
    }

    override suspend fun createInvite(carId: String): Result<SharedCarInvite> {
        val fresh = SharedCarInvite(
            code = "K7QP3M",
            carId = carId,
            ownerUid = "mock_user_001",
            createdAtMs = MOCK_CREATED_AT_MS,
            expiresAtMs = MOCK_CREATED_AT_MS + SharedCarInviteCode.INVITE_TTL_MS,
        )
        invite.value = fresh
        return Result.success(fresh)
    }

    override suspend fun findInvite(code: String): Result<SharedCarInvite> =
        Result.success(
            SharedCarInvite(
                code = code,
                carId = MOCK_CAR_ID,
                ownerUid = "mock_user_002",
                createdAtMs = MOCK_CREATED_AT_MS,
                expiresAtMs = MOCK_CREATED_AT_MS + SharedCarInviteCode.INVITE_TTL_MS,
            ),
        )

    override suspend fun joinByCode(code: String, bluetoothDeviceId: String?): Result<SharedCar> {
        joined?.value = true
        return Result.success(car(isMemberReading = true))
    }

    override suspend fun leave(carId: String): Result<Unit> {
        joined?.value = false
        return Result.success(Unit)
    }

    override suspend fun setShareRoutes(carId: String, share: Boolean): Result<Unit> {
        sharesRoutes.value = share
        return Result.success(Unit)
    }

    override suspend fun removeMember(carId: String, uid: String): Result<Unit> = Result.success(Unit)

    /** Cerrar sesión no cambia la pertenencia, así que el fake tampoco: solo anota que pasó. */
    var pushTokensRetired = false
    override suspend fun retirePushTokens(): Result<Unit> {
        pushTokensRetired = true
        return Result.success(Unit)
    }

    override suspend fun syncFromRemote(): Result<Unit> = Result.success(Unit)

    /** Erasure looks like walking out of every group at once, which is what it is. */
    override suspend fun deleteAllData(userId: String): Result<Unit> {
        joined?.value = false
        return Result.success(Unit)
    }

    private companion object {
        const val MOCK_CAR_ID = "mock_vehicle_001"
        const val MOCK_CREATED_AT_MS = 1_757_000_000_000L
        const val MOCK_PARKED_AT_MS = 1_757_900_000_000L
    }
}

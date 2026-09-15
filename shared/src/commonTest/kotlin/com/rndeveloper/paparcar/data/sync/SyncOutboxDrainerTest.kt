package com.rndeveloper.paparcar.data.sync

import com.rndeveloper.paparcar.data.datasource.remote.RemoteUserProfileDataSource
import com.rndeveloper.paparcar.data.datasource.remote.dto.AddressDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.ParkingHistoryDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.PlaceInfoDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.UserProfileDto
import com.rndeveloper.paparcar.data.datasource.remote.dto.VehicleDto
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.repository.AddressAndPlaceRepository
import com.rndeveloper.paparcar.domain.repository.NearbySpotsRead
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import com.rndeveloper.paparcar.domain.sync.OutboxOp
import com.rndeveloper.paparcar.domain.sync.SyncOutbox
import com.rndeveloper.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import com.rndeveloper.paparcar.fakes.data.repository.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeUserParkingRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest

/** [IOS-SYNC-A-QUEUE-THAT-DIES-WITH-THE-PROCESS-001] The drainer's load-bearing behaviors:
 *  the delete tombstone, the keep-on-failure bookkeeping, the poison cap, and the golden
 *  TTL-in-queue gate. */
class SyncOutboxDrainerTest {

    // ── Minimal fakes (recording, controllable) ─────────────────────────────────────────────

    private class InMemoryOutbox : SyncOutbox {
        val ops = linkedMapOf<String, OutboxOp>()
        override fun persist(op: OutboxOp) { ops[op.key] = op }
        override fun pending(): List<OutboxOp> = ops.values.toList()
        override fun remove(key: String) { ops.remove(key) }
    }

    private class RecordingRemote : RemoteUserProfileDataSource {
        var failNext = false
        val deletedSessionVehicles = mutableListOf<String>()
        val deletedVehicles = mutableListOf<String>()
        val savedSessions = mutableListOf<ParkingHistoryDto>()
        val clearedSessions = mutableListOf<String>()

        private fun gate() { if (failNext) error("network down") }
        override suspend fun getProfile(userId: String): UserProfileDto? = null
        override suspend fun createOrUpdateProfile(profile: UserProfileDto) = gate()
        override suspend fun updateIdentityFields(
            userId: String, email: String?, displayName: String?, photoUrl: String?, updatedAt: Long,
        ) = gate()
        override suspend fun updateDefaultVehicleId(userId: String, vehicleId: String?) = gate()
        override suspend fun updateFirstStepsDismissedAt(userId: String, dismissedAtMs: Long?) = gate()
        override suspend fun saveParkingSession(userId: String, session: ParkingHistoryDto) { gate(); savedSessions += session }
        override suspend fun clearParkingSessionActiveFlag(userId: String, sessionId: String) { gate(); clearedSessions += sessionId }
        override suspend fun updateParkingSessionAddressAndPlace(
            userId: String, sessionId: String, address: AddressDto?, placeInfo: PlaceInfoDto?,
        ) = gate()
        override suspend fun getParkingHistory(userId: String): List<ParkingHistoryDto> = emptyList()
        override suspend fun deleteParkingSessionsForVehicle(userId: String, vehicleId: String) {
            gate(); deletedSessionVehicles += vehicleId
        }
        override suspend fun getVehicles(userId: String): List<VehicleDto> = emptyList()
        override suspend fun saveVehicle(userId: String, vehicle: VehicleDto) = gate()
        override suspend fun deleteVehicle(userId: String, vehicleId: String) { gate(); deletedVehicles += vehicleId }
        override suspend fun updateVehicleActiveFlag(userId: String, vehicleId: String, isActive: Boolean) = gate()
        override suspend fun updateVehicleBluetoothDevice(userId: String, vehicleId: String, deviceAddress: String?) = gate()
        override suspend fun deleteUserData(userId: String) = gate()
    }

    private class RecordingSpots : SpotRepository {
        var failNext = false
        val reported = mutableListOf<Spot>()
        override suspend fun reportSpotReleased(spot: Spot): Result<Unit> {
            if (failNext) return Result.failure(IllegalStateException("network down"))
            reported += spot
            return Result.success(Unit)
        }
        override fun observeNearbySpots(location: GpsPoint, radiusMeters: Double): Flow<List<Spot>> = emptyFlow()
        override suspend fun getNearbySpotsOnce(location: GpsPoint, radiusMeters: Double) =
            Result.success(NearbySpotsRead(spots = emptyList(), documentsRead = 0))
        override suspend fun retractSpot(spotId: String): Result<Unit> = Result.success(Unit)
        override suspend fun sendSpotSignal(spotId: String, accepted: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun refreshSpot(spotId: String): Result<Unit> = Result.success(Unit)
        override suspend fun clearCache(): Result<Unit> = Result.success(Unit)
    }

    private class NoAddress : AddressAndPlaceRepository {
        override fun getAddressAndPlace(
            lat: Double,
            lon: Double,
            persisting: Boolean,
        ): Flow<AddressAndPlace> = emptyFlow()
    }

    // ── Harness ─────────────────────────────────────────────────────────────────────────────

    private val outbox = InMemoryOutbox()
    private val remote = RecordingRemote()
    private val spots = RecordingSpots()
    private var nowMs = 1_000_000L

    private fun drainer() = SyncOutboxDrainer(
        outbox = outbox,
        remoteDataSource = remote,
        authRepository = FakeAuthRepository(),
        spotRepository = spots,
        userParkingRepository = FakeUserParkingRepository(),
        getAddressAndPlace = GetAddressAndPlaceUseCase(NoAddress()),
        clock = { nowMs },
    )

    private fun spotOp(enqueuedAtMs: Long, provisional: Boolean = false) = OutboxOp.ReportSpot(
        spotId = "spot-1", lat = 40.4, lon = -3.7, spotType = SpotType.AUTO_DETECTED,
        confidence = 0.9f, sizeCategory = null, carbodyType = null,
        reportedBy = "uid", provisional = provisional, enqueuedAtMs = enqueuedAtMs,
    )

    // ── The tombstone ───────────────────────────────────────────────────────────────────────

    @Test
    fun should_executeSessionsFirstThenVehicle_when_deleteTombstoneDrains() = runTest {
        outbox.persist(OutboxOp.DeleteVehicle("veh-1"))
        drainer().drain()
        assertEquals(listOf("veh-1"), remote.deletedSessionVehicles)
        assertEquals(listOf("veh-1"), remote.deletedVehicles)
        assertTrue(outbox.pending().isEmpty())
    }

    @Test
    fun should_keepTheTombstoneWithOneMoreAttempt_when_theDeleteFailsMidFlight() = runTest {
        // The resurrection bug's exact shape: the op must SURVIVE the failure — a fresh drainer
        // (a fresh process) over the same store finishes the job.
        outbox.persist(OutboxOp.DeleteVehicle("veh-1"))
        remote.failNext = true
        drainer().drain()
        assertEquals(1, outbox.pending().size)
        assertEquals(1, outbox.pending().single().attempts)

        remote.failNext = false
        drainer().drain() // "next process"
        assertEquals(listOf("veh-1"), remote.deletedVehicles)
        assertTrue(outbox.pending().isEmpty())
    }

    @Test
    fun should_dropWithoutClogging_when_anOpKeepsFailingPastTheCap() = runTest {
        outbox.persist(OutboxOp.DeleteVehicle("veh-1", attempts = 19))
        remote.failNext = true
        drainer().drain()
        assertTrue(outbox.pending().isEmpty()) // dropped at the cap — logged, never eternal
    }

    // ── The golden TTL-in-queue gate ────────────────────────────────────────────────────────

    @Test
    fun should_dropAnExpiredSpotWithoutPublishing_when_theQueueOutlivedItsTtl() = runTest {
        outbox.persist(spotOp(enqueuedAtMs = 0L))
        nowMs = 3 * 60 * 60 * 1_000L // 3 h later — past any TTL
        drainer().drain()
        assertTrue(spots.reported.isEmpty())
        assertTrue(outbox.pending().isEmpty())
    }

    @Test
    fun should_publishWithTtlAnchoredAtEnqueue_when_aFreshSpotDrains() = runTest {
        outbox.persist(spotOp(enqueuedAtMs = nowMs))
        drainer().drain()
        val spot = spots.reported.single()
        assertTrue(spot.expiresAt > nowMs) // alive…
        // …but its life measures from ENQUEUE, so a replay can never extend it.
        assertEquals(nowMs + (spot.expiresAt - nowMs), spot.expiresAt)
        assertTrue(outbox.pending().isEmpty())
    }

    // ── Room is the truth ───────────────────────────────────────────────────────────────────

    @Test
    fun should_finishTheOpWithoutRemoteCall_when_theSessionRowIsGone() = runTest {
        outbox.persist(OutboxOp.SaveSession("no-such-session", previousSessionId = null))
        drainer().drain()
        assertTrue(remote.savedSessions.isEmpty())
        assertTrue(outbox.pending().isEmpty()) // nothing to sync IS done, not a retry
    }

    @Test
    fun should_saveWithFreshUpdatedAtAndClearPrevious_when_aSeededSessionDrains() = runTest {
        val repo = FakeUserParkingRepository()
        val session = UserParking(
            id = "sess-1", vehicleId = "veh-1",
            location = GpsPoint(40.4, -3.7, 8f, 500L, 0f),
        )
        repo.saveNewParkingSession(session)
        val drainer = SyncOutboxDrainer(
            outbox = outbox, remoteDataSource = remote, authRepository = FakeAuthRepository(),
            spotRepository = spots, userParkingRepository = repo,
            getAddressAndPlace = GetAddressAndPlaceUseCase(NoAddress()), clock = { nowMs },
        )
        outbox.persist(OutboxOp.SaveSession("sess-1", previousSessionId = "sess-0"))
        drainer.drain()
        assertEquals(listOf("sess-0"), remote.clearedSessions)
        assertEquals(nowMs, remote.savedSessions.single().updatedAt) // fresh LWW stamp at SEND time
    }
}

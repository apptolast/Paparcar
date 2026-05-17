package io.apptolast.paparcar.domain.usecase.user

import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import io.apptolast.paparcar.fakes.FakeZoneRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BootstrapUserDataUseCaseTest {

    private val userId = "user-1"

    private fun buildUseCase(
        vehicleRepo: FakeVehicleRepository = FakeVehicleRepository(),
        parkingRepo: FakeUserParkingRepository = FakeUserParkingRepository(),
        zoneRepo: FakeZoneRepository = FakeZoneRepository(),
    ): Triple<BootstrapUserDataUseCase, FakeVehicleRepository, Pair<FakeUserParkingRepository, FakeZoneRepository>> {
        val useCase = BootstrapUserDataUseCase(
            vehicleRepository = vehicleRepo,
            userParkingRepository = parkingRepo,
            zoneRepository = zoneRepo,
        )
        return Triple(useCase, vehicleRepo, parkingRepo to zoneRepo)
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `returns success and invokes every repo when all syncs succeed`() = runTest {
        val (useCase, vehicleRepo, others) = buildUseCase()
        val (parkingRepo, zoneRepo) = others

        val result = useCase(userId)

        assertTrue(result.isSuccess)
        assertEquals(1, vehicleRepo.syncFromRemoteCallCount)
        assertEquals(1, parkingRepo.syncCallCount)
        assertEquals(1, zoneRepo.syncFromRemoteCallCount)
    }

    // ── Fail-fast on each repo ────────────────────────────────────────────────

    @Test
    fun `propagates failure when vehicle sync fails`() = runTest {
        val vehicleRepo = FakeVehicleRepository().apply {
            syncFromRemoteResult = Result.failure(RuntimeException("vehicles down"))
        }
        val (useCase, _, _) = buildUseCase(vehicleRepo = vehicleRepo)

        val result = useCase(userId)

        assertTrue(result.isFailure)
        assertEquals("vehicles down", result.exceptionOrNull()?.message)
    }

    @Test
    fun `propagates failure when parking history sync fails`() = runTest {
        val parkingRepo = FakeUserParkingRepository().apply {
            syncResult = Result.failure(RuntimeException("parking down"))
        }
        val (useCase, _, _) = buildUseCase(parkingRepo = parkingRepo)

        val result = useCase(userId)

        assertTrue(result.isFailure)
        assertEquals("parking down", result.exceptionOrNull()?.message)
    }

    @Test
    fun `propagates failure when zone sync fails`() = runTest {
        val zoneRepo = FakeZoneRepository().apply {
            syncFromRemoteResult = Result.failure(RuntimeException("zones down"))
        }
        val (useCase, _, _) = buildUseCase(zoneRepo = zoneRepo)

        val result = useCase(userId)

        assertTrue(result.isFailure)
        assertEquals("zones down", result.exceptionOrNull()?.message)
    }

}

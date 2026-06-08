package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObserveParkedVehiclesUseCaseTest {

    private val location = GpsPoint(latitude = 40.416775, longitude = -3.703790, accuracy = 8f, timestamp = 0L, speed = 0f)

    private val vehicleA = Vehicle(id = "a-vehicle", userId = "user-1", sizeCategory = VehicleSize.MEDIUM_SUV)
    private val vehicleB = Vehicle(id = "b-vehicle", userId = "user-1", sizeCategory = VehicleSize.VAN_HIGH)

    private fun activeSession(id: String, vehicleId: String) = UserParking(
        id = id,
        userId = "user-1",
        vehicleId = vehicleId,
        location = location,
        isActive = true,
    )

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `should emit empty list when no active sessions exist`() = runTest {
        val useCase = buildUseCase()

        val result = useCase().first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `should emit one entry when one active session exists with matching vehicle`() = runTest {
        val session = activeSession("s-1", vehicleA.id)
        val parkingRepo = FakeUserParkingRepository(initialSession = session)
        val useCase = buildUseCase(parking = parkingRepo, vehicles = FakeVehicleRepository(vehicleA))

        val result = useCase().first()

        assertEquals(1, result.size)
        assertEquals(vehicleA.id, result.first().vehicleId)
        assertEquals(session.id, result.first().sessionId)
    }

    @Test
    fun `should emit two entries when two vehicles have active sessions`() = runTest {
        val sessionA = activeSession("s-a", vehicleA.id)
        val sessionB = activeSession("s-b", vehicleB.id)
        val parkingRepo = FakeUserParkingRepository(initialSessions = listOf(sessionA, sessionB))
        val useCase = buildUseCase(
            parking = parkingRepo,
            vehicles = FakeVehicleRepository(vehicleA, extraVehicles = listOf(vehicleB)),
        )

        val result = useCase().first()

        assertEquals(2, result.size)
    }

    @Test
    fun `should skip sessions whose vehicleId is null`() = runTest {
        val orphanSession = UserParking(id = "orphan", userId = "user-1", vehicleId = null, location = location, isActive = true)
        val parkingRepo = FakeUserParkingRepository(initialSession = orphanSession)
        val useCase = buildUseCase(parking = parkingRepo, vehicles = FakeVehicleRepository(vehicleA))

        val result = useCase().first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `should skip sessions whose vehicleId does not resolve to a known vehicle`() = runTest {
        val session = activeSession("s-ghost", "deleted-vehicle")
        val parkingRepo = FakeUserParkingRepository(initialSession = session)
        val useCase = buildUseCase(parking = parkingRepo, vehicles = FakeVehicleRepository(vehicleA))

        val result = useCase().first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `should skip inactive sessions`() = runTest {
        val inactive = UserParking(id = "s-past", userId = "user-1", vehicleId = vehicleA.id, location = location, isActive = false)
        val parkingRepo = FakeUserParkingRepository(initialSession = inactive)
        val useCase = buildUseCase(parking = parkingRepo, vehicles = FakeVehicleRepository(vehicleA))

        val result = useCase().first()

        assertTrue(result.isEmpty())
    }

    // ── stableRank ──────────────────────────────────────────────────────────

    @Test
    fun `should assign stableRank by lexicographic vehicleId order`() = runTest {
        // "a-vehicle" < "b-vehicle" lexicographically → a=0, b=1
        val sessionA = activeSession("s-a", vehicleA.id)
        val sessionB = activeSession("s-b", vehicleB.id)
        val parkingRepo = FakeUserParkingRepository(initialSessions = listOf(sessionA, sessionB))
        val useCase = buildUseCase(
            parking = parkingRepo,
            vehicles = FakeVehicleRepository(vehicleA, extraVehicles = listOf(vehicleB)),
        )

        val result = useCase().first().sortedBy { it.vehicleId }

        assertEquals(0, result[0].stableRank) // a-vehicle
        assertEquals(1, result[1].stableRank) // b-vehicle
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildUseCase(
        parking: FakeUserParkingRepository = FakeUserParkingRepository(),
        vehicles: FakeVehicleRepository = FakeVehicleRepository(vehicleA),
    ) = ObserveParkedVehiclesUseCase(
        userParkingRepository = parking,
        vehicleRepository = vehicles,
    )
}

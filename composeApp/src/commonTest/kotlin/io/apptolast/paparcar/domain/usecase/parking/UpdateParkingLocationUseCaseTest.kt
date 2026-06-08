@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.fakes.FakeDepartureEventBus
import io.apptolast.paparcar.fakes.FakeGeofenceManager
import io.apptolast.paparcar.fakes.FakeParkingEnrichmentScheduler
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UpdateParkingLocationUseCaseTest {

    private val newLocation = GpsPoint(latitude = 40.42, longitude = -3.71, accuracy = 5f, timestamp = 0L, speed = 0f)

    private fun existingSession(id: String = "session-1", sizeCategory: VehicleSize = VehicleSize.MEDIUM_SUV) = UserParking(
        id = id,
        userId = "user-1",
        vehicleId = "v-1",
        location = GpsPoint(latitude = 40.41, longitude = -3.70, accuracy = 8f, timestamp = 0L, speed = 0f),
        isActive = true,
        geofenceId = id,
        sizeCategory = sizeCategory,
    )

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `should return success with updated session on valid input`() = runTest {
        val session = existingSession()
        val repo = FakeUserParkingRepository(initialSession = session)
        val useCase = buildUseCase(repo = repo)

        val result = useCase(session.id, newLocation)

        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
    }

    @Test
    fun `should call updateLocation on repository`() = runTest {
        val session = existingSession()
        val repo = FakeUserParkingRepository(initialSession = session)
        val useCase = buildUseCase(repo = repo)

        useCase(session.id, newLocation)

        assertEquals(1, repo.updateParkingSessionPositionCallCount)
    }

    @Test
    fun `should schedule enrichment after successful location update`() = runTest {
        val session = existingSession()
        val repo = FakeUserParkingRepository(initialSession = session)
        val enrichment = FakeParkingEnrichmentScheduler()
        val useCase = buildUseCase(repo = repo, enrichment = enrichment)

        useCase(session.id, newLocation)

        assertEquals(1, enrichment.scheduleCallCount)
        assertEquals(session.id, enrichment.lastScheduledSessionId)
    }

    @Test
    fun `should create new geofence at updated location`() = runTest {
        val session = existingSession()
        val repo = FakeUserParkingRepository(initialSession = session)
        val geofence = FakeGeofenceManager()
        val useCase = buildUseCase(repo = repo, geofence = geofence)

        useCase(session.id, newLocation)

        assertEquals(1, geofence.createGeofenceCallCount)
        assertEquals(session.id, geofence.lastCreatedGeofenceId)
    }

    @Test
    fun `should reuse session id as geofence id`() = runTest {
        val session = existingSession("parking-xyz")
        val repo = FakeUserParkingRepository(initialSession = session)
        val geofence = FakeGeofenceManager()
        val useCase = buildUseCase(repo = repo, geofence = geofence)

        useCase(session.id, newLocation)

        assertEquals("parking-xyz", geofence.lastCreatedGeofenceId)
    }

    // ── Geofence radius ───────────────────────────────────────────────────────

    @Test
    fun `should use moto radius for MOTO session`() = runTest {
        val session = existingSession(sizeCategory = VehicleSize.MOTORCYCLE)
        val repo = FakeUserParkingRepository(initialSession = session)
        val geofence = FakeGeofenceManager()
        val config = ParkingDetectionConfig()
        val useCase = buildUseCase(repo = repo, geofence = geofence, config = config)
        val zeroAccuracy = newLocation.copy(accuracy = 0f)

        useCase(session.id, zeroAccuracy)

        assertEquals(config.geofenceRadiusMotoMeters, geofence.lastCreatedRadiusMeters)
    }

    @Test
    fun `should cap radius at geofenceMaxRadiusMeters when accuracy is very high`() = runTest {
        val session = existingSession(sizeCategory = VehicleSize.VAN_HIGH)
        val repo = FakeUserParkingRepository(initialSession = session)
        val geofence = FakeGeofenceManager()
        val config = ParkingDetectionConfig()
        val useCase = buildUseCase(repo = repo, geofence = geofence, config = config)
        val highInaccuracy = newLocation.copy(accuracy = 100f)

        useCase(session.id, highInaccuracy)

        assertEquals(config.geofenceMaxRadiusMeters, geofence.lastCreatedRadiusMeters)
    }

    // ── Failure path ──────────────────────────────────────────────────────────

    @Test
    fun `should return failure when repository updateLocation fails`() = runTest {
        val repo = FakeUserParkingRepository().apply {
            updateParkingSessionPositionResult = Result.failure(RuntimeException("DB error"))
        }
        val useCase = buildUseCase(repo = repo)

        val result = useCase("session-1", newLocation)

        assertTrue(result.isFailure)
    }

    @Test
    fun `should not schedule enrichment when location update fails`() = runTest {
        val repo = FakeUserParkingRepository().apply {
            updateParkingSessionPositionResult = Result.failure(RuntimeException("DB error"))
        }
        val enrichment = FakeParkingEnrichmentScheduler()
        val useCase = buildUseCase(repo = repo, enrichment = enrichment)

        useCase("session-1", newLocation)

        assertEquals(0, enrichment.scheduleCallCount)
    }

    @Test
    fun `should not create geofence when location update fails`() = runTest {
        val repo = FakeUserParkingRepository().apply {
            updateParkingSessionPositionResult = Result.failure(RuntimeException("DB error"))
        }
        val geofence = FakeGeofenceManager()
        val useCase = buildUseCase(repo = repo, geofence = geofence)

        useCase("session-1", newLocation)

        assertEquals(0, geofence.createGeofenceCallCount)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildUseCase(
        repo: FakeUserParkingRepository = FakeUserParkingRepository(),
        geofence: FakeGeofenceManager = FakeGeofenceManager(),
        enrichment: FakeParkingEnrichmentScheduler = FakeParkingEnrichmentScheduler(),
        config: ParkingDetectionConfig = ParkingDetectionConfig(),
    ) = UpdateParkingLocationUseCase(
        userParkingRepository = repo,
        geofenceService = geofence,
        enrichmentScheduler = enrichment,
        config = config,
        departureEventBus = FakeDepartureEventBus(),
    )
}

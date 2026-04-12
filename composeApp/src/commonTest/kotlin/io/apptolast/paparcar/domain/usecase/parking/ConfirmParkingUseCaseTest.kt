@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.fakes.FakeAppNotificationManager
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeGeofenceManager
import io.apptolast.paparcar.fakes.FakeParkingEnrichmentScheduler
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import io.apptolast.paparcar.domain.error.PaparcarError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfirmParkingUseCaseTest {

    private val location = GpsPoint(
        latitude = 40.416775,
        longitude = -3.703790,
        accuracy = 8f,
        timestamp = 0L,
        speed = 0f,
    )

    private val session = FakeAuthRepository.authenticatedSession(userId = "user-42")

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `should save session when called with valid location`() = runTest {
        val repo = FakeUserParkingRepository()
        val useCase = buildUseCase(repo = repo)

        val result = useCase(location)

        assertTrue(result.isSuccess)
        assertEquals(1, repo.saveSessionCallCount)
    }

    @Test
    fun `should schedule enrichment after successful save`() = runTest {
        val enrichment = FakeParkingEnrichmentScheduler()
        val useCase = buildUseCase(enrichment = enrichment)

        val result = useCase(location)

        assertTrue(result.isSuccess)
        assertEquals(1, enrichment.scheduleCallCount)
    }

    @Test
    fun `should create geofence after successful save`() = runTest {
        val geofence = FakeGeofenceManager()
        val useCase = buildUseCase(geofence = geofence)

        val result = useCase(location)

        assertTrue(result.isSuccess)
        assertEquals(1, geofence.createGeofenceCallCount)
    }

    @Test
    fun `should show notification after successful save`() = runTest {
        val notification = FakeAppNotificationManager()
        val useCase = buildUseCase(notification = notification)

        val result = useCase(location)

        assertTrue(result.isSuccess)
        assertEquals(1, notification.parkingSpotSavedCallCount)
    }

    @Test
    fun `should use same ID for session and geofence`() = runTest {
        val repo = FakeUserParkingRepository()
        val geofence = FakeGeofenceManager()
        val useCase = buildUseCase(repo = repo, geofence = geofence)

        val result = useCase(location)

        assertTrue(result.isSuccess)
        val savedSession = repo.getActiveSession()
        assertNotNull(savedSession)
        assertEquals(savedSession.id, savedSession.geofenceId)
        assertEquals(savedSession.geofenceId, geofence.lastCreatedGeofenceId)
    }

    @Test
    fun `should use authenticated user ID in session`() = runTest {
        val repo = FakeUserParkingRepository()
        val useCase = buildUseCase(repo = repo)

        val result = useCase(location)

        assertTrue(result.isSuccess)
        val savedSession = repo.getActiveSession()
        assertNotNull(savedSession)
        assertEquals(session.userId, savedSession.userId)
    }

    // ── Save failure — abort early ────────────────────────────────────────────

    @Test
    fun `should not schedule enrichment when save fails`() = runTest {
        val repo = FakeUserParkingRepository().apply {
            saveSessionResult = Result.failure(RuntimeException("DB error"))
        }
        val enrichment = FakeParkingEnrichmentScheduler()
        val useCase = buildUseCase(repo = repo, enrichment = enrichment)

        val result = useCase(location)

        assertTrue(result.isFailure)
        assertIs<PaparcarError.Parking.SaveFailed>(result.exceptionOrNull())
        assertEquals(0, enrichment.scheduleCallCount)
    }

    @Test
    fun `should not create geofence when save fails`() = runTest {
        val repo = FakeUserParkingRepository().apply {
            saveSessionResult = Result.failure(RuntimeException("DB error"))
        }
        val geofence = FakeGeofenceManager()
        val useCase = buildUseCase(repo = repo, geofence = geofence)

        val result = useCase(location)

        assertTrue(result.isFailure)
        assertIs<PaparcarError.Parking.SaveFailed>(result.exceptionOrNull())
        assertEquals(0, geofence.createGeofenceCallCount)
    }

    @Test
    fun `should not show notification when save fails`() = runTest {
        val repo = FakeUserParkingRepository().apply {
            saveSessionResult = Result.failure(RuntimeException("DB error"))
        }
        val notification = FakeAppNotificationManager()
        val useCase = buildUseCase(repo = repo, notification = notification)

        val result = useCase(location)

        assertTrue(result.isFailure)
        assertIs<PaparcarError.Parking.SaveFailed>(result.exceptionOrNull())
        assertEquals(0, notification.parkingSpotSavedCallCount)
    }

    // ── sizeCategory from VehicleRepository ──────────────────────────────────

    @Test
    fun `should resolve sizeCategory from default vehicle when not explicitly provided`() = runTest {
        val repo = FakeUserParkingRepository()
        val vehicle = Vehicle(id = "v-1", userId = "user-42", sizeCategory = VehicleSize.LARGE)
        val useCase = buildUseCase(repo = repo, vehicles = FakeVehicleRepository(vehicle))

        useCase(location)

        val savedSession = repo.getActiveSession()
        assertNotNull(savedSession)
        assertEquals(VehicleSize.LARGE, savedSession.sizeCategory)
    }

    @Test
    fun `should use explicit sizeCategory when provided even if vehicle has different size`() = runTest {
        val repo = FakeUserParkingRepository()
        val vehicle = Vehicle(id = "v-1", userId = "user-42", sizeCategory = VehicleSize.LARGE)
        val useCase = buildUseCase(repo = repo, vehicles = FakeVehicleRepository(vehicle))

        useCase(location, sizeCategory = VehicleSize.MOTO)

        val savedSession = repo.getActiveSession()
        assertNotNull(savedSession)
        assertEquals(VehicleSize.MOTO, savedSession.sizeCategory)
    }

    @Test
    fun `should save session with null sizeCategory when no default vehicle registered`() = runTest {
        val repo = FakeUserParkingRepository()
        val useCase = buildUseCase(repo = repo, vehicles = FakeVehicleRepository(defaultVehicle = null))

        useCase(location)

        val savedSession = repo.getActiveSession()
        assertNotNull(savedSession)
        assertNull(savedSession.sizeCategory)
    }

    // ── No authenticated user ─────────────────────────────────────────────────

    @Test
    fun `should save session with empty userId when no authenticated session`() = runTest {
        val repo = FakeUserParkingRepository()
        val noAuthCase = buildUseCase(
            repo = repo,
            auth = FakeAuthRepository(initialSession = null),
        )

        val result = noAuthCase(location)

        assertTrue(result.isSuccess)
        val savedSession = repo.getActiveSession()
        assertNotNull(savedSession)
        assertEquals("", savedSession.userId)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildUseCase(
        repo: FakeUserParkingRepository = FakeUserParkingRepository(),
        vehicles: FakeVehicleRepository = FakeVehicleRepository(),
        geofence: FakeGeofenceManager = FakeGeofenceManager(),
        notification: FakeAppNotificationManager = FakeAppNotificationManager(),
        enrichment: FakeParkingEnrichmentScheduler = FakeParkingEnrichmentScheduler(),
        auth: FakeAuthRepository = FakeAuthRepository(initialSession = session),
    ) = ConfirmParkingUseCase(
        userParkingRepository = repo,
        vehicleRepository = vehicles,
        geofenceService = geofence,
        notificationPort = notification,
        enrichmentScheduler = enrichment,
        authRepository = auth,
        config = ParkingDetectionConfig(),
    )
}

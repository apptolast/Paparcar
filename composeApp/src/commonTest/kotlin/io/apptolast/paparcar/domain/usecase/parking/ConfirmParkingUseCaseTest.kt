@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.fakes.FakeAppNotificationManager
import io.apptolast.paparcar.fakes.FakeDepartureEventBus
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeGeofenceManager
import io.apptolast.paparcar.fakes.FakeParkingEnrichmentScheduler
import io.apptolast.paparcar.fakes.FakeZoneRepository
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import io.apptolast.paparcar.domain.error.PaparcarError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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

    /** Default vehicle present in every fixture unless a test explicitly overrides — matches
     *  the production invariant (FLOW-001 ensures the user always has a default before the
     *  detection service can fire). [AUTH-001] */
    private val defaultVehicle = Vehicle(id = "v-1", userId = "user-42", sizeCategory = VehicleSize.MEDIUM_SUV)

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `should save session when called with valid location`() = runTest {
        val repo = FakeUserParkingRepository()
        val useCase = buildUseCase(repo = repo)

        val result = useCase(location, detectionReliability = 0.9f)

        assertTrue(result.isSuccess)
        assertEquals(1, repo.saveNewParkingSessionCallCount)
    }

    @Test
    fun `should schedule enrichment after successful save`() = runTest {
        val enrichment = FakeParkingEnrichmentScheduler()
        val useCase = buildUseCase(enrichment = enrichment)

        val result = useCase(location, detectionReliability = 0.9f)

        assertTrue(result.isSuccess)
        assertEquals(1, enrichment.scheduleCallCount)
    }

    @Test
    fun `should create geofence after successful save`() = runTest {
        val geofence = FakeGeofenceManager()
        val useCase = buildUseCase(geofence = geofence)

        val result = useCase(location, detectionReliability = 0.9f)

        assertTrue(result.isSuccess)
        assertEquals(1, geofence.createGeofenceCallCount)
    }

    @Test
    fun `should show notification after successful save`() = runTest {
        val notification = FakeAppNotificationManager()
        val useCase = buildUseCase(notification = notification)

        val result = useCase(location, detectionReliability = 0.9f)

        assertTrue(result.isSuccess)
        assertEquals(1, notification.parkingSpotSavedCallCount)
    }

    @Test
    fun `should use same ID for session and geofence`() = runTest {
        val repo = FakeUserParkingRepository()
        val geofence = FakeGeofenceManager()
        val useCase = buildUseCase(repo = repo, geofence = geofence)

        val result = useCase(location, detectionReliability = 0.9f)

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

        val result = useCase(location, detectionReliability = 0.9f)

        assertTrue(result.isSuccess)
        val savedSession = repo.getActiveSession()
        assertNotNull(savedSession)
        assertEquals(session.userId, savedSession.userId)
    }

    // ── Save failure — abort early ────────────────────────────────────────────

    @Test
    fun `should not schedule enrichment when save fails`() = runTest {
        val repo = FakeUserParkingRepository().apply {
            saveNewParkingSessionResult = Result.failure(RuntimeException("DB error"))
        }
        val enrichment = FakeParkingEnrichmentScheduler()
        val useCase = buildUseCase(repo = repo, enrichment = enrichment)

        val result = useCase(location, detectionReliability = 0.9f)

        assertTrue(result.isFailure)
        assertIs<PaparcarError.Parking.SaveFailed>(result.exceptionOrNull())
        assertEquals(0, enrichment.scheduleCallCount)
    }

    @Test
    fun `should not create geofence when save fails`() = runTest {
        val repo = FakeUserParkingRepository().apply {
            saveNewParkingSessionResult = Result.failure(RuntimeException("DB error"))
        }
        val geofence = FakeGeofenceManager()
        val useCase = buildUseCase(repo = repo, geofence = geofence)

        val result = useCase(location, detectionReliability = 0.9f)

        assertTrue(result.isFailure)
        assertIs<PaparcarError.Parking.SaveFailed>(result.exceptionOrNull())
        assertEquals(0, geofence.createGeofenceCallCount)
    }

    @Test
    fun `should not show notification when save fails`() = runTest {
        val repo = FakeUserParkingRepository().apply {
            saveNewParkingSessionResult = Result.failure(RuntimeException("DB error"))
        }
        val notification = FakeAppNotificationManager()
        val useCase = buildUseCase(repo = repo, notification = notification)

        val result = useCase(location, detectionReliability = 0.9f)

        assertTrue(result.isFailure)
        assertIs<PaparcarError.Parking.SaveFailed>(result.exceptionOrNull())
        assertEquals(0, notification.parkingSpotSavedCallCount)
    }

    // ── sizeCategory from VehicleRepository ──────────────────────────────────

    @Test
    fun `should resolve sizeCategory from default vehicle when not explicitly provided`() = runTest {
        val repo = FakeUserParkingRepository()
        val vehicle = Vehicle(id = "v-1", userId = "user-42", sizeCategory = VehicleSize.LARGE_SEDAN)
        val useCase = buildUseCase(repo = repo, vehicles = FakeVehicleRepository(vehicle))

        useCase(location, detectionReliability = 0.9f)

        val savedSession = repo.getActiveSession()
        assertNotNull(savedSession)
        assertEquals(VehicleSize.LARGE_SEDAN, savedSession.sizeCategory)
    }

    @Test
    fun `should use explicit sizeCategory when provided even if vehicle has different size`() = runTest {
        val repo = FakeUserParkingRepository()
        val vehicle = Vehicle(id = "v-1", userId = "user-42", sizeCategory = VehicleSize.LARGE_SEDAN)
        val useCase = buildUseCase(repo = repo, vehicles = FakeVehicleRepository(vehicle))

        useCase(location, detectionReliability = 0.9f, sizeCategory = VehicleSize.MOTORCYCLE)

        val savedSession = repo.getActiveSession()
        assertNotNull(savedSession)
        assertEquals(VehicleSize.MOTORCYCLE, savedSession.sizeCategory)
    }

    @Test
    fun `should return NoDefaultVehicle failure and not save when no default vehicle registered`() = runTest {
        // Invariant per AUTH-001 / parking_vehicleid memory: a parking belongs to a vehicle.
        // The History UI is per-vehicle (HIST-001); saving with vehicleId=null would create
        // an unreachable orphan. Better to fail loud than to corrupt Firestore.
        val repo = FakeUserParkingRepository()
        val useCase = buildUseCase(repo = repo, vehicles = FakeVehicleRepository(defaultVehicle = null))

        val result = useCase(location, detectionReliability = 0.9f)

        assertTrue(result.isFailure)
        assertIs<PaparcarError.Parking.NoDefaultVehicle>(result.exceptionOrNull())
        assertEquals(0, repo.saveNewParkingSessionCallCount)
    }

    // ── Explicit vehicleId (BT-strategy path) ─────────────────────────────────

    @Test
    fun `should attach session to explicit vehicleId when provided`() = runTest {
        // BT strategy resolves the parking vehicle from the disconnected device address
        // and passes that vehicleId explicitly. The use case must honour it, even when
        // it is NOT the user's default vehicle (default ≠ parked under multi-vehicle BT).
        val repo = FakeUserParkingRepository()
        val secondary = Vehicle(
            id = "v-2",
            userId = "user-42",
            sizeCategory = VehicleSize.VAN_HIGH,
            bluetoothDeviceId = "AA:BB:CC:DD:EE:FF",
        )
        val useCase = buildUseCase(
            repo = repo,
            vehicles = FakeVehicleRepository(
                defaultVehicle = defaultVehicle,
                extraVehicles = listOf(secondary),
            ),
        )

        val result = useCase(location, detectionReliability = 0.9f, vehicleId = "v-2")

        assertTrue(result.isSuccess)
        val savedSession = repo.getActiveSession()
        assertNotNull(savedSession)
        assertEquals("v-2", savedSession.vehicleId)
    }

    @Test
    fun `should resolve sizeCategory from explicit vehicle when vehicleId provided`() = runTest {
        val repo = FakeUserParkingRepository()
        val secondary = Vehicle(id = "v-2", userId = "user-42", sizeCategory = VehicleSize.VAN_HIGH)
        val useCase = buildUseCase(
            repo = repo,
            vehicles = FakeVehicleRepository(
                defaultVehicle = defaultVehicle, // MEDIUM
                extraVehicles = listOf(secondary),
            ),
        )

        useCase(location, detectionReliability = 0.9f, vehicleId = "v-2")

        val savedSession = repo.getActiveSession()
        assertNotNull(savedSession)
        assertEquals(VehicleSize.VAN_HIGH, savedSession.sizeCategory)
    }

    @Test
    fun `should NOT fall back to default vehicle when explicit vehicleId does not resolve`() = runTest {
        // If the caller passes a vehicleId we cannot find, that is a precondition violation
        // (BT receiver resolved from a row that has since been deleted, or test misconfig).
        // Silently falling back to the default would attach the session to the wrong vehicle.
        val repo = FakeUserParkingRepository()
        val useCase = buildUseCase(
            repo = repo,
            vehicles = FakeVehicleRepository(defaultVehicle = defaultVehicle),
        )

        val result = useCase(location, detectionReliability = 0.9f, vehicleId = "v-missing")

        assertTrue(result.isFailure)
        assertIs<PaparcarError.Parking.NoDefaultVehicle>(result.exceptionOrNull())
        assertEquals(0, repo.saveNewParkingSessionCallCount)
    }

    @Test
    fun `should fall back to default vehicle when vehicleId is null`() = runTest {
        // Coordinator-strategy / manual paths still call without a vehicleId — the default
        // resolution remains the legacy single-vehicle behaviour.
        val repo = FakeUserParkingRepository()
        val useCase = buildUseCase(repo = repo)

        val result = useCase(location, detectionReliability = 0.9f, vehicleId = null)

        assertTrue(result.isSuccess)
        val savedSession = repo.getActiveSession()
        assertNotNull(savedSession)
        assertEquals(defaultVehicle.id, savedSession.vehicleId)
    }

    // ── No authenticated user ─────────────────────────────────────────────────

    @Test
    fun `should return NotAuthenticated failure when no active session`() = runTest {
        val noAuthCase = buildUseCase(
            auth = FakeAuthRepository(initialSession = null),
        )

        val result = noAuthCase(location, detectionReliability = 0.9f)

        assertTrue(result.isFailure)
        assertIs<PaparcarError.Auth.NotAuthenticated>(result.exceptionOrNull())
    }

    // ── Adaptive geofence radius ──────────────────────────────────────────────

    @Test
    fun `should use moto radius for MOTO vehicle`() = runTest {
        val geofence = FakeGeofenceManager()
        val vehicle = Vehicle(id = "v-1", userId = "user-42", sizeCategory = VehicleSize.MOTORCYCLE)
        val config = ParkingDetectionConfig()
        val useCase = buildUseCase(
            geofence = geofence,
            vehicles = FakeVehicleRepository(vehicle),
            config = config,
        )
        val zeroAccuracy = location.copy(accuracy = 0f)

        useCase(zeroAccuracy, detectionReliability = 0.9f)

        assertEquals(config.geofenceRadiusMotoMeters, geofence.lastCreatedRadiusMeters)
    }

    @Test
    fun `should use van radius for VAN vehicle`() = runTest {
        val geofence = FakeGeofenceManager()
        val vehicle = Vehicle(id = "v-1", userId = "user-42", sizeCategory = VehicleSize.VAN_HIGH)
        val config = ParkingDetectionConfig()
        val useCase = buildUseCase(
            geofence = geofence,
            vehicles = FakeVehicleRepository(vehicle),
            config = config,
        )
        val zeroAccuracy = location.copy(accuracy = 0f)

        useCase(zeroAccuracy, detectionReliability = 0.9f)

        assertEquals(config.geofenceRadiusVanMeters, geofence.lastCreatedRadiusMeters)
    }

    @Test
    fun `should pad radius with GPS accuracy`() = runTest {
        val geofence = FakeGeofenceManager()
        val config = ParkingDetectionConfig()
        val useCase = buildUseCase(geofence = geofence, config = config)
        val locationWith10mAccuracy = location.copy(accuracy = 10f)

        useCase(locationWith10mAccuracy, detectionReliability = 0.9f, sizeCategory = VehicleSize.MEDIUM_SUV)

        val expected = config.geofenceRadiusMeters + (10f * config.geofenceAccuracyPadFactor)
        assertEquals(expected, geofence.lastCreatedRadiusMeters)
    }

    @Test
    fun `should cap radius at geofenceMaxRadiusMeters`() = runTest {
        val geofence = FakeGeofenceManager()
        val config = ParkingDetectionConfig()
        val useCase = buildUseCase(geofence = geofence, config = config)
        // accuracy=100m on a VAN (base 120m) → 120 + 150 = 270m > 200m max
        val highInaccuracy = location.copy(accuracy = 100f)

        useCase(highInaccuracy, detectionReliability = 0.9f, sizeCategory = VehicleSize.VAN_HIGH)

        assertEquals(config.geofenceMaxRadiusMeters, geofence.lastCreatedRadiusMeters)
    }

    // ── DepartureEventBus reset [BUG-WALK-DEPART-001] ────────────────────────

    @Test
    fun `should reset departure event bus after successful parking confirmation`() = runTest {
        val bus = FakeDepartureEventBus(initialTimestamp = System.currentTimeMillis() - 60_000L)
        val useCase = buildUseCase(bus = bus)

        useCase(location, detectionReliability = 0.9f)

        assertEquals(1, bus.resetCount)
    }

    @Test
    fun `should not reset departure event bus when parking save fails`() = runTest {
        val bus = FakeDepartureEventBus(initialTimestamp = System.currentTimeMillis() - 60_000L)
        val failingRepo = FakeUserParkingRepository().apply {
            saveNewParkingSessionResult = Result.failure(RuntimeException("db error"))
        }
        val useCase = buildUseCase(repo = failingRepo, bus = bus)

        useCase(location, detectionReliability = 0.9f)

        assertEquals(0, bus.resetCount)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildUseCase(
        repo: FakeUserParkingRepository = FakeUserParkingRepository(),
        vehicles: FakeVehicleRepository = FakeVehicleRepository(defaultVehicle),
        geofence: FakeGeofenceManager = FakeGeofenceManager(),
        notification: FakeAppNotificationManager = FakeAppNotificationManager(),
        enrichment: FakeParkingEnrichmentScheduler = FakeParkingEnrichmentScheduler(),
        auth: FakeAuthRepository = FakeAuthRepository(initialSession = session),
        config: ParkingDetectionConfig = ParkingDetectionConfig(),
        bus: FakeDepartureEventBus = FakeDepartureEventBus(),
    ) = ConfirmParkingUseCase(
        userParkingRepository = repo,
        vehicleRepository = vehicles,
        zoneRepository = FakeZoneRepository(),
        geofenceService = geofence,
        notificationPort = notification,
        enrichmentScheduler = enrichment,
        authRepository = auth,
        config = config,
        departureEventBus = bus,
    )
}

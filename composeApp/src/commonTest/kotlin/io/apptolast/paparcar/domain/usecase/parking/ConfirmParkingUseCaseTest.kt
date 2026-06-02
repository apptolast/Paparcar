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
import io.apptolast.paparcar.fakes.FakeParkingSyncScheduler
import io.apptolast.paparcar.fakes.FakeZoneRepository
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

    /** Default vehicle present in every fixture unless a test explicitly overrides — matches
     *  the production invariant (FLOW-001 ensures the user always has a default before the
     *  detection service can fire). [AUTH-001] */
    private val defaultVehicle = Vehicle(id = "v-1", userId = "user-42", sizeCategory = VehicleSize.MEDIUM)

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `should save session when called with valid location`() = runTest {
        val repo = FakeUserParkingRepository()
        val useCase = buildUseCase(repo = repo)

        val result = useCase(location, detectionReliability = 0.9f)

        assertTrue(result.isSuccess)
        assertEquals(1, repo.saveSessionCallCount)
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
    fun `should schedule parking sync after successful save with null previous when none existed`() = runTest {
        val parkingSync = FakeParkingSyncScheduler()
        val useCase = buildUseCase(parkingSync = parkingSync)

        val result = useCase(location, detectionReliability = 0.9f)

        assertTrue(result.isSuccess)
        assertEquals(1, parkingSync.scheduleCallCount)
        assertNull(parkingSync.scheduleCalls.first().previousSessionId)
    }

    @Test
    fun `should pass previous session id to parking sync when a session was already active`() = runTest {
        // FakeUserParkingRepository scopes "previous active" by vehicleId since MULTI-PARKING-001 —
        // mirror the default vehicle so the fake's match logic fires.
        val previousSession = io.apptolast.paparcar.domain.model.UserParking(
            id = "previous-session-id",
            userId = "user-42",
            vehicleId = defaultVehicle.id,
            location = location,
            isActive = true,
        )
        val repo = FakeUserParkingRepository(initialSession = previousSession)
        val parkingSync = FakeParkingSyncScheduler()
        val useCase = buildUseCase(repo = repo, parkingSync = parkingSync)

        val result = useCase(location, detectionReliability = 0.9f)

        assertTrue(result.isSuccess)
        assertEquals(1, parkingSync.scheduleCallCount)
        assertEquals("previous-session-id", parkingSync.scheduleCalls.first().previousSessionId)
    }

    @Test
    fun `should NOT schedule parking sync when save fails`() = runTest {
        val parkingSync = FakeParkingSyncScheduler()
        val repo = FakeUserParkingRepository().apply {
            saveSessionResult = Result.failure(RuntimeException("DB error"))
        }
        val useCase = buildUseCase(repo = repo, parkingSync = parkingSync)

        val result = useCase(location, detectionReliability = 0.9f)

        assertTrue(result.isFailure)
        assertEquals(0, parkingSync.scheduleCallCount)
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
            saveSessionResult = Result.failure(RuntimeException("DB error"))
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
            saveSessionResult = Result.failure(RuntimeException("DB error"))
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
            saveSessionResult = Result.failure(RuntimeException("DB error"))
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
        val vehicle = Vehicle(id = "v-1", userId = "user-42", sizeCategory = VehicleSize.LARGE)
        val useCase = buildUseCase(repo = repo, vehicles = FakeVehicleRepository(vehicle))

        useCase(location, detectionReliability = 0.9f)

        val savedSession = repo.getActiveSession()
        assertNotNull(savedSession)
        assertEquals(VehicleSize.LARGE, savedSession.sizeCategory)
    }

    @Test
    fun `should use explicit sizeCategory when provided even if vehicle has different size`() = runTest {
        val repo = FakeUserParkingRepository()
        val vehicle = Vehicle(id = "v-1", userId = "user-42", sizeCategory = VehicleSize.LARGE)
        val useCase = buildUseCase(repo = repo, vehicles = FakeVehicleRepository(vehicle))

        useCase(location, detectionReliability = 0.9f, sizeCategory = VehicleSize.MOTO)

        val savedSession = repo.getActiveSession()
        assertNotNull(savedSession)
        assertEquals(VehicleSize.MOTO, savedSession.sizeCategory)
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
        assertEquals(0, repo.saveSessionCallCount)
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
            sizeCategory = VehicleSize.VAN,
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
        val secondary = Vehicle(id = "v-2", userId = "user-42", sizeCategory = VehicleSize.VAN)
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
        assertEquals(VehicleSize.VAN, savedSession.sizeCategory)
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
        assertEquals(0, repo.saveSessionCallCount)
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
        val vehicle = Vehicle(id = "v-1", userId = "user-42", sizeCategory = VehicleSize.MOTO)
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
        val vehicle = Vehicle(id = "v-1", userId = "user-42", sizeCategory = VehicleSize.VAN)
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

        useCase(locationWith10mAccuracy, detectionReliability = 0.9f, sizeCategory = VehicleSize.MEDIUM)

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

        useCase(highInaccuracy, detectionReliability = 0.9f, sizeCategory = VehicleSize.VAN)

        assertEquals(config.geofenceMaxRadiusMeters, geofence.lastCreatedRadiusMeters)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildUseCase(
        repo: FakeUserParkingRepository = FakeUserParkingRepository(),
        vehicles: FakeVehicleRepository = FakeVehicleRepository(defaultVehicle),
        geofence: FakeGeofenceManager = FakeGeofenceManager(),
        notification: FakeAppNotificationManager = FakeAppNotificationManager(),
        enrichment: FakeParkingEnrichmentScheduler = FakeParkingEnrichmentScheduler(),
        parkingSync: FakeParkingSyncScheduler = FakeParkingSyncScheduler(),
        auth: FakeAuthRepository = FakeAuthRepository(initialSession = session),
        config: ParkingDetectionConfig = ParkingDetectionConfig(),
    ) = ConfirmParkingUseCase(
        userParkingRepository = repo,
        vehicleRepository = vehicles,
        zoneRepository = FakeZoneRepository(),
        geofenceService = geofence,
        notificationPort = notification,
        enrichmentScheduler = enrichment,
        parkingSyncScheduler = parkingSync,
        authRepository = auth,
        config = config,
    )
}

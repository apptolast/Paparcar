@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.detection.ArmLabel
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.fakes.FakeAppNotificationManager
import com.rndeveloper.paparcar.fakes.FakeActivityRecognitionManager
import com.rndeveloper.paparcar.fakes.FakeDepartureEventBus
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeGeofenceManager
import com.rndeveloper.paparcar.fakes.FakeParkingEnrichmentScheduler
import com.rndeveloper.paparcar.fakes.FakeZoneRepository
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.detection.ports.DrivingRouteStore
import com.rndeveloper.paparcar.domain.util.PolylineCodec
import com.rndeveloper.paparcar.fakes.FakeDrivingRouteStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

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
    // isActive = true: the default vehicle IS the active one (getActiveVehicle returns the isActive=1
    // row in production). Fence ownership now reads this flag. [VEH-ACTIVE-FENCE-001]
    private val defaultVehicle = Vehicle(id = "v-1", userId = "user-42", sizeCategory = VehicleSize.MEDIUM_SUV, isActive = true)

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `should save session when called with valid location`() = runTest {
        val repo = FakeUserParkingRepository()
        val useCase = buildUseCase(repo = repo)

        val result = useCase(location, detectionReliability = 0.9f, sealPoint = null)

        assertTrue(result.isSuccess)
        assertEquals(1, repo.saveNewParkingSessionCallCount)
    }

    @Test
    fun `should schedule enrichment after successful save`() = runTest {
        val enrichment = FakeParkingEnrichmentScheduler()
        val useCase = buildUseCase(enrichment = enrichment)

        val result = useCase(location, detectionReliability = 0.9f, sealPoint = null)

        assertTrue(result.isSuccess)
        assertEquals(1, enrichment.scheduleCallCount)
    }

    @Test
    fun `should create geofence after successful save`() = runTest {
        val geofence = FakeGeofenceManager()
        val useCase = buildUseCase(geofence = geofence)

        val result = useCase(location, detectionReliability = 0.9f, sealPoint = null)

        assertTrue(result.isSuccess)
        assertEquals(1, geofence.createGeofenceCallCount)
    }

    @Test
    fun `should not post any notification - the caller's responsibility`() = runTest {
        // [CONFIRM-NO-NOTIF-CLEANUP] Notification responsibility lives in the caller now:
        // coordinator owns showParkingSavedConfirm (REVERT card), BT detector + HomeViewModel
        // own showParkingSaved (legacy tap-to-open). The use case never posts.
        val notification = FakeAppNotificationManager()
        val useCase = buildUseCase(notification = notification)

        val result = useCase(location, detectionReliability = 0.9f, sealPoint = null)

        assertTrue(result.isSuccess)
        assertEquals(0, notification.parkingSpotSavedCallCount)
        assertEquals(0, notification.parkingSavedConfirmCallCount)
    }

    @Test
    fun `should not create a geofence when the parked vehicle is inactive`() = runTest {
        // [VEH-ACTIVE-FENCE-001] An inactive, non-BT vehicle owns no OS fence — its session keeps the
        // pin/TTL/safety-net, and the fence is (re)registered when the user declares this car active.
        val repo = FakeUserParkingRepository()
        val geofence = FakeGeofenceManager()
        val inactive = Vehicle(id = "v-inactive", userId = "user-42", sizeCategory = VehicleSize.MEDIUM_SUV)
        val useCase = buildUseCase(
            repo = repo,
            geofence = geofence,
            vehicles = FakeVehicleRepository(defaultVehicle = defaultVehicle, extraVehicles = listOf(inactive)),
        )

        val result = useCase(location, detectionReliability = 0.9f, vehicleId = "v-inactive", sealPoint = null)

        assertTrue(result.isSuccess)
        assertEquals(1, repo.saveNewParkingSessionCallCount, "session still saved (pin + TTL kept)")
        assertEquals(0, geofence.createGeofenceCallCount, "inactive vehicle registers no fence")
    }

    @Test
    fun `should create a geofence for a Bluetooth-paired vehicle even when inactive`() = runTest {
        // The MAC is identity: a paired car is automatic regardless of the active flag. [DET-TIERS-001]
        val repo = FakeUserParkingRepository()
        val geofence = FakeGeofenceManager()
        val paired = Vehicle(
            id = "v-bt", userId = "user-42", sizeCategory = VehicleSize.MEDIUM_SUV,
            bluetoothDeviceId = "AA:BB:CC:DD:EE:FF",
        )
        val useCase = buildUseCase(
            repo = repo,
            geofence = geofence,
            vehicles = FakeVehicleRepository(defaultVehicle = defaultVehicle, extraVehicles = listOf(paired)),
        )

        val result = useCase(location, detectionReliability = 0.9f, vehicleId = "v-bt", sealPoint = null)

        assertTrue(result.isSuccess)
        assertEquals(1, geofence.createGeofenceCallCount)
    }

    @Test
    fun `should use same ID for session and geofence`() = runTest {
        val repo = FakeUserParkingRepository()
        val geofence = FakeGeofenceManager()
        val useCase = buildUseCase(repo = repo, geofence = geofence)

        val result = useCase(location, detectionReliability = 0.9f, sealPoint = null)

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

        val result = useCase(location, detectionReliability = 0.9f, sealPoint = null)

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

        val result = useCase(location, detectionReliability = 0.9f, sealPoint = null)

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

        val result = useCase(location, detectionReliability = 0.9f, sealPoint = null)

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

        val result = useCase(location, detectionReliability = 0.9f, sealPoint = null)

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

        useCase(location, detectionReliability = 0.9f, sealPoint = null)

        val savedSession = repo.getActiveSession()
        assertNotNull(savedSession)
        assertEquals(VehicleSize.LARGE_SEDAN, savedSession.sizeCategory)
    }

    @Test
    fun `should use explicit sizeCategory when provided even if vehicle has different size`() = runTest {
        val repo = FakeUserParkingRepository()
        val vehicle = Vehicle(id = "v-1", userId = "user-42", sizeCategory = VehicleSize.LARGE_SEDAN)
        val useCase = buildUseCase(repo = repo, vehicles = FakeVehicleRepository(vehicle))

        useCase(location, detectionReliability = 0.9f, sizeCategory = VehicleSize.MOTORCYCLE, sealPoint = null)

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

        val result = useCase(location, detectionReliability = 0.9f, sealPoint = null)

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

        val result = useCase(location, detectionReliability = 0.9f, vehicleId = "v-2", sealPoint = null)

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

        useCase(location, detectionReliability = 0.9f, vehicleId = "v-2", sealPoint = null)

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

        val result = useCase(location, detectionReliability = 0.9f, vehicleId = "v-missing", sealPoint = null)

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

        val result = useCase(location, detectionReliability = 0.9f, vehicleId = null, sealPoint = null)

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

        val result = noAuthCase(location, detectionReliability = 0.9f, sealPoint = null)

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

        useCase(zeroAccuracy, detectionReliability = 0.9f, sealPoint = null)

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

        useCase(zeroAccuracy, detectionReliability = 0.9f, sealPoint = null)

        assertEquals(config.geofenceRadiusVanMeters, geofence.lastCreatedRadiusMeters)
    }

    @Test
    fun `should pad radius with GPS accuracy`() = runTest {
        val geofence = FakeGeofenceManager()
        val config = ParkingDetectionConfig()
        val useCase = buildUseCase(geofence = geofence, config = config)
        val locationWith10mAccuracy = location.copy(accuracy = 10f)

        useCase(locationWith10mAccuracy, detectionReliability = 0.9f, sizeCategory = VehicleSize.MEDIUM_SUV, sealPoint = null)

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

        useCase(highInaccuracy, detectionReliability = 0.9f, sizeCategory = VehicleSize.VAN_HIGH, sealPoint = null)

        assertEquals(config.geofenceMaxRadiusMeters, geofence.lastCreatedRadiusMeters)
    }

    // ── DepartureEventBus reset [BUG-WALK-DEPART-001] ────────────────────────

    @Test
    fun `should reset departure event bus after successful parking confirmation`() = runTest {
        val bus = FakeDepartureEventBus(initialTimestamp = Clock.System.now().toEpochMilliseconds() - 60_000L)
        val useCase = buildUseCase(bus = bus)

        useCase(location, detectionReliability = 0.9f, sealPoint = null)

        assertEquals(1, bus.resetCount)
    }

    @Test
    fun `should not reset departure event bus when parking save fails`() = runTest {
        val bus = FakeDepartureEventBus(initialTimestamp = Clock.System.now().toEpochMilliseconds() - 60_000L)
        val failingRepo = FakeUserParkingRepository().apply {
            saveNewParkingSessionResult = Result.failure(RuntimeException("db error"))
        }
        val useCase = buildUseCase(repo = failingRepo, bus = bus)

        useCase(location, detectionReliability = 0.9f, sealPoint = null)

        assertEquals(0, bus.resetCount)
    }

    // ── Repark-plausibility guard [DET-SOLID-001] ─────────────────────────────

    private fun recentActiveSession(
        lat: Double = location.latitude,
        lon: Double = location.longitude,
        ageMs: Long = 2 * 60_000L,
    ) = com.rndeveloper.paparcar.domain.model.UserParking(
        id = "prev-session",
        userId = "user-42",
        vehicleId = defaultVehicle.id,
        location = GpsPoint(lat, lon, 5f, Clock.System.now().toEpochMilliseconds() - ageMs, 0f),
        geofenceId = "prev-session",
        isActive = true,
    )

    // ── Assertion guard [DET-ASSERTION-OUTRANKS-INFERENCE-001] ────────────────

    /** The same recent nearby session, but this one the USER put there. */
    private fun assertedActiveSession(ageMs: Long = 2 * 60_000L + 53_000L) =
        recentActiveSession(ageMs = ageMs).copy(detectionReliability = 1.0f)

    @Test
    fun `should refuse to relocate a user-asserted pin when the answer is the only new evidence`() = runTest {
        // Field 2026-08-24 20:51, Oppo/Calle Fragua. The "Sí" arrives with reliability 1.0 — which
        // used to bypass the repark guard outright — and the session's PEAK speed (5,33 m/s, one
        // fix out of 25) sat just above minimumTripSpeedMps, so the guard's other clause would have
        // let it through too. The pin the user confirmed 2 min 53 s earlier stays.
        val repo = FakeUserParkingRepository(initialSession = assertedActiveSession())
        val useCase = buildUseCase(repo = repo)

        val result = useCase(
            location,
            detectionReliability = 1.0f,
            tripMaxSpeedMps = 5.33f,
            sessionSawDriving = false,
            detectionPath = "user",
            sealPoint = null,
        )

        assertIs<PaparcarError.Parking.ImplausibleRepark>(result.exceptionOrNull())
        assertEquals(0, repo.saveNewParkingSessionCallCount)
    }

    @Test
    fun `should allow relocating a user-asserted pin when the session measured sustained driving`() = runTest {
        // A genuine re-park: the car drove. Measured movement is not an inference, so it may
        // overrule the earlier assertion — the single escape hatch, and it must stay open.
        val repo = FakeUserParkingRepository(initialSession = assertedActiveSession())
        val useCase = buildUseCase(repo = repo)

        val result = useCase(
            location,
            detectionReliability = 1.0f,
            tripMaxSpeedMps = 15f,
            sessionSawDriving = true,
            detectionPath = "user",
            sealPoint = null,
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun `should never block a hand-placed pin over a user-asserted one`() = runTest {
        // The user chose this POSITION on the map. Nothing in this file may argue with that.
        val repo = FakeUserParkingRepository(initialSession = assertedActiveSession())
        val useCase = buildUseCase(repo = repo)

        val result = useCase(
            location,
            detectionReliability = 1.0f,
            spotType = com.rndeveloper.paparcar.domain.model.SpotType.MANUAL_REPORT,
            tripMaxSpeedMps = 1.2f,
            sessionSawDriving = false,
            detectionPath = "manual",
            sealPoint = null,
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun `should leave callers without session provenance untouched`() = runTest {
        // BT / external callers pass no tripMaxSpeedMps — the same exemption the repark guard
        // grants them. The deterministic lane proves the car moved by its own means.
        val repo = FakeUserParkingRepository(initialSession = assertedActiveSession())
        val useCase = buildUseCase(repo = repo)

        val result = useCase(location, detectionReliability = 0.95f, detectionPath = "bt", sealPoint = null)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `should reject implausible repark - recent nearby active session and no driving observed`() = runTest {
        val repo = FakeUserParkingRepository(initialSession = recentActiveSession())
        val useCase = buildUseCase(repo = repo)

        val result = useCase(location, detectionReliability = 0.9f, tripMaxSpeedMps = 1.2f, sealPoint = null)

        assertIs<PaparcarError.Parking.ImplausibleRepark>(result.exceptionOrNull())
        assertEquals(0, repo.saveNewParkingSessionCallCount)
    }

    @Test
    fun `should allow repark when session observed driving speed`() = runTest {
        val repo = FakeUserParkingRepository(initialSession = recentActiveSession())
        val useCase = buildUseCase(repo = repo)

        val result = useCase(location, detectionReliability = 0.9f, tripMaxSpeedMps = 8f, sealPoint = null)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `should allow repark when previous session is far away`() = runTest {
        // ~1.1 km north — outside reparkPlausibilityRadiusMeters (300 m).
        val repo = FakeUserParkingRepository(initialSession = recentActiveSession(lat = location.latitude + 0.01))
        val useCase = buildUseCase(repo = repo)

        val result = useCase(location, detectionReliability = 0.9f, tripMaxSpeedMps = 1.2f, sealPoint = null)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `should allow repark when previous session is old`() = runTest {
        val repo = FakeUserParkingRepository(initialSession = recentActiveSession(ageMs = 30 * 60_000L))
        val useCase = buildUseCase(repo = repo)

        val result = useCase(location, detectionReliability = 0.9f, tripMaxSpeedMps = 1.2f, sealPoint = null)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `should bypass guard when arm evidence is verified departure`() = runTest {
        val repo = FakeUserParkingRepository(initialSession = recentActiveSession())
        val useCase = buildUseCase(repo = repo)

        val result = useCase(
            location,
            detectionReliability = 0.9f,
            tripMaxSpeedMps = 1.2f,
            armEvidence = ArmLabel.VERIFIED_SPEED,
            sealPoint = null,
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun `should bypass guard for user-confirmed parking`() = runTest {
        val repo = FakeUserParkingRepository(initialSession = recentActiveSession())
        val useCase = buildUseCase(repo = repo)

        val result = useCase(location, detectionReliability = 1.0f, tripMaxSpeedMps = 1.2f, sealPoint = null)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `should bypass guard when caller has no session provenance`() = runTest {
        // BT strategy / external callers pass no tripMaxSpeedMps — the guard needs provenance.
        val repo = FakeUserParkingRepository(initialSession = recentActiveSession())
        val useCase = buildUseCase(repo = repo)

        val result = useCase(location, detectionReliability = 0.95f, tripMaxSpeedMps = null, sealPoint = null)

        assertTrue(result.isSuccess)
    }

    // ── Geofence-registration invariant [DET-SOLID-001] ───────────────────────

    @Test
    fun `should schedule janitor restore when geofence registration fails`() = runTest {
        val geofence = FakeGeofenceManager().apply { createResult = Result.failure(RuntimeException("gms unavailable")) }
        val scheduler = RecordingParkingSyncScheduler()
        val useCase = buildUseCase(geofence = geofence, scheduler = scheduler)

        val result = useCase(location, detectionReliability = 0.9f, sealPoint = null)

        assertTrue(result.isSuccess, "a failed geofence registration must not fail the durable save")
        assertEquals(1, scheduler.geofenceRestoreCount)
    }

    private class RecordingParkingSyncScheduler : com.rndeveloper.paparcar.domain.service.ParkingSyncScheduler {
        var geofenceRestoreCount = 0
        override fun enqueueGeofenceRestore() { geofenceRestoreCount++ }
        override fun enqueueSaveNewParkingSession(session: com.rndeveloper.paparcar.domain.model.UserParking, previousSessionId: String?) {}
        override fun enqueueClearActiveParkingSession(sessionId: String) {}
        override fun enqueueUpdateParkingSessionAddressAndPlace(
            sessionId: String,
            address: com.rndeveloper.paparcar.domain.model.AddressInfo?,
            placeInfo: com.rndeveloper.paparcar.domain.model.PlaceInfo?,
        ) {}
        override fun enqueueDeleteVehicleRemote(vehicleId: String) {}
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
        scheduler: com.rndeveloper.paparcar.domain.service.ParkingSyncScheduler? = null,
        routeStore: DrivingRouteStore? = null,
    ) = ConfirmParkingUseCase(
        userParkingRepository = repo,
        vehicleRepository = vehicles,
        zoneRepository = FakeZoneRepository(),
        geofenceService = geofence,
        enrichmentScheduler = enrichment,
        authRepository = auth,
        config = config,
        departureEventBus = bus,
        parkingSyncScheduler = scheduler,
        drivingRouteStore = routeStore,
    )

    // ── Driven route snapshot [DET-ROUTE-TRACK-001] ─────────────────────────────

    @Test
    fun `should snapshot the recorded route onto the saved parking and clear the store`() = runTest {
        val now = Clock.System.now().toEpochMilliseconds()
        // Spans ~470 m out-and-back, ending AT the anchor — above MIN_ROUTE_EXTENT_METERS without
        // triggering the anchor append. [ROUTE-GAP-HONEST-001]
        val route = listOf(
            GpsPoint(40.4160, -3.7040, 5f, now - 120_000, 0f),
            GpsPoint(40.4185, -3.7038, 5f, now - 60_000, 0f),
            GpsPoint(40.41678, -3.70377, 5f, now - 2_000, 0f),
        )
        val store = FakeDrivingRouteStore(initial = route)
        val useCase = buildUseCase(routeStore = store)

        val saved = useCase(location, detectionReliability = 0.9f, sealPoint = null).getOrNull()
        assertNotNull(saved)
        assertEquals(PolylineCodec.encode(route), saved.routePolyline)
        assertTrue(store.points().isEmpty(), "the live route store must be cleared after the snapshot")
    }

    @Test
    fun `should not attach a stale route left over from a previous aborted trip`() = runTest {
        val old = Clock.System.now().toEpochMilliseconds() - 60 * 60_000L // 1 h ago
        val stale = listOf(
            GpsPoint(40.0, -3.0, 5f, old, 0f),
            GpsPoint(40.001, -3.0, 5f, old + 1_000, 0f),
        )
        val useCase = buildUseCase(routeStore = FakeDrivingRouteStore(initial = stale))

        val saved = useCase(location, detectionReliability = 0.9f, sealPoint = null).getOrNull()
        assertNotNull(saved)
        assertNull(saved.routePolyline)
    }

    @Test
    fun `should save with no route when the store is empty - BT park with no drive tracked`() = runTest {
        val useCase = buildUseCase(routeStore = FakeDrivingRouteStore())

        val saved = useCase(location, detectionReliability = 0.95f, sealPoint = null).getOrNull()
        assertNotNull(saved)
        assertNull(saved.routePolyline)
    }

    @Test
    fun `should not attach a stub route shorter than a real drive`() = runTest {
        // Field 2026-08-14 22:51: a safety-net backfill pin carried a 40 m 5-point "route" (the
        // wake-up's own fixes) and drew a fake origin next to the pin. [ROUTE-GAP-HONEST-001]
        val now = Clock.System.now().toEpochMilliseconds()
        val stub = listOf(
            GpsPoint(40.41600, -3.7040, 5f, now - 30_000, 0f),
            GpsPoint(40.41615, -3.7041, 5f, now - 15_000, 0f),
            GpsPoint(40.41630, -3.7042, 5f, now - 2_000, 0f),
        )
        val useCase = buildUseCase(routeStore = FakeDrivingRouteStore(initial = stub))

        val saved = useCase(location, detectionReliability = 0.5f, sealPoint = null).getOrNull()
        assertNotNull(saved)
        assertNull(saved.routePolyline, "expected no route attached for a ~40 m stub")
    }

    // ── Route origin seed [ROUTE-QUALITY-001] ───────────────────────────────────

    // Spans ~470 m out-and-back, ending AT the test anchor — above MIN_ROUTE_EXTENT_METERS while
    // keeping every origin-seed/anchor assertion untouched. [ROUTE-GAP-HONEST-001]
    private fun freshRoute(now: Long) = listOf(
        GpsPoint(40.4160, -3.7040, 5f, now - 120_000, 0f),
        GpsPoint(40.4185, -3.7038, 5f, now - 60_000, 0f),
        GpsPoint(40.41678, -3.70377, 5f, now - 2_000, 0f),
    )

    @Test
    fun `should prepend the previous parking as the route origin`() = runTest {
        // The store's first element is the first fix AFTER arming — typically hundreds of metres
        // into the drive. The trip's true origin is the vehicle's still-active previous parking.
        val now = Clock.System.now().toEpochMilliseconds()
        val route = freshRoute(now)
        val previous = recentActiveSession(lat = 40.4115, lon = -3.7040) // ~500 m before the first fix
        val useCase = buildUseCase(
            repo = FakeUserParkingRepository(initialSession = previous),
            routeStore = FakeDrivingRouteStore(initial = route),
        )

        val saved = useCase(location, detectionReliability = 0.9f, sealPoint = null).getOrNull()
        assertNotNull(saved)
        assertEquals(PolylineCodec.encode(listOf(previous.location) + route), saved.routePolyline)
    }

    @Test
    fun `should seed the origin from the released previous session when the departure already freed the spot`() = runTest {
        // [ROUTE-START-AT-CAR-001] Field 2026-08-17 23:57 (Redmi): on a healthy trip the verified
        // departure releases the previous session minutes after driving off, so an active-only
        // lookup finds nothing at confirm time and the saved route was born ~130 m past the pin.
        // The car still left from that pin — a released session seeds the origin all the same.
        val now = Clock.System.now().toEpochMilliseconds()
        val route = freshRoute(now)
        val previous = recentActiveSession(lat = 40.4115, lon = -3.7040).copy(isActive = false)
        val useCase = buildUseCase(
            repo = FakeUserParkingRepository(initialSession = previous),
            routeStore = FakeDrivingRouteStore(initial = route),
        )

        val saved = useCase(location, detectionReliability = 0.9f, sealPoint = null).getOrNull()
        assertNotNull(saved)
        assertEquals(PolylineCodec.encode(listOf(previous.location) + route), saved.routePolyline)
    }

    @Test
    fun `should not prepend an origin further than the plausibility ceiling`() = runTest {
        // A previous parking across town (stale session) is another story — never stretch the line.
        val now = Clock.System.now().toEpochMilliseconds()
        val route = freshRoute(now)
        val previous = recentActiveSession(lat = 40.3600, lon = -3.7040) // ~6 km away
        val useCase = buildUseCase(
            repo = FakeUserParkingRepository(initialSession = previous),
            routeStore = FakeDrivingRouteStore(initial = route),
        )

        val saved = useCase(location, detectionReliability = 0.9f, sealPoint = null).getOrNull()
        assertNotNull(saved)
        assertEquals(PolylineCodec.encode(route), saved.routePolyline)
    }

    // ── Route ends at the car [ROUTE-END-AT-CAR-001] ────────────────────────────

    /** Driven fixes ending at the stop; the anchor's fix timestamp marks the end of driving. */
    private fun drivenLeg(now: Long) = listOf(
        GpsPoint(40.4160, -3.7040, 5f, now - 120_000, 0f),
        GpsPoint(40.4175, -3.7038, 5f, now - 60_000, 0f),
        GpsPoint(40.4167, -3.7037, 5f, now - 30_000, 0f),
    )

    /** Pedestrian fixes the store kept sampling AFTER the stop (GPS live during egress). */
    private fun walkTail(now: Long) = listOf(
        GpsPoint(40.4170, -3.7030, 5f, now - 20_000, 0f),
        GpsPoint(40.4174, -3.7024, 5f, now - 2_000, 0f),
    )

    @Test
    fun `should drop the pedestrian tail after the stop and end the polyline at the parking anchor`() = runTest {
        // Field 2026-08-13 17:33, Calle Mar de Alborán: the stored line continued past the car,
        // following the user's walk after parking. The anchor's fix timestamp is the measured end
        // of driving — everything after it is the walk, and the pin caps the line.
        val now = Clock.System.now().toEpochMilliseconds()
        val drive = drivenLeg(now)
        val anchor = GpsPoint(40.41675, -3.70335, 8f, now - 30_000, 0f) // ~30 m past the last driven fix
        val useCase = buildUseCase(routeStore = FakeDrivingRouteStore(initial = drive + walkTail(now)))

        val saved = useCase(anchor, detectionReliability = 0.9f, sealPoint = null).getOrNull()

        assertNotNull(saved)
        assertEquals(PolylineCodec.encode(drive + anchor), saved.routePolyline)
    }

    @Test
    fun `should keep a route without a pedestrian tail intact`() = runTest {
        val now = Clock.System.now().toEpochMilliseconds()
        val route = freshRoute(now)
        // Pin within the append floor of the last fix, stamped after the drive → nothing to trim,
        // nothing to append.
        val anchor = GpsPoint(40.416775, -3.703790, 8f, now - 1_000, 0f)
        val useCase = buildUseCase(routeStore = FakeDrivingRouteStore(initial = route))

        val saved = useCase(anchor, detectionReliability = 0.9f, sealPoint = null).getOrNull()

        assertNotNull(saved)
        assertEquals(PolylineCodec.encode(route), saved.routePolyline)
    }

    @Test
    fun `should still seed the origin when the pedestrian tail is trimmed`() = runTest {
        // Tail trim must not regress ROUTE-QUALITY-001: the previous parking still opens the line.
        val now = Clock.System.now().toEpochMilliseconds()
        val drive = drivenLeg(now)
        val anchor = GpsPoint(40.41675, -3.70335, 8f, now - 30_000, 0f)
        val previous = recentActiveSession(lat = 40.4115, lon = -3.7040) // ~500 m before the first fix
        val useCase = buildUseCase(
            repo = FakeUserParkingRepository(initialSession = previous),
            routeStore = FakeDrivingRouteStore(initial = drive + walkTail(now)),
        )

        val saved = useCase(anchor, detectionReliability = 0.9f, sealPoint = null).getOrNull()

        assertNotNull(saved)
        assertEquals(PolylineCodec.encode(listOf(previous.location) + drive + anchor), saved.routePolyline)
    }

    @Test
    fun `should not prepend an origin that is effectively the first fix already`() = runTest {
        val now = Clock.System.now().toEpochMilliseconds()
        val route = freshRoute(now)
        val previous = recentActiveSession(lat = 40.4160, lon = -3.7040) // same spot as the first fix
        val useCase = buildUseCase(
            repo = FakeUserParkingRepository(initialSession = previous),
            routeStore = FakeDrivingRouteStore(initial = route),
        )

        val saved = useCase(location, detectionReliability = 0.9f, sealPoint = null).getOrNull()
        assertNotNull(saved)
        assertEquals(PolylineCodec.encode(route), saved.routePolyline)
    }
}

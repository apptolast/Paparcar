@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.detection.DetectionPath
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.fakes.FakeDepartureEventBus
import com.rndeveloper.paparcar.fakes.FakeGeofenceManager
import com.rndeveloper.paparcar.fakes.FakeParkingEnrichmentScheduler
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
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

    // ── Provenance [PARK-A-PIN-MUST-SAY-WHO-PLACED-IT-001] ────────────────────

    /**
     * A dragged pin sits where the USER put it, so the provenance the detector left on it is a lie.
     * Field 2026-08-30 19:32:44 (Redmi): the pin still read `unattended_zone_gap_anchor` at
     * reliability 0.5 after the user corrected it by hand.
     *
     * The three fields move together — path, reliability and the doubt radius — because they answer
     * the same question. Asserting them in one test is deliberate: a drag that rewrote two of the
     * three would be a new way to lie.
     */
    @Test
    fun should_rewriteProvenanceToUserMoved_when_theUserDragsThePin() = runTest {
        val session = existingSession().copy(
            detectionPath = DetectionPath.UnattendedZone("gap_anchor").label,
            detectionReliability = 0.5f,
            zoneRadiusMeters = 250f,
        )
        val repo = FakeUserParkingRepository(initialSession = session)
        val useCase = buildUseCase(repo = repo)

        val moved = useCase(session.id, newLocation).getOrNull()

        assertNotNull(moved)
        assertEquals(DetectionPath.UserMovedPin.label, moved.detectionPath, "who placed it")
        assertEquals(1.0f, moved.detectionReliability, "a pin a human pointed at is ground truth")
        assertEquals(null, moved.zoneRadiusMeters, "the doubt was about where the car was; the user just answered it")
    }

    /**
     * `user_moved` must be distinguishable from `manual`: one pin was born by hand, the other was
     * born by the detector and then corrected — and only the second is a detection failure worth
     * chasing. Collapsing them would erase exactly the signal a field trace needs.
     */
    @Test
    fun should_notClaimTheDraggedPinWasHandPlacedFromTheStart() = runTest {
        val session = existingSession()
        val repo = FakeUserParkingRepository(initialSession = session)
        val useCase = buildUseCase(repo = repo)

        val moved = useCase(session.id, newLocation).getOrNull()

        assertNotNull(moved)
        assertTrue(
            moved.detectionPath != DetectionPath.ManualPin.label,
            "a corrected pin is not a hand-placed one — keep the two paths apart",
        )
    }

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

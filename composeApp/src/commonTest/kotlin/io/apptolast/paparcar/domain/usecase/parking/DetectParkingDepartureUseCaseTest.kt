package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.fakes.FakeDepartureEventBus
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class DetectParkingDepartureUseCaseTest {

    private val config = ParkingDetectionConfig()

    private val activeSession = UserParking(
        id = "session-1",
        userId = "user-1",
        location = GpsPoint(40.4, -3.7, 10f, 0L, 0f),
        geofenceId = "session-1",
        isActive = true,
    )

    private val exitTimestamp = 1_000_000L
    private val speedAboveThreshold = config.minimumDepartureSpeedKmh + 1f
    private val speedBelowThreshold = config.minimumDepartureSpeedKmh - 1f

    // ── Signal 1: no active session ───────────────────────────────────────────

    @Test
    fun `should return Rejected when no active parking session exists`() = runTest {
        val repo = FakeUserParkingRepository()  // empty → getActiveSession() = null
        val useCase = buildUseCase(repo = repo)

        val result = useCase(geofenceId = "session-1", exitTimestampMs = exitTimestamp, currentSpeedKmh = null)

        assertIs<DepartureDecision.Rejected>(result)
    }

    // ── Signal 2: geofence ID mismatch ────────────────────────────────────────

    @Test
    fun `should return Rejected when geofence ID does not match active session`() = runTest {
        val repo = FakeUserParkingRepository(activeSession)
        val useCase = buildUseCase(repo = repo)

        val result = useCase(geofenceId = "other-geofence", exitTimestampMs = exitTimestamp, currentSpeedKmh = null)

        assertIs<DepartureDecision.Rejected>(result)
    }

    // ── Signal 3: IN_VEHICLE_ENTER not yet arrived ────────────────────────────

    @Test
    fun `should return Inconclusive when no vehicleEnter and speed is null`() = runTest {
        val repo = FakeUserParkingRepository(activeSession)
        val bus = FakeDepartureEventBus(initialTimestamp = null)
        val useCase = buildUseCase(repo = repo, bus = bus)

        val result = useCase(geofenceId = activeSession.geofenceId!!, exitTimestampMs = exitTimestamp, currentSpeedKmh = null)

        assertIs<DepartureDecision.Inconclusive>(result)
    }

    @Test
    fun `should return Inconclusive when no vehicleEnter and speed is below threshold`() = runTest {
        val repo = FakeUserParkingRepository(activeSession)
        val bus = FakeDepartureEventBus(initialTimestamp = null)
        val useCase = buildUseCase(repo = repo, bus = bus)

        val result = useCase(geofenceId = activeSession.geofenceId!!, exitTimestampMs = exitTimestamp, currentSpeedKmh = speedBelowThreshold)

        assertIs<DepartureDecision.Inconclusive>(result)
    }

    @Test
    fun `should return Confirmed when no vehicleEnter but speed is above threshold`() = runTest {
        val repo = FakeUserParkingRepository(activeSession)
        val bus = FakeDepartureEventBus(initialTimestamp = null)
        val useCase = buildUseCase(repo = repo, bus = bus)

        val result = useCase(geofenceId = activeSession.geofenceId!!, exitTimestampMs = exitTimestamp, currentSpeedKmh = speedAboveThreshold)

        assertIs<DepartureDecision.Confirmed>(result)
    }

    // ── Signal 3: IN_VEHICLE_ENTER within time window ─────────────────────────

    @Test
    fun `should return Confirmed when vehicleEnter is within window and speed is above threshold`() = runTest {
        val repo = FakeUserParkingRepository(activeSession)
        val bus = FakeDepartureEventBus(initialTimestamp = exitTimestamp - 60_000L)  // 1 min before exit
        val useCase = buildUseCase(repo = repo, bus = bus)

        val result = useCase(geofenceId = activeSession.geofenceId!!, exitTimestampMs = exitTimestamp, currentSpeedKmh = speedAboveThreshold)

        assertIs<DepartureDecision.Confirmed>(result)
    }

    @Test
    fun `should return Confirmed when vehicleEnter is within window and speed is null`() = runTest {
        val repo = FakeUserParkingRepository(activeSession)
        val bus = FakeDepartureEventBus(initialTimestamp = exitTimestamp - 60_000L)
        val useCase = buildUseCase(repo = repo, bus = bus)

        val result = useCase(geofenceId = activeSession.geofenceId!!, exitTimestampMs = exitTimestamp, currentSpeedKmh = null)

        assertIs<DepartureDecision.Confirmed>(result)
    }

    @Test
    fun `should return Inconclusive when vehicleEnter is within window but speed is below threshold`() = runTest {
        val repo = FakeUserParkingRepository(activeSession)
        val bus = FakeDepartureEventBus(initialTimestamp = exitTimestamp - 60_000L)
        val useCase = buildUseCase(repo = repo, bus = bus)

        val result = useCase(geofenceId = activeSession.geofenceId!!, exitTimestampMs = exitTimestamp, currentSpeedKmh = speedBelowThreshold)

        assertIs<DepartureDecision.Inconclusive>(result)
    }

    // ── Signal 3: IN_VEHICLE_ENTER outside time window ────────────────────────

    @Test
    fun `should return Rejected when vehicleEnter is outside the time window`() = runTest {
        val repo = FakeUserParkingRepository(activeSession)
        val staleTimestamp = exitTimestamp - config.vehicleEnterWindowMs - 1L
        val bus = FakeDepartureEventBus(initialTimestamp = staleTimestamp)
        val useCase = buildUseCase(repo = repo, bus = bus)

        val result = useCase(geofenceId = activeSession.geofenceId!!, exitTimestampMs = exitTimestamp, currentSpeedKmh = null)

        assertIs<DepartureDecision.Rejected>(result)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildUseCase(
        repo: FakeUserParkingRepository = FakeUserParkingRepository(),
        bus: FakeDepartureEventBus = FakeDepartureEventBus(),
    ) = DetectParkingDepartureUseCase(
        userParkingRepository = repo,
        departureEventBus = bus,
        config = config,
    )
}

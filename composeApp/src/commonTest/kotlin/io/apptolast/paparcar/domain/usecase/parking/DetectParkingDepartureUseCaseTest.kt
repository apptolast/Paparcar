package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.fakes.FakeDepartureEventBus
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

    /** A sampled fix. Defaults sit AT the parked car with good accuracy at exit time. */
    private fun fix(
        speedKmh: Float,
        accuracyM: Float = 10f,
        latitude: Double = 40.4,
        longitude: Double = -3.7,
        timestamp: Long = exitTimestamp,
    ) = GpsPoint(latitude, longitude, accuracyM, timestamp, speedKmh / 3.6f)

    /** ~1.1 km north of the parked car — beyond pedestrian reach for any short window. */
    private fun farFix(speedKmh: Float, accuracyM: Float = 10f, timestamp: Long = exitTimestamp) =
        fix(speedKmh, accuracyM, latitude = 40.41, timestamp = timestamp)

    // ── Signal 1: no active session ───────────────────────────────────────────

    @Test
    fun `should return Rejected when no active parking session exists`() = runTest {
        val repo = FakeUserParkingRepository()  // empty → getActiveSession() = null
        val useCase = buildUseCase(repo = repo)

        val result = useCase(geofenceId = "session-1", exitTimestampMs = exitTimestamp, currentFix = null)

        assertIs<DepartureDecision.Rejected>(result)
    }

    // ── Signal 2: geofence ID mismatch ────────────────────────────────────────

    @Test
    fun `should return Rejected when geofence ID does not match active session`() = runTest {
        val repo = FakeUserParkingRepository(activeSession)
        val useCase = buildUseCase(repo = repo)

        val result = useCase(geofenceId = "other-geofence", exitTimestampMs = exitTimestamp, currentFix = null)

        assertIs<DepartureDecision.Rejected>(result)
    }

    // ── Signal 3: IN_VEHICLE_ENTER not yet arrived ────────────────────────────

    @Test
    fun `should return Inconclusive when no vehicleEnter and no fix`() = runTest {
        val repo = FakeUserParkingRepository(activeSession)
        val bus = FakeDepartureEventBus(initialTimestamp = null)
        val useCase = buildUseCase(repo = repo, bus = bus)

        val result = useCase(geofenceId = activeSession.geofenceId!!, exitTimestampMs = exitTimestamp, currentFix = null)

        assertIs<DepartureDecision.Inconclusive>(result)
    }

    @Test
    fun `should return Inconclusive when no vehicleEnter and speed is below threshold`() = runTest {
        val repo = FakeUserParkingRepository(activeSession)
        val bus = FakeDepartureEventBus(initialTimestamp = null)
        val useCase = buildUseCase(repo = repo, bus = bus)

        val result = useCase(geofenceId = activeSession.geofenceId!!, exitTimestampMs = exitTimestamp, currentFix = fix(speedBelowThreshold))

        assertIs<DepartureDecision.Inconclusive>(result)
    }

    @Test
    fun `should return Confirmed when no vehicleEnter but speed is above threshold`() = runTest {
        val repo = FakeUserParkingRepository(activeSession)
        val bus = FakeDepartureEventBus(initialTimestamp = null)
        val useCase = buildUseCase(repo = repo, bus = bus)

        val result = useCase(geofenceId = activeSession.geofenceId!!, exitTimestampMs = exitTimestamp, currentFix = fix(speedAboveThreshold))

        assertIs<DepartureDecision.Confirmed>(result)
    }

    @Test
    fun `should return Inconclusive when speed is above threshold but accuracy is degraded`() = runTest {
        // [DET-EXIT-TRUST-001] Field 2026-07-08 04:18 (Oppo): an acc=100 m cache jump reported
        // 21.6 km/h on a motionless phone. Speed without credible accuracy is not evidence.
        val repo = FakeUserParkingRepository(activeSession)
        val bus = FakeDepartureEventBus(initialTimestamp = null)
        val useCase = buildUseCase(repo = repo, bus = bus)

        val result = useCase(
            geofenceId = activeSession.geofenceId!!,
            exitTimestampMs = exitTimestamp,
            currentFix = fix(speedAboveThreshold, accuracyM = 100f),
        )

        assertIs<DepartureDecision.Inconclusive>(result)
    }

    // ── Signal 3: IN_VEHICLE_ENTER within time window ─────────────────────────

    @Test
    fun `should return Confirmed when vehicleEnter is within window and speed is above threshold`() = runTest {
        val repo = FakeUserParkingRepository(activeSession)
        val bus = FakeDepartureEventBus(initialTimestamp = exitTimestamp - 60_000L)  // 1 min before exit
        val useCase = buildUseCase(repo = repo, bus = bus)

        val result = useCase(geofenceId = activeSession.geofenceId!!, exitTimestampMs = exitTimestamp, currentFix = fix(speedAboveThreshold))

        assertIs<DepartureDecision.Confirmed>(result)
    }

    @Test
    fun `should return Inconclusive not Confirmed when vehicleEnter is within window but there is no fix`() = runTest {
        // [DET-RIDE-PROOF-001] AR alone never confirms: the ENTER is a nomination and only
        // measured movement is proof. The old branch confirmed on ENTER + null speed — that is
        // exactly the shape of a phantom ENTER on a fixless wake-up. Retry instead; without a
        // fix the boarding is not even admissible for the fall-through (fail closed).
        val repo = FakeUserParkingRepository(activeSession)
        val bus = FakeDepartureEventBus(initialTimestamp = exitTimestamp - 60_000L)
        val useCase = buildUseCase(repo = repo, bus = bus)

        val result = useCase(geofenceId = activeSession.geofenceId!!, exitTimestampMs = exitTimestamp, currentFix = null)

        val inconclusive = assertIs<DepartureDecision.Inconclusive>(result)
        assertEquals(false, inconclusive.admissibleBoarding)
    }

    @Test
    fun `should keep boarding admissible when slow but the position outran pedestrian reach`() = runTest {
        // Slow garage exit / stop at the first light: enter 1 min before exit, current sample
        // slow — but the phone is already 1.1 km from the car. Only a vehicle explains that:
        // the fall-through may confirm after retries. [DET-RIDE-PROOF-001]
        val repo = FakeUserParkingRepository(activeSession)
        val bus = FakeDepartureEventBus(initialTimestamp = exitTimestamp - 60_000L)
        val useCase = buildUseCase(repo = repo, bus = bus)

        val result = useCase(geofenceId = activeSession.geofenceId!!, exitTimestampMs = exitTimestamp, currentFix = farFix(speedBelowThreshold))

        val inconclusive = assertIs<DepartureDecision.Inconclusive>(result)
        assertEquals(true, inconclusive.admissibleBoarding)
    }

    @Test
    fun `should not admit a boarding whose displacement is pedestrian reachable`() = runTest {
        // Field 2026-07-09 11:53-11:55 (Redmi): phantom IN_VEHICLE ENTER while WALKING, exit at
        // 127 m, every sample at speed=0 — the old fall-through released the real spot. A
        // walkable displacement keeps the boarding inadmissible, so exhausted retries dismiss.
        val repo = FakeUserParkingRepository(activeSession)
        val bus = FakeDepartureEventBus(initialTimestamp = exitTimestamp - 14_000L)
        val useCase = buildUseCase(repo = repo, bus = bus)

        val result = useCase(
            geofenceId = activeSession.geofenceId!!,
            exitTimestampMs = exitTimestamp,
            // ~100 m from the car (0.0009° lat), stationary, good accuracy — comfortably
            // inside any fence-radius + pedestrian-reach envelope.
            currentFix = fix(0f, accuracyM = 12f, latitude = 40.4009),
        )

        val inconclusive = assertIs<DepartureDecision.Inconclusive>(result)
        assertEquals(false, inconclusive.admissibleBoarding)
    }

    @Test
    fun `should return Rejected when vehicleEnter happened AFTER the exit`() = runTest {
        // [DET-SOLID-001] An IN_VEHICLE_ENTER whose TRUE transition time is after the geofence
        // exit is a vehicle boarded OUTSIDE the radius (bus/taxi after walking out) — not
        // evidence the user drove their own car away. The old abs() window accepted it.
        val repo = FakeUserParkingRepository(activeSession)
        val bus = FakeDepartureEventBus(initialTimestamp = exitTimestamp + 60_000L) // 1 min AFTER exit
        val useCase = buildUseCase(repo = repo, bus = bus)

        val result = useCase(geofenceId = activeSession.geofenceId!!, exitTimestampMs = exitTimestamp, currentFix = null)

        assertIs<DepartureDecision.Rejected>(result)
    }

    // ── Signal 3: IN_VEHICLE_ENTER outside time window ────────────────────────

    @Test
    fun `should return Rejected when vehicleEnter is outside the time window`() = runTest {
        val repo = FakeUserParkingRepository(activeSession)
        // Post-session (≥ 0 = the fixture's parked-at) but older than the window before the exit.
        val lateExitTimestamp = config.vehicleEnterWindowMs + exitTimestamp
        val staleTimestamp = lateExitTimestamp - config.vehicleEnterWindowMs - 1L
        val bus = FakeDepartureEventBus(initialTimestamp = staleTimestamp)
        val useCase = buildUseCase(repo = repo, bus = bus)

        val result = useCase(geofenceId = activeSession.geofenceId!!, exitTimestampMs = lateExitTimestamp, currentFix = null)

        assertIs<DepartureDecision.Rejected>(result)
    }

    // ── [DET-SESSION-BIRTH-001] A boarding predating the session is not evidence ──

    @Test
    fun `should treat pre-session vehicleEnter as absent and stay Inconclusive`() = runTest {
        // Field replay 2026-07-08 18:52 (Redmi): the inbound drive's ENTER re-delivered right
        // after the park. It must not verify the walking exit — with no other evidence the
        // decision retries (and the fall-through, seeing no admissible boarding, dismisses).
        val parkedAt = 500_000L
        val repo = FakeUserParkingRepository(activeSession.copy(location = activeSession.location.copy(timestamp = parkedAt)))
        val bus = FakeDepartureEventBus(initialTimestamp = parkedAt - 60_000L) // inbound trip's boarding
        val useCase = buildUseCase(repo = repo, bus = bus)

        val result = useCase(geofenceId = activeSession.geofenceId!!, exitTimestampMs = exitTimestamp, currentFix = null)

        val inconclusive = assertIs<DepartureDecision.Inconclusive>(result)
        assertEquals(false, inconclusive.admissibleBoarding)
    }

    @Test
    fun `should still confirm by speed when the only vehicleEnter predates the session`() = runTest {
        // A pre-session boarding must not VETO real evidence either: credible driving speed
        // confirms the departure on its own.
        val parkedAt = 500_000L
        val repo = FakeUserParkingRepository(activeSession.copy(location = activeSession.location.copy(timestamp = parkedAt)))
        val bus = FakeDepartureEventBus(initialTimestamp = parkedAt - 60_000L)
        val useCase = buildUseCase(repo = repo, bus = bus)

        val result = useCase(geofenceId = activeSession.geofenceId!!, exitTimestampMs = exitTimestamp, currentFix = fix(speedAboveThreshold))

        assertIs<DepartureDecision.Confirmed>(result)
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

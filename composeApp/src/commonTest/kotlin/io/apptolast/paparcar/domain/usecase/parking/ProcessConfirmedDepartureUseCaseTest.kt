package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.detection.DepartureProof
import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.fakes.FakeAddressAndPlaceRepository
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeDepartureEventBus
import io.apptolast.paparcar.fakes.FakeDetectionEventLogger
import io.apptolast.paparcar.fakes.FakeGeofenceManager
import io.apptolast.paparcar.fakes.FakeReportSpotScheduler
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [DET-SOLID-001] First coverage of the departure side-effect chain — the corrective path
 * that publishes the freed spot, clears the session, and tears down the geofence.
 */
class ProcessConfirmedDepartureUseCaseTest {

    private fun activeSession(
        id: String = "session-1",
        vehicleId: String? = "v-1",
        privateZoneId: String? = null,
    ) = UserParking(
        id = id,
        userId = "user-42",
        vehicleId = vehicleId,
        location = GpsPoint(40.4, -3.7, 8f, 1_000_000L, 0f),
        geofenceId = id,
        isActive = true,
        privateZoneId = privateZoneId,
    )

    @Test
    fun should_publish_spot_and_clear_session_for_public_departure() = runTest {
        val repo = FakeUserParkingRepository(initialSession = activeSession())
        val spotScheduler = FakeReportSpotScheduler()
        val geofence = FakeGeofenceManager()
        val bus = FakeDepartureEventBus(initialTimestamp = 999L)
        val useCase = buildUseCase(repo = repo, spotScheduler = spotScheduler, geofence = geofence, bus = bus)

        val result = useCase("session-1")

        assertTrue(result.isSuccess)
        assertEquals(1, spotScheduler.scheduleCallCount, "public departure must publish the freed spot")
        assertFalse(repo.getActiveSessionByGeofence("session-1") != null, "session must be cleared")
        assertEquals(listOf("session-1"), geofence.removedIds, "geofence must be removed")
        assertEquals(null, bus.lastVehicleEnteredAt, "departure bus must reset for the next trip")
    }

    @Test
    fun should_not_publish_spot_for_private_zone_departure() = runTest {
        val repo = FakeUserParkingRepository(initialSession = activeSession(privateZoneId = "zone-1"))
        val spotScheduler = FakeReportSpotScheduler()
        val useCase = buildUseCase(repo = repo, spotScheduler = spotScheduler)

        val result = useCase("session-1")

        assertTrue(result.isSuccess)
        assertEquals(0, spotScheduler.scheduleCallCount, "private-zone spots are never published")
        assertFalse(repo.getActiveSessionByGeofence("session-1") != null, "session must still be cleared")
    }

    @Test
    fun should_succeed_without_side_effects_when_no_session_matches_geofence() = runTest {
        val repo = FakeUserParkingRepository()
        val spotScheduler = FakeReportSpotScheduler()
        val useCase = buildUseCase(repo = repo, spotScheduler = spotScheduler)

        val result = useCase("unknown-geofence")

        assertTrue(result.isSuccess)
        assertEquals(0, spotScheduler.scheduleCallCount)
    }

    @Test
    fun should_fail_when_session_clear_fails_so_worker_retries() = runTest {
        val repo = FakeUserParkingRepository(initialSession = activeSession()).apply {
            clearActiveParkingSessionResult = Result.failure(RuntimeException("db error"))
        }
        val useCase = buildUseCase(repo = repo)

        val result = useCase("session-1")

        assertTrue(result.isFailure, "a failed clear must propagate so the session is never left open")
    }

    @Test
    fun should_log_departure_processed_event() = runTest {
        val logger = FakeDetectionEventLogger()
        val repo = FakeUserParkingRepository(initialSession = activeSession())
        buildUseCase(repo = repo, logger = logger)("session-1")

        val event = logger.events.filterIsInstance<DetectionEvent.DepartureProcessed>().single()
        assertEquals("session-1", event.sessionId)
        assertTrue(event.published)
        assertTrue(event.sessionCleared)
    }

    @Test
    fun should_log_departure_processed_without_publish_for_private_zone() = runTest {
        val logger = FakeDetectionEventLogger()
        val repo = FakeUserParkingRepository(initialSession = activeSession(privateZoneId = "zone-1"))
        buildUseCase(repo = repo, logger = logger)("session-1")

        val event = logger.events.filterIsInstance<DetectionEvent.DepartureProcessed>().single()
        assertFalse(event.published)
        assertTrue(event.sessionCleared)
    }

    // ── [DET-HANDOFF-NOT-MANUAL-001 §B] A DEDUCED departure publishes, but takes nothing ──

    @Test
    fun should_publish_provisionally_and_keep_the_car_when_the_departure_was_only_deduced() = runTest {
        // Field 2026-08-19 22:32: the safety net inferred a departure from the phone being far from
        // the parked car. The user was on a bicycle. The old code published a 2-hour ghost spot AND
        // released the session AND removed the geofence — three irreversible acts on a guess.
        val repo = FakeUserParkingRepository(initialSession = activeSession())
        val spotScheduler = FakeReportSpotScheduler()
        val geofence = FakeGeofenceManager()
        val useCase = buildUseCase(repo = repo, spotScheduler = spotScheduler, geofence = geofence)

        val result = useCase("session-1", proof = DepartureProof.Deduced)

        assertTrue(result.isSuccess)
        // The spot still goes out AT ONCE — freshness is its entire value — but with a short life.
        assertEquals(1, spotScheduler.scheduleCallCount)
        assertEquals(true, spotScheduler.lastProvisional)
        // …and nothing of the user's was given up.
        assertTrue(repo.getActiveSessionByGeofence("session-1") != null, "the car stays parked")
        assertEquals(emptyList(), geofence.removedIds, "its geofence stays armed for the real exit")
        assertTrue(repo.provisionalDepartures["session-1"] != null, "the deduction is marked pending")
    }

    @Test
    fun should_publish_a_deduced_departure_only_once() = runTest {
        // The session now survives a deduction, so the 15-min safety net will deduce the same
        // departure again while the user stays away. Re-publishing each time would blink a ghost
        // spot on and off all afternoon; the first publication stands and its short TTL bounds it.
        val repo = FakeUserParkingRepository(initialSession = activeSession())
        val spotScheduler = FakeReportSpotScheduler()
        val useCase = buildUseCase(repo = repo, spotScheduler = spotScheduler)

        useCase("session-1", proof = DepartureProof.Deduced)
        useCase("session-1", proof = DepartureProof.Deduced)

        assertEquals(1, spotScheduler.scheduleCallCount, "one deduction, one publication")
        assertTrue(repo.getActiveSessionByGeofence("session-1") != null)
    }

    @Test
    fun should_still_commit_everything_when_the_departure_was_witnessed() = runTest {
        // The measured case is untouched: a fresh fix at driving speed releases the car and the
        // spot goes out with the normal lifetime.
        val repo = FakeUserParkingRepository(initialSession = activeSession())
        val spotScheduler = FakeReportSpotScheduler()
        val geofence = FakeGeofenceManager()
        val useCase = buildUseCase(repo = repo, spotScheduler = spotScheduler, geofence = geofence)

        useCase("session-1", proof = DepartureProof.Witnessed)

        assertEquals(false, spotScheduler.lastProvisional)
        assertFalse(repo.getActiveSessionByGeofence("session-1") != null)
        assertEquals(listOf("session-1"), geofence.removedIds)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildUseCase(
        repo: FakeUserParkingRepository = FakeUserParkingRepository(),
        spotScheduler: FakeReportSpotScheduler = FakeReportSpotScheduler(),
        geofence: FakeGeofenceManager = FakeGeofenceManager(),
        bus: FakeDepartureEventBus = FakeDepartureEventBus(),
        logger: FakeDetectionEventLogger = FakeDetectionEventLogger(),
    ) = ProcessConfirmedDepartureUseCase(
        userParkingRepository = repo,
        reportSpotReleased = ReportSpotReleasedUseCase(
            reportSpotScheduler = spotScheduler,
            getAddressAndPlace = GetAddressAndPlaceUseCase(repository = FakeAddressAndPlaceRepository()),
            authRepository = FakeAuthRepository(initialSession = FakeAuthRepository.authenticatedSession(userId = "user-42")),
        ),
        geofenceService = geofence,
        departureEventBus = bus,
        detectionEventLogger = logger,
    )
}

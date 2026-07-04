package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.fakes.FakeActivityRecognitionManager
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
    fun should_unregister_enter_arming_only_when_no_sessions_remain() = runTest {
        val ar = FakeActivityRecognitionManager()
        val repo = FakeUserParkingRepository(initialSession = activeSession())
        buildUseCase(repo = repo, ar = ar)("session-1")

        assertEquals(1, ar.enterArmingUnregisterCount, "last active session gone → tear down arming")

        val ar2 = FakeActivityRecognitionManager()
        val repo2 = FakeUserParkingRepository(
            initialSessions = listOf(activeSession(), activeSession(id = "session-2", vehicleId = "v-2")),
        )
        buildUseCase(repo = repo2, ar = ar2)("session-1")

        assertEquals(0, ar2.enterArmingUnregisterCount, "another car still parked → arming stays")
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildUseCase(
        repo: FakeUserParkingRepository = FakeUserParkingRepository(),
        spotScheduler: FakeReportSpotScheduler = FakeReportSpotScheduler(),
        geofence: FakeGeofenceManager = FakeGeofenceManager(),
        bus: FakeDepartureEventBus = FakeDepartureEventBus(),
        ar: FakeActivityRecognitionManager = FakeActivityRecognitionManager(),
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
        activityRecognitionManager = ar,
        detectionEventLogger = logger,
    )
}

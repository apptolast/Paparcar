package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.detection.DepartureConfirmationListener
import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import io.apptolast.paparcar.domain.usecase.location.GetOneLocationUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.fakes.FakeAddressAndPlaceRepository
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeDepartureEventBus
import io.apptolast.paparcar.fakes.FakeDetectionEventLogger
import io.apptolast.paparcar.fakes.FakeGeofenceManager
import io.apptolast.paparcar.fakes.FakeLocationDataSource
import io.apptolast.paparcar.fakes.FakeReportSpotScheduler
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [DET-SOLID-001] The departure-check seam — decide → retry policy → walking-guard
 * fallthrough → live-session upgrade → side-effect processing — extracted from
 * `DepartureDetectionWorker` precisely so this file can exist in commonTest.
 */
class RunDepartureCheckUseCaseTest {

    private val exitTimestamp = 1_000_000L

    private fun activeSession(id: String = "geo-1") = UserParking(
        id = id,
        userId = "user-42",
        vehicleId = "v-1",
        location = GpsPoint(40.4, -3.7, 8f, exitTimestamp - 60_000L, 0f),
        geofenceId = id,
        isActive = true,
    )

    private class RecordingListener : DepartureConfirmationListener {
        var notifyCount = 0
        override fun notifyDepartureConfirmed() { notifyCount++ }
    }

    @Test
    fun should_dismiss_when_no_active_session_matches() = runTest {
        val env = Env(repo = FakeUserParkingRepository())

        val outcome = env.useCase("geo-1", exitTimestamp, attempt = 0)

        assertIs<DepartureCheckOutcome.Dismissed>(outcome)
        assertEquals(0, env.listener.notifyCount)
    }

    @Test
    fun should_retry_while_inconclusive_and_attempts_remain() = runTest {
        // Session active, no AR ENTER, no GPS speed sample → Inconclusive.
        val env = Env()

        val outcome = env.useCase("geo-1", exitTimestamp, attempt = 0)

        assertIs<DepartureCheckOutcome.Retry>(outcome)
        assertEquals(0, env.listener.notifyCount)
        assertNotNull(env.repo.getActiveSession(), "nothing released while inconclusive")
    }

    @Test
    fun should_dismiss_after_exhausted_attempts_without_vehicle_signal() = runTest {
        // [BUG-WALK-DEPART-001] Walking near the car: inconclusive forever, no ENTER. The
        // fallthrough must dismiss — never release the spot, never upgrade the live session.
        val env = Env()

        val outcome = env.useCase(
            "geo-1",
            exitTimestamp,
            attempt = RunDepartureCheckUseCase.MAX_INCONCLUSIVE_ATTEMPTS,
        )

        assertIs<DepartureCheckOutcome.Dismissed>(outcome)
        assertEquals(0, env.listener.notifyCount, "walking must NOT upgrade the live session")
        assertNotNull(env.repo.getActiveSession(), "the parked session must remain intact")
        assertEquals(0, env.spotScheduler.scheduleCallCount, "no phantom spot published")
    }

    @Test
    fun should_process_and_upgrade_session_when_vehicle_enter_confirms() = runTest {
        // AR ENTER within the window + no speed sample → Confirmed.
        val env = Env(bus = FakeDepartureEventBus(initialTimestamp = exitTimestamp - 60_000L))

        val outcome = env.useCase("geo-1", exitTimestamp, attempt = 0)

        assertIs<DepartureCheckOutcome.Processed>(outcome)
        assertEquals(1, env.listener.notifyCount, "confirmed departure must upgrade the live session")
        assertNull(env.repo.getActiveSession(), "session cleared")
        assertEquals(1, env.spotScheduler.scheduleCallCount, "freed spot published")
    }

    @Test
    fun should_return_process_failed_retry_when_side_effects_fail() = runTest {
        val repo = FakeUserParkingRepository(initialSession = activeSession()).apply {
            clearActiveParkingSessionResult = Result.failure(RuntimeException("db error"))
        }
        val env = Env(repo = repo, bus = FakeDepartureEventBus(initialTimestamp = exitTimestamp - 60_000L))

        val outcome = env.useCase("geo-1", exitTimestamp, attempt = 0)

        assertIs<DepartureCheckOutcome.ProcessFailedRetry>(outcome)
    }

    @Test
    fun should_log_a_worker_verdict_per_attempt() = runTest {
        val env = Env()

        env.useCase("geo-1", exitTimestamp, attempt = 1)

        val verdict = env.logger.events.filterIsInstance<DetectionEvent.DepartureVerdict>().single()
        assertEquals("geo-1", verdict.sessionId)
        assertEquals("worker", verdict.source)
        assertEquals(1, verdict.attempt)
        assertEquals("Inconclusive", verdict.verdict)
    }

    // ── Fixture ───────────────────────────────────────────────────────────────

    private inner class Env(
        val repo: FakeUserParkingRepository = FakeUserParkingRepository(initialSession = activeSession()),
        val bus: FakeDepartureEventBus = FakeDepartureEventBus(),
        val spotScheduler: FakeReportSpotScheduler = FakeReportSpotScheduler(),
        val listener: RecordingListener = RecordingListener(),
        val logger: FakeDetectionEventLogger = FakeDetectionEventLogger(),
    ) {
        val useCase = RunDepartureCheckUseCase(
            detectParkingDeparture = DetectParkingDepartureUseCase(
                userParkingRepository = repo,
                departureEventBus = bus,
                config = ParkingDetectionConfig(),
            ),
            processConfirmedDeparture = ProcessConfirmedDepartureUseCase(
                userParkingRepository = repo,
                reportSpotReleased = ReportSpotReleasedUseCase(
                    reportSpotScheduler = spotScheduler,
                    getAddressAndPlace = GetAddressAndPlaceUseCase(repository = FakeAddressAndPlaceRepository()),
                    authRepository = FakeAuthRepository(initialSession = FakeAuthRepository.authenticatedSession(userId = "user-42")),
                ),
                geofenceService = FakeGeofenceManager(),
                departureEventBus = bus,
            ),
            getOneLocation = GetOneLocationUseCase(FakeLocationDataSource()),
            departureEventBus = bus,
            departureConfirmationListener = listener,
            detectionEventLogger = logger,
        )
    }
}

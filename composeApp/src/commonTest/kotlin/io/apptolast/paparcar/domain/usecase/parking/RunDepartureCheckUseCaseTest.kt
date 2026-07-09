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
import kotlinx.coroutines.launch
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

    // Large enough that "hours before it" stays positive (the staleness test subtracts 5 h).
    private val exitTimestamp = 1_000_000_000_000L

    /** [parkedAtMs] is the session's birth — evidence admissibility is anchored to it
     *  ([DET-SESSION-BIRTH-001]), so tests set it explicitly relative to their evidence. */
    private fun activeSession(id: String = "geo-1", parkedAtMs: Long = exitTimestamp - 60_000L) = UserParking(
        id = id,
        userId = "user-42",
        vehicleId = "v-1",
        location = GpsPoint(40.4, -3.7, 8f, parkedAtMs, 0f),
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
    fun should_retry_not_confirm_on_vehicle_enter_without_a_fix() = runTest {
        // [DET-RIDE-PROOF-001] AR ENTER within the window + no fix at all. The old branch
        // CONFIRMED here — the exact shape of a phantom ENTER on a fixless wake-up. An event
        // only nominates; without measured movement the attempt retries and releases nothing.
        val env = Env(bus = FakeDepartureEventBus(initialTimestamp = exitTimestamp - 60_000L))

        val outcome = env.useCase("geo-1", exitTimestamp, attempt = 0)

        assertIs<DepartureCheckOutcome.Retry>(outcome)
        assertEquals(0, env.listener.notifyCount)
        assertNotNull(env.repo.getActiveSession(), "nothing released without movement proof")
        assertEquals(0, env.spotScheduler.scheduleCallCount)
    }

    @Test
    fun should_return_process_failed_retry_when_side_effects_fail() = runTest {
        val repo = FakeUserParkingRepository(initialSession = activeSession()).apply {
            clearActiveParkingSessionResult = Result.failure(RuntimeException("db error"))
        }
        val env = Env(repo = repo, bus = FakeDepartureEventBus(initialTimestamp = exitTimestamp - 60_000L))
        launch { env.locationSource.emitBalanced(currentFix(speedMps = 8f, accuracy = 10f)) }

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
        val locationSource: FakeLocationDataSource = FakeLocationDataSource(),
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
            getOneLocation = GetOneLocationUseCase(locationSource, nowMs = { exitTimestamp + 60_000L }),
            departureEventBus = bus,
            departureConfirmationListener = listener,
            config = ParkingDetectionConfig(),
            detectionEventLogger = logger,
            // Fixed clock 1 min after the exit → departures are FRESH by default; the staleness
            // test overrides per-call timestamps instead. [DET-RECONCILE-001]
            nowMs = { exitTimestamp + 60_000L },
        )
    }

    /** A fix whose timestamp matches the fixture's fixed clock (always fresh), AT the car. */
    private fun currentFix(speedMps: Float, accuracy: Float) =
        GpsPoint(40.4, -3.7, accuracy, exitTimestamp + 60_000L, speedMps)

    /** Fresh fix ~1.1 km north of the car — beyond pedestrian reach for any short window. */
    private fun farFix(speedMps: Float, accuracy: Float) =
        GpsPoint(40.41, -3.7, accuracy, exitTimestamp + 60_000L, speedMps)

    /** Fresh fix ~100 m from the car — comfortably walkable. */
    private fun nearFix(speedMps: Float, accuracy: Float) =
        GpsPoint(40.4009, -3.7, accuracy, exitTimestamp + 60_000L, speedMps)

    // ── [DET-EXIT-TRUST-001] Speed is only evidence with credible accuracy ─────

    @Test
    fun should_not_confirm_on_driving_speed_from_degraded_fix() = runTest {
        // Field trace 2026-07-08 04:18 (Oppo): a single acc=100 m cache jump reported 6 m/s
        // (21.6 km/h) with the phone motionless on a nightstand — and confirmed the departure of
        // a car that had not moved, publishing a ghost spot. Degraded speed is NOT evidence:
        // the attempt must stay Inconclusive.
        val env = Env()
        launch { env.locationSource.emitBalanced(currentFix(speedMps = 6f, accuracy = 100f)) }

        val outcome = env.useCase("geo-1", exitTimestamp, attempt = 0)

        assertIs<DepartureCheckOutcome.Retry>(outcome)
        assertEquals(0, env.listener.notifyCount)
        assertNotNull(env.repo.getActiveSession(), "nothing released on a garbage fix")
        assertEquals(0, env.spotScheduler.scheduleCallCount, "no ghost spot published")
    }

    @Test
    fun should_confirm_when_driving_speed_comes_with_credible_accuracy() = runTest {
        val env = Env()
        launch { env.locationSource.emitBalanced(currentFix(speedMps = 8f, accuracy = 10f)) }

        val outcome = env.useCase("geo-1", exitTimestamp, attempt = 0)

        assertIs<DepartureCheckOutcome.Processed>(outcome)
        assertEquals(1, env.spotScheduler.scheduleCallCount, "freed spot published")
    }

    // ── [DET-RECONCILE-001] preconfirmed + freshness gate ─────────────────────

    @Test
    fun should_process_without_deciding_when_preconfirmed_by_reconcile() = runTest {
        // Trip already over: no AR ENTER, no speed — the old decision path would be Inconclusive
        // and eventually dismiss. Preconfirmed must skip straight to processing.
        val env = Env()

        val outcome = env.useCase("geo-1", exitTimestamp, attempt = 0, preconfirmed = true)

        assertIs<DepartureCheckOutcome.Processed>(outcome)
        assertEquals(1, env.listener.notifyCount)
        assertNull(env.repo.getActiveSession(), "session cleared")
        assertEquals(1, env.spotScheduler.scheduleCallCount, "fresh recovered departure publishes")
        val verdict = env.logger.events.filterIsInstance<DetectionEvent.DepartureVerdict>().single()
        assertEquals("Preconfirmed", verdict.verdict)
    }

    // ── [DET-SESSION-BIRTH-001] The fall-through only trusts a POST-session boarding ─

    @Test
    fun should_dismiss_after_exhausted_attempts_when_boarding_predates_the_session() = runTest {
        // Field replay 2026-07-08 18:52-18:54 (Redmi): parking correctly confirmed, MIUI
        // re-delivers the INBOUND drive's IN_VEHICLE ENTER (trueTime 17 min old), the fresh
        // fence fires a walking EXIT 23 s later. All speed attempts are Inconclusive (the user
        // is on foot) — and the old raw bus null-check then let that pre-session boarding
        // "verify" the departure: correct parking erased + phantom spot published. The
        // fall-through must dismiss instead.
        val sessionStart = exitTimestamp - 23_000L // parked 23 s before the walking EXIT
        val env = Env(
            repo = FakeUserParkingRepository(initialSession = activeSession(parkedAtMs = sessionStart)),
            bus = FakeDepartureEventBus(initialTimestamp = sessionStart - 17 * 60_000L),
        )

        val outcome = env.useCase(
            "geo-1",
            exitTimestamp,
            attempt = RunDepartureCheckUseCase.MAX_INCONCLUSIVE_ATTEMPTS,
        )

        assertIs<DepartureCheckOutcome.Dismissed>(outcome)
        assertNotNull(env.repo.getActiveSession(), "the correct parking must survive")
        assertEquals(0, env.spotScheduler.scheduleCallCount, "no phantom spot published")
        assertEquals(0, env.listener.notifyCount)
    }

    @Test
    fun should_fall_through_after_exhausted_attempts_when_boarding_is_admissible() = runTest {
        // The slow-garage-exit case the fall-through exists for: a POST-session boarding within
        // the window, speed never crossing the threshold — but the position already ~1.1 km
        // from the car, far beyond pedestrian reach since the boarding (the corroboration the
        // fall-through now demands — [DET-RIDE-PROOF-001]). Attempts exhausted → process.
        val env = Env(bus = FakeDepartureEventBus(initialTimestamp = exitTimestamp - 30_000L))
        launch { env.locationSource.emitBalanced(farFix(speedMps = 1f, accuracy = 10f)) }

        val outcome = env.useCase(
            "geo-1",
            exitTimestamp,
            attempt = RunDepartureCheckUseCase.MAX_INCONCLUSIVE_ATTEMPTS,
        )

        assertIs<DepartureCheckOutcome.Processed>(outcome)
        assertNull(env.repo.getActiveSession(), "session cleared")
    }

    @Test
    fun should_dismiss_after_exhausted_attempts_when_boarding_is_pedestrian_reachable() = runTest {
        // Field 2026-07-09 11:53-11:55 (Redmi): phantom IN_VEHICLE ENTER while WALKING, exit at
        // ~100 m, every sample stationary. The old fall-through released the real spot here.
        // A walkable displacement keeps the boarding a nomination — dismissed, nothing touched.
        val env = Env(bus = FakeDepartureEventBus(initialTimestamp = exitTimestamp - 14_000L))
        launch { env.locationSource.emitBalanced(nearFix(speedMps = 0f, accuracy = 12f)) }

        val outcome = env.useCase(
            "geo-1",
            exitTimestamp,
            attempt = RunDepartureCheckUseCase.MAX_INCONCLUSIVE_ATTEMPTS,
        )

        assertIs<DepartureCheckOutcome.Dismissed>(outcome)
        assertNotNull(env.repo.getActiveSession(), "the real parking must survive a walking exit")
        assertEquals(0, env.spotScheduler.scheduleCallCount, "no spot released while walking")
    }

    @Test
    fun should_clear_without_publishing_when_departure_is_stale() = runTest {
        // Departure recovered hours late (offline device — Redmi 2026-07-06) via the reconcile
        // (preconfirmed — a live re-check would wrongly veto a trip that is already over): the
        // session must converge, but the freed spot is long gone and must NOT be advertised.
        val staleExitTimestamp = exitTimestamp - 5 * 60 * 60_000L // 5 h before the fixed clock
        val env = Env(
            repo = FakeUserParkingRepository(initialSession = activeSession(parkedAtMs = staleExitTimestamp - 10 * 60_000L)),
            bus = FakeDepartureEventBus(initialTimestamp = staleExitTimestamp - 60_000L),
        )

        val outcome = env.useCase("geo-1", staleExitTimestamp, attempt = 0, preconfirmed = true)

        assertIs<DepartureCheckOutcome.Processed>(outcome)
        assertNull(env.repo.getActiveSession(), "stale departure still clears the session")
        assertEquals(0, env.spotScheduler.scheduleCallCount, "no ghost spot published")
    }
}

@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.coordinator.replay

import io.apptolast.paparcar.domain.coordinator.CoordinatorParkingDetector
import io.apptolast.paparcar.domain.detection.ArmEvidence
import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import io.apptolast.paparcar.domain.usecase.parking.CalculateParkingConfidenceUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.EvaluateParkingDecisionUseCase
import io.apptolast.paparcar.fakes.FakeAppNotificationManager
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeDepartureEventBus
import io.apptolast.paparcar.fakes.FakeDetectionEventLogger
import io.apptolast.paparcar.fakes.FakeGeofenceManager
import io.apptolast.paparcar.fakes.FakeParkingEnrichmentScheduler
import io.apptolast.paparcar.fakes.FakeStepDetectorSource
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import io.apptolast.paparcar.fakes.FakeZoneRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [DET-SOLID-001][C4] Field-trace replays against the REAL detector — the mechanism that turns
 * "stop patching" into practice: every field bug becomes a permanent fixture here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetectionTraceReplayTest {

    @Test
    fun bug_repark_walk_001_unverified_walking_exit_saves_nothing_and_prompts_nothing() =
        runTest(UnconfinedTestDispatcher()) {
            // The 2026-07-03 incident: park at home, walk away, geofence EXIT arms the detector.
            // Post-redesign the walking exit arms UNVERIFIED (no speed, no ENTER) → the
            // false-ENTER guard must abort on the step burst: no save, no prompt, real session
            // untouched. Pre-fix, this exact trace re-parked the car ~120 m away at 0.90.
            val replayer = DetectionTraceReplayer(TRACE_BUG_REPARK_WALK_001)
            val env = buildEnv(clock = { replayer.nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 256)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified) }

            replayer.replay(
                emitFix = { locations.emit(it) },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "no phantom re-park")
            assertEquals(0, env.notification.parkingConfirmationCallCount, "no prompt either — clean abort")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_false_enter", ended.outcome, "the anti-walking guard must be what kills it")
        }

    @Test
    fun same_trace_with_speed_verified_arm_confirms_at_the_stop_anchor() =
        runTest(UnconfinedTestDispatcher()) {
            // Contrast — the DET-G-04 short-hop semantics at trace level: when the exit WAS
            // verified (driving-speed fix witnessed it), the identical low-speed arrival stream
            // must still confirm the park, anchored at the first stopped fix. This is the
            // legitimate behaviour the verifier's evidence buys.
            val replayer = DetectionTraceReplayer(TRACE_BUG_REPARK_WALK_001)
            val env = buildEnv(clock = { replayer.nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 256)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.VerifiedBySpeed(speedKmh = 22f, accuracyM = 12f),
                )
            }

            replayer.replay(
                emitFix = { locations.emit(it) },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "verified arm must confirm")
            val saved = env.parkingRepo.getActiveSession()
            assertTrue(
                saved != null && saved.location.latitude in 36.60460..36.60470,
                "park must anchor at the first stopped fix (bestStopLocation), was ${saved?.location?.latitude}",
            )
        }

    // ── Env ───────────────────────────────────────────────────────────────────

    private class Env(
        val coordinator: CoordinatorParkingDetector,
        val parkingRepo: FakeUserParkingRepository,
        val notification: FakeAppNotificationManager,
        val stepDetector: FakeStepDetectorSource,
        val detectionLogger: FakeDetectionEventLogger,
    )

    private fun buildEnv(clock: () -> Long): Env {
        // confirmHoldMs=0: the replay ends at the confirm moment; the 2-min errand hold is
        // covered by its own dedicated tests.
        val config = ParkingDetectionConfig(confirmHoldMs = 0L)
        val auth = FakeAuthRepository(initialSession = FakeAuthRepository.authenticatedSession(userId = "user-1"))
        val vehicleRepo = FakeVehicleRepository(
            defaultVehicle = Vehicle(id = "v-1", userId = "user-1", sizeCategory = VehicleSize.MEDIUM_SUV),
        )
        val parkingRepo = FakeUserParkingRepository()
        val notification = FakeAppNotificationManager()
        val stepDetector = FakeStepDetectorSource()
        val detectionLogger = FakeDetectionEventLogger()
        val coordinator = CoordinatorParkingDetector(
            calculateParkingConfidence = CalculateParkingConfidenceUseCase(config),
            confirmParking = ConfirmParkingUseCase(
                userParkingRepository = parkingRepo,
                vehicleRepository = vehicleRepo,
                zoneRepository = FakeZoneRepository(),
                geofenceService = FakeGeofenceManager(),
                enrichmentScheduler = FakeParkingEnrichmentScheduler(),
                authRepository = auth,
                config = config,
                departureEventBus = FakeDepartureEventBus(),
            ),
            notifyParkingConfirmation = NotifyParkingConfirmationUseCase(
                notificationPort = notification,
                vehicleRepository = vehicleRepo,
            ),
            notificationPort = notification,
            vehicleRepository = vehicleRepo,
            stepDetector = stepDetector,
            config = config,
            detectionEventLogger = detectionLogger,
            evaluateParkingDecision = EvaluateParkingDecisionUseCase(config),
            clock = clock,
        )
        return Env(coordinator, parkingRepo, notification, stepDetector, detectionLogger)
    }
}

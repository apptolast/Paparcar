@file:OptIn(kotlin.time.ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.domain.coordinator

import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import io.apptolast.paparcar.domain.usecase.parking.CalculateParkingConfidenceUseCase
import io.apptolast.paparcar.domain.usecase.parking.EvaluateParkingDecisionUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.fakes.FakeAppNotificationManager
import io.apptolast.paparcar.fakes.FakeActivityRecognitionManager
import io.apptolast.paparcar.domain.detection.ArmEvidence
import io.apptolast.paparcar.fakes.FakeDepartureEventBus
import io.apptolast.paparcar.fakes.FakeDetectionEventLogger
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeGeofenceManager
import io.apptolast.paparcar.fakes.FakeParkingEnrichmentScheduler
import io.apptolast.paparcar.fakes.FakeZoneRepository
import io.apptolast.paparcar.fakes.FakeStepDetectorSource
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [CoordinatorParkingDetector].
 *
 * Scope is the deterministic, non-time-dependent behaviour:
 *  - user-confirmed confirmation path
 *  - deny semantics (preserve hasEverMoved)
 *  - hasDetectedMovement requires BOTH speed AND distance
 *  - spurious sessions are gated by the movement guard
 *
 * The observation-window paths (vehicle-exit / slow-path auto-confirmation) are
 * time-driven via `kotlin.time.Clock.System.now()` and intentionally exercised by
 * [io.apptolast.paparcar.domain.usecase.parking.ParkingFlowIntegrationTest] instead
 * of being faked here.
 */
class CoordinatorParkingDetectorTest {

    private val authSession = FakeAuthRepository.authenticatedSession(userId = "user-1")
    // confirmHoldMs = 0 → no post-confirm hold, so egress confirms fire immediately and these
    // deterministic tests stay synchronous. The hold itself is covered by dedicated tests below
    // that drive an injected clock. [DET-C-02]
    private val config = ParkingDetectionConfig(confirmHoldMs = 0L)

    private fun setup(
        config: ParkingDetectionConfig = this.config,
        clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    ): TestEnv {
        val auth = FakeAuthRepository(initialSession = authSession)
        val vehicleRepo = FakeVehicleRepository(
            defaultVehicle = Vehicle(
                id = "v-1",
                userId = "user-1",
                sizeCategory = VehicleSize.MEDIUM_SUV,
            ),
        )
        val parkingRepo = FakeUserParkingRepository()
        val geofence = FakeGeofenceManager()
        val notification = FakeAppNotificationManager()
        val enrichment = FakeParkingEnrichmentScheduler()
        val confirmParking = ConfirmParkingUseCase(
            userParkingRepository = parkingRepo,
            vehicleRepository = vehicleRepo,
            zoneRepository = FakeZoneRepository(),
            geofenceService = geofence,
            enrichmentScheduler = enrichment,
            authRepository = auth,
            config = config,
            departureEventBus = FakeDepartureEventBus(),
        )
        val notifyParking = NotifyParkingConfirmationUseCase(
            notificationPort = notification,
            vehicleRepository = vehicleRepo,
        )
        val calcConfidence = CalculateParkingConfidenceUseCase(config)
        val stepDetector = FakeStepDetectorSource()
        val detectionLogger = FakeDetectionEventLogger()
        val coordinator = CoordinatorParkingDetector(
            calculateParkingConfidence = calcConfidence,
            confirmParking = confirmParking,
            notifyParkingConfirmation = notifyParking,
            notificationPort = notification,
            vehicleRepository = vehicleRepo,
            stepDetector = stepDetector,
            config = config,
            detectionEventLogger = detectionLogger,
            evaluateParkingDecision = EvaluateParkingDecisionUseCase(config),
            clock = clock,
        )
        return TestEnv(coordinator, parkingRepo, geofence, enrichment, notification, stepDetector, detectionLogger)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // User-confirmed path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_confirm_parking_with_user_reliability_when_user_taps_yes() =
        runTest(UnconfinedTestDispatcher()) {
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)

            val job = launch { env.coordinator.invoke(locations) }

            // Establish session origin + cross the movement threshold (speed + distance).
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            assertTrue(env.coordinator.hasDetectedMovement, "movement should be detected after driving fix")

            // User taps "Yes, I parked" before any auto-confirmation timer.
            env.coordinator.onUserConfirmedParking()
            // Drive the loop with one more fix so collectLatest re-enters and sees the flag.
            locations.emit(stationaryFix(lat = 40.002, lon = -3.7))

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "ConfirmParking should run exactly once")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved, "active session should be persisted")
            assertEquals(
                config.reliabilityUserConfirmed,
                saved.detectionReliability ?: 0f,
                /* absoluteTolerance = */ 0.0001f,
                "reliability should be the user-confirmed score",
            )
            assertEquals(1, env.geofence.createGeofenceCallCount, "geofence should be registered for the saved session")
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-LOG-03: coordinator emits a diagnostics session trace
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_emit_session_trace_when_user_confirms() =
        runTest(UnconfinedTestDispatcher()) {
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            env.coordinator.onUserConfirmedParking()
            locations.emit(stationaryFix(lat = 40.002, lon = -3.7))

            job.cancelAndJoin()

            val events = env.detectionLogger.events
            assertTrue(events.any { it is DetectionEvent.SessionStarted }, "SessionStarted must be logged")
            assertTrue(events.any { it is DetectionEvent.LocationFix }, "raw GPS fixes must be logged [DET-LOG-04]")
            assertTrue(
                events.any { it is DetectionEvent.Decision && it.outcome == "CONFIRMED" && it.pathLabel == "user" },
                "a CONFIRMED Decision with the user path must be logged",
            )
            assertTrue(events.any { it is DetectionEvent.SessionEnded }, "SessionEnded must be logged")
            assertEquals(
                1,
                events.map { it.sessionId }.distinct().size,
                "all events in one session must share a single sessionId",
            )
        }

    @Test
    fun should_log_vehicle_exit_transition_in_trace() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-LOG-04] An IN_VEHICLE→EXIT fed via onVehicleExit() must surface as an
            // ActivityTransition in the trace, edge-logged once on the next fix.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            env.coordinator.onVehicleExit()
            locations.emit(stationaryFix(lat = 40.002, lon = -3.7))

            job.cancelAndJoin()

            assertTrue(
                env.detectionLogger.events.any {
                    it is DetectionEvent.ActivityTransition && it.activity == "IN_VEHICLE" && it.transition == "EXIT"
                },
                "an IN_VEHICLE EXIT transition must be logged in the trace",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Deny resets state but preserves hasEverMoved
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_preserve_hasEverMoved_after_user_denies_parking() =
        runTest(UnconfinedTestDispatcher()) {
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            assertTrue(env.coordinator.hasDetectedMovement)

            env.coordinator.onUserDeniedParking()

            // Critical: hasEverMoved must survive the deny reset, otherwise the
            // maxNoMovementMs guard would immediately end the session on the next fix.
            assertTrue(
                env.coordinator.hasDetectedMovement,
                "hasEverMoved must survive onUserDeniedParking",
            )

            job.cancelAndJoin()
        }

    // ─────────────────────────────────────────────────────────────────────────
    // hasDetectedMovement: BOTH speed AND distance required
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_flag_movement_when_speed_meets_threshold_even_without_displacement() =
        runTest(UnconfinedTestDispatcher()) {
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Session origin.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            // Same location, speed above threshold — hasEverReachedDrivingSpeed triggers on speed alone.
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))

            assertTrue(
                env.coordinator.hasDetectedMovement,
                "hasDetectedMovement (hasEverReachedDrivingSpeed) triggers on speed, not on distance",
            )

            job.cancelAndJoin()
        }

    @Test
    fun should_not_flag_movement_when_distance_meets_threshold_but_speed_does_not() =
        runTest(UnconfinedTestDispatcher()) {
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Session origin.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            // ~200 m displacement (well past minimumTripDistanceMeters=150) but speed is below threshold.
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 1f))

            assertFalse(
                env.coordinator.hasDetectedMovement,
                "displacement alone without speed must not trip hasEverMoved",
            )

            job.cancelAndJoin()
        }

    // ─────────────────────────────────────────────────────────────────────────
    // LOC-002: low-accuracy fix must not clear bestStopLocation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_preserve_bestStopLocation_when_high_speed_fix_has_poor_accuracy() =
        runTest(UnconfinedTestDispatcher()) {
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Origin + cross the movement threshold so hasEverMoved=true.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))

            // Park: stopped fix with good accuracy at (40.005, -3.7). This becomes the
            // bestStopLocation we expect to survive the noisy fix below.
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            // Noisy GPS hallucination: apparent driving speed but bad accuracy. Pre-LOC-002
            // this single fix wiped bestStopLocation; with the gate in place it is ignored.
            locations.emit(GpsPoint(40.010, -3.7, accuracy = 100f, timestamp = 0L, speed = 5f))

            // Trigger user-confirmed path with a stopped fix at a DIFFERENT spot (40.020).
            // If bestStopLocation was wrongly cleared, the saved location would be 40.020
            // (the fallback bestFix). With LOC-002 the saved location stays at 40.005.
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(40.020, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved, "active session should be persisted")
            assertEquals(
                40.005,
                saved.location.latitude,
                /* absoluteTolerance = */ 0.00001,
                "bestStopLocation must survive a high-speed fix with accuracy > minGpsAccuracyForDriving",
            )
        }

    @Test
    fun should_clear_bestStopLocation_when_high_speed_fix_has_good_accuracy() =
        runTest(UnconfinedTestDispatcher()) {
            // Regression: ensure a trusted driving signal (good accuracy + driving speed)
            // still clears bestStopLocation, so traffic-light stops followed by genuine
            // driving away don't anchor the eventual park to the wrong intersection.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            // Brief stop (traffic light) — sets bestStopLocation.
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            // Resume driving with GOOD accuracy. This is a trusted driving signal and
            // should clear bestStopLocation per the original LOC-001 contract.
            locations.emit(GpsPoint(40.010, -3.7, accuracy = 5f, timestamp = 0L, speed = 5f))
            // User confirms after stopping at the eventual park spot (40.030).
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(40.030, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(
                40.030,
                saved.location.latitude,
                /* absoluteTolerance = */ 0.00001,
                "trusted driving fix must clear bestStopLocation so the eventual park anchors here",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // PARKING-001: reposition-burst clears bestStopLocation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_clear_bestStopLocation_after_three_consecutive_reposition_fixes() =
        runTest(UnconfinedTestDispatcher()) {
            // Scenario: wait + maneuver to plaza. The car stops at the waiting position,
            // bestStopLocation is captured there. The brief maneuver to the actual plaza
            // produces 3 consecutive fixes at 1.7 m/s with good accuracy — below
            // clearBestStopSpeedMps so LOC-002 alone would preserve the stale value, but
            // PARKING-001 counts the burst (repositionFixCount=3) and clears it. The
            // eventual confirmed location must be the plaza, not the waiting position.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Origin + cross the movement threshold so hasEverMoved=true.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))

            // Waiting stop — bestStopLocation = 40.005.
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            // Reposition burst: 3 fixes at 1.7 m/s with good accuracy. Below
            // clearBestStopSpeedMps (2.5) — LOC-002 alone would preserve. With PARKING-001
            // the third fix triggers the reset (repositionFixCount=3). [PARKING-001]
            locations.emit(GpsPoint(40.006, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.7f))
            locations.emit(GpsPoint(40.007, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.7f))
            locations.emit(GpsPoint(40.008, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.7f))

            // Trigger user-confirm at the actual plaza (40.010). With the burst-reset the
            // saved location should be 40.010, not the stale waiting 40.005.
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(40.010, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(
                40.010,
                saved.location.latitude,
                /* absoluteTolerance = */ 0.00001,
                "reposition burst must clear bestStopLocation so the plaza wins over the waiting spot",
            )
        }

    @Test
    fun should_preserve_bestStopLocation_on_single_reposition_fix() =
        runTest(UnconfinedTestDispatcher()) {
            // A single 1.7 m/s fix must NOT clear bestStopLocation — that would be the
            // LOC-002 noise-spike scenario lowered to reposition speed. Three consecutive
            // fixes are required (repositionFixCount=3). [PARKING-001]
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))

            // Park: bestStopLocation = 40.005.
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            // ONE reposition-speed fix → counter=1 < repositionFixCount(3). bestStopLocation preserved.
            locations.emit(GpsPoint(40.006, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.7f))

            // User confirms at a different stop. Saved location should still be 40.005.
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(40.020, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(
                40.005,
                saved.location.latitude,
                /* absoluteTolerance = */ 0.00001,
                "single reposition-speed fix must not clear bestStopLocation",
            )
        }

    @Test
    fun should_preserve_bestStopLocation_on_sustained_walking() =
        runTest(UnconfinedTestDispatcher()) {
            // The user parks and walks toward their destination at ~1.2 m/s. Walking pace
            // is below repositionSpeedMps (1.7), so consecutiveRepositionFixes never
            // increments. bestStopLocation must remain at the parked-car position even
            // across many walking fixes.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))

            // Park: bestStopLocation = 40.005.
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            // Walking sequence: speed sustained at 1.2 m/s — never crosses 1.7.
            repeat(6) { i ->
                locations.emit(
                    GpsPoint(40.005 + (i + 1) * 0.0001, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f)
                )
            }

            // User confirms at the walking destination. Saved location must still be the parked spot.
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(40.020, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(
                40.005,
                saved.location.latitude,
                /* absoluteTolerance = */ 0.00001,
                "sustained walking pace must not clear bestStopLocation",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification reset on invoke entry
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_dismiss_parking_confirmation_notification_on_each_invoke_entry() =
        runTest(UnconfinedTestDispatcher()) {
            val env = setup()
            // Start a session — this should call dismiss() during the reset().
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }
            // Drive the flow once so the reset() pathway has surely run.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            job.cancelAndJoin()

            assertTrue(
                env.notification.dismissedIds.contains(
                    io.apptolast.paparcar.domain.notification.AppNotificationManager
                        .PARKING_CONFIRMATION_NOTIFICATION_ID,
                ),
                "session-start must dismiss any stale PARKING_CONFIRMATION notification",
            )
        }

    @Test
    fun should_keep_post_save_card_after_session_finally() =
        runTest(UnconfinedTestDispatcher()) {
            // Regression: after auto-confirm, [runConfirm] posts the unified "Vehículo aparcado ·
            // Cancelar" card on PARKING_CONFIRMATION_NOTIFICATION_ID. The session finally must
            // NOT dismiss that id, otherwise the user loses the revert affordance ~1–2 s after
            // it appears (next location tick closes the flow). [REFACTOR-300 follow-up]
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Drive past the movement threshold so the user-confirm path can run.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))

            // User taps "Yes" — runConfirm fires → onSuccess → showParkingSavedConfirm.
            env.coordinator.onUserConfirmedParking()
            locations.emit(stationaryFix(lat = 40.002, lon = -3.7))

            job.cancelAndJoin()

            assertEquals(
                1,
                env.notification.parkingSavedConfirmCallCount,
                "showParkingSavedConfirm should fire exactly once on auto-confirm success",
            )
            // The post-save card must be the LAST op on the confirmation id — nothing
            // (finally's reset(), or any other path) should have dismissed it afterwards.
            assertEquals(
                "savedConfirm",
                env.notification.confirmationNotifOps.last(),
                "post-save card must survive session finally — no dismiss may follow it",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // BUG-FALSE-ENTER-WALKING: 8 steps before driving speed → abort the session
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_abort_session_when_steps_burst_before_driving_speed() =
        runTest(UnconfinedTestDispatcher()) {
            // Scenario: AR misfires IN_VEHICLE_ENTER while the user is walking from the
            // car carrying bags. The coordinator should not wait the full maxNoMovementMs
            // (4 min) — once 8 pedestrian steps have accumulated without driving speed
            // being reached, abort the session and let the service stop. [BUG-FALSE-ENTER-WALKING]
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Emit a slow GPS fix (walking speed) — does NOT cross minimumTripSpeedMps (5 m/s).
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f))

            // 8 pedestrian steps fire — typical post-park burst (door slam + walk to trunk).
            env.stepDetector.emitSteps(8)

            // Next location fix — the abort check runs here and flips completed=true. The
            // takeWhile won't actually close the flow until the *following* emit, so we use
            // cancelAndJoin to wrap up the test rather than waiting for that next tick.
            locations.emit(GpsPoint(40.0001, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f))

            job.cancelAndJoin()

            assertFalse(
                env.coordinator.hasDetectedMovement,
                "session must abort without ever reaching driving speed",
            )
            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "no parking save must happen — this was a false ENTER, not a real trip",
            )
        }

    @Test
    fun should_not_abort_session_when_steps_arrive_after_driving_speed() =
        runTest(UnconfinedTestDispatcher()) {
            // Regression guard: once the user is genuinely driving (hasEverReachedDrivingSpeed),
            // step events during a stop are the normal "user got out" proof and must NOT
            // trigger the false-ENTER abort. The abort gate is strictly pre-drive.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Drive: cross movement threshold.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            assertTrue(env.coordinator.hasDetectedMovement, "sanity: driving speed reached")

            // Stop and fire 8 steps — this is the canonical "user got out" signal, not a false ENTER.
            locations.emit(stationaryFix(lat = 40.002, lon = -3.7))
            env.stepDetector.emitSteps(8)
            locations.emit(stationaryFix(lat = 40.002, lon = -3.7))

            // Session must still be alive (no abort). The user-confirm path can still confirm.
            assertTrue(
                env.coordinator.hasDetectedMovement,
                "session must remain alive after driving + steps — abort is pre-drive only",
            )

            job.cancelAndJoin()
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-G-04: a GEOFENCE_EXIT-armed session is seeded already-driving
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_confirm_geofence_armed_session_even_when_it_never_reaches_driving_speed() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-G-04] Real trace (2026-07-01, El Puerto de Santa María): a GEOFENCE_EXIT armed a
            // coordinator session for a SHORT hop between two parks. Its GPS stream warmed up after
            // the fast driving was already over — every fix reported ≤ 2.9 m/s (< minimumTripSpeedMps
            // = 5), so hasEverReachedDrivingSpeed stayed false and the egress steps tripped
            // falseEnterAbortSteps → aborted_false_enter, and the REAL park was lost. Because the
            // geofence exit is a CONFIRMED departure (the car left its own parked-car geofence — the
            // same signal that publishes the freed spot), the session is armed already-driving and
            // MUST confirm the park instead of aborting.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.VerifiedBySpeed(speedKmh = 20f, accuracyM = 10f)) }

            // The seed is applied before the first fix — the session reports movement immediately.
            assertTrue(
                env.coordinator.hasDetectedMovement,
                "a geofence-armed session is seeded already-driving [DET-G-04]",
            )

            // Arrival + park: NO fix ever crosses minimumTripSpeedMps (5 m/s). bestStopLocation is
            // captured at (40.005) — the real parked-car position.
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 2.8f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            // User gets out: 8 pedestrian steps + egress displacement (~33 m from the anchor). Pre-fix
            // this same burst would have tripped the false-ENTER abort; now it confirms.
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "geofence-armed session must confirm the real park despite never re-reaching driving speed [DET-G-04]",
            )
            assertEquals(
                40.005,
                env.parkingRepo.getActiveSession()?.location?.latitude ?: 0.0,
                /* absoluteTolerance = */ 0.00001,
                "confirmed location must be the parked-car position (bestStopLocation)",
            )
        }

    @Test
    fun should_still_abort_false_enter_when_session_is_not_a_confirmed_departure() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-G-04] Regression guard: the seed must NOT leak to MANUAL/unverified sessions.
            // Without verified arm evidence the false-ENTER guard still protects against a
            // spurious AR IN_VEHICLE_ENTER while walking (bus/taxi/desk) — same input as the test
            // above, but this session must ABORT and save nothing.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) } // default: ArmEvidence.Manual (no seed)

            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 2.8f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertFalse(
                env.coordinator.hasDetectedMovement,
                "a non-departure session that never reaches driving speed must not be treated as driving",
            )
            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "false-ENTER guard must still abort spurious walking sessions [DET-G-04]",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-SOLID-001 C3: the time-driven paths, exercised with the injected clock
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_abort_after_maxNoMovement_without_driving() =
        runTest(UnconfinedTestDispatcher()) {
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs = config.maxNoMovementMs + 1_000L
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_no_movement", ended.outcome)
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount)
        }

    @Test
    fun should_save_unattended_when_prompt_gets_no_response_within_timeout() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-RECONCILE-001] The prompt only shows after a real trip + stop; an unanswered
            // notification must not cost the user their parking (field incident 2026-07-06,
            // Redmi: a real parking was discarded after 15 silent minutes). The timeout SAVES
            // with low reliability instead of aborting; the session still closes. [BUG-STUCK-SESSION]
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Drive, then stop long enough for the slow path to reach Low → Notified (90 s gate
            // + 90 s lowNotifTimeout), then let the 15-min response window expire untouched.
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 6f))
            nowMs = 1_000L
            locations.emit(GpsPoint(40.001, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs = 1_000L + config.slowPathGateMs + 5_000L
            locations.emit(GpsPoint(40.001, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs += config.lowNotifTimeoutMs + 5_000L
            locations.emit(GpsPoint(40.001, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            assertEquals(1, env.notification.parkingConfirmationCallCount, "prompt must be shown")
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "nothing saved while the prompt waits")

            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(40.001, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("confirmed_unattended_timeout", ended.outcome, "[DET-RECONCILE-001]")
            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "unanswered prompt saves, never discards")
        }

    @Test
    fun should_nudge_instead_of_saving_when_unattended_timeout_has_no_measured_driving() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-AR-FIRST-001 F3] Seeded evidence (verified_enter arm) authorises RELEASING the
            // old spot, never PLACING a new pin: a session armed after the trip ended follows the
            // pedestrian, and its unattended save planted the pin in the user's living room
            // (field 2026-07-10 19:34, Redmi). Without measured in-session driving the timeout
            // must ask WHERE the car is, not guess.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch {
                env.coordinator.invoke(locations, armEvidence = ArmEvidence.VerifiedByVehicleEnter(30_000L))
            }

            // Seeded session: hasEverReachedDrivingSpeed=true but every fix is pedestrian —
            // maxSpeedMps never crosses minimumTripSpeedMps. Slow path reaches High → prompt.
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs = config.slowPath5MinMs + 1_000L
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 10f, timestamp = 0L, speed = 0.1f))
            assertEquals(1, env.notification.parkingConfirmationCallCount, "prompt must be shown")

            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(40.0, -3.7, accuracy = 10f, timestamp = 0L, speed = 0.1f))

            job.cancelAndJoin()

            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "no pin without measured driving")
            assertEquals(1, env.notification.markParkingNudgeCallCount, "the user must be asked where the car is")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_unattended_no_drive", ended.outcome, "[DET-AR-FIRST-001]")
        }

    @Test
    fun should_keep_kerb_anchor_when_user_walks_away_immediately_after_parking() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-AR-FIRST-001 F3] The Camelias regression (field 2026-07-10 15:54): park, exit
            // the car after only 3 steps, walk off at 2.6 m/s — the old rule cleared the
            // unlocked anchor on that first ambiguous fix and the pin re-anchored wherever the
            // pedestrian ended up (inside the house). Steps discriminate person vs car: the
            // displacement never outruns the counted steps, so the kerb anchor must survive and
            // the steps+egress confirm must save AT THE KERB.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 6f)) // drive
            val kerbLat = 40.001
            locations.emit(GpsPoint(kerbLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // park → anchor
            env.stepDetector.emitSteps(3) // door slam + first steps, stop still alive
            // Brisk walk-away: ambiguous band (≥ clearBestStopSpeedMps, < real driving), good
            // accuracy — the fix that used to WIPE the anchor. 3 steps cover ~5 m: HOLD.
            locations.emit(GpsPoint(40.00105, -3.7, accuracy = 15f, timestamp = 0L, speed = 2.6f))
            // The walk continues; steps keep counting even though GPS reads movement (the
            // counting gate feeds the discriminator during the walk).
            env.stepDetector.emitSteps(6) // total 9 ≥ minStepsToConfirm
            // ~28 m from the kerb at walking pace → steps+egress confirm fires.
            locations.emit(GpsPoint(40.00125, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f))

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "steps+egress must confirm")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(kerbLat, saved.location.latitude, 0.00005, "pin must stay at the kerb anchor, not follow the walker")
        }

    @Test
    fun should_flush_phantom_jam_steps_when_displacement_outruns_them() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-AR-FIRST-001 F3] The jam guard the discriminator must NOT break: phone jiggle
            // at a jam stop counts 2 phantom steps; the car then creeps on at 3 m/s (below real
            // driving). Displacement outruns what 2 steps could walk → CAR: anchor cleared AND
            // steps flushed, so the next genuine stop re-anchors clean and confirms THERE.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 6f)) // drive
            locations.emit(GpsPoint(40.001, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // jam stop → anchor
            env.stepDetector.emitSteps(2) // phone jiggle
            // Jam creeps on: ambiguous band, displacement 30 m then 60 m — outruns 2 steps.
            locations.emit(GpsPoint(40.00127, -3.7, accuracy = 10f, timestamp = 0L, speed = 3f))
            locations.emit(GpsPoint(40.00164, -3.7, accuracy = 10f, timestamp = 0L, speed = 3f))
            // Real park 100 m later.
            val plazaLat = 40.0025
            locations.emit(GpsPoint(plazaLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(8) // real exit
            locations.emit(GpsPoint(40.00275, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f)) // egress ~28 m

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount)
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(plazaLat, saved.location.latitude, 0.00005, "pin must anchor at the real plaza, not the jam stop")
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-ANCHOR-FREEZE-001: the end-of-drive anchor on mute-step-counter devices
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_pin_the_frozen_anchor_when_a_stepless_walk_drags_to_a_later_stop() =
        runTest(UnconfinedTestDispatcher()) {
            // Field 2026-07-11 20:46 (Redmi): drive → 78-s stop AT the car → walk home with a
            // MUTE step counter (zero steps the whole way) → stand at the front door → prompt
            // ignored → unattended save. The unlocked anchor followed the walker and the pin
            // landed at the door, 95 m from the car. The matured end-of-drive stop must FREEZE
            // the anchor: the walk (including reposition-signature bursts) cannot move it, the
            // later stop cannot re-capture it, and the timeout save pins the CAR.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 6f)) // drive
            val carLat = 40.001
            nowMs = 1_000L
            locations.emit(GpsPoint(carLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // park
            nowMs = 1_000L + config.anchorFreezeStopMs + 1_000L
            locations.emit(GpsPoint(carLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // stop matures → FROZEN
            // Stepless walk home, including three consecutive reposition-signature fixes
            // (≥ repositionSpeedMps, tight accuracy) that used to clear an unlocked anchor.
            locations.emit(GpsPoint(40.0013, -3.7, accuracy = 10f, timestamp = 0L, speed = 2.0f))
            locations.emit(GpsPoint(40.0016, -3.7, accuracy = 10f, timestamp = 0L, speed = 2.0f))
            locations.emit(GpsPoint(40.0019, -3.7, accuracy = 10f, timestamp = 0L, speed = 2.0f))
            val doorLat = 40.0021
            nowMs += 60_000L
            locations.emit(GpsPoint(doorLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // front door
            nowMs += config.slowPathGateMs + 5_000L
            locations.emit(GpsPoint(doorLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs += config.lowNotifTimeoutMs + 5_000L
            locations.emit(GpsPoint(doorLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            assertEquals(1, env.notification.parkingConfirmationCallCount, "prompt must be shown")
            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(doorLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "a pinned anchor authorises the unattended save")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(carLat, saved.location.latitude, 0.00005, "pin must stay at the frozen end-of-drive anchor, not the front door")
        }

    @Test
    fun should_confirm_kinematically_when_stepless_walk_leaves_the_frozen_anchor() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-KINEMATIC-EGRESS-001] Field 2026-07-11 (Redmi), the outcome it should have
            // had: drive → stop matures at the car (anchor FROZEN) → the user walks home with a
            // MUTE step counter. The frozen anchor watches a sustained quality walk away — that
            // GPS-measured egress must confirm the park AT THE ANCHOR within seconds, at the
            // kinematic reliability tier, instead of waiting 15 minutes for the 0.5 timeout save.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 6f)) // drive
            val carLat = 40.001
            nowMs = 1_000L
            locations.emit(GpsPoint(carLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // park
            nowMs = 1_000L + config.anchorFreezeStopMs + 1_000L
            locations.emit(GpsPoint(carLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // FROZEN
            // Stepless walk home: quality pedestrian-band fixes, ~11 m apart.
            var lat = carLat
            repeat(config.kinematicEgressMinWalkFixes) {
                lat += 0.0001
                nowMs += 5_000L
                locations.emit(GpsPoint(lat, -3.7, accuracy = 10f, timestamp = 0L, speed = 1.3f))
            }

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "kinematic egress must confirm")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(carLat, saved.location.latitude, 0.00005, "pin at the frozen anchor, not along the walk")
            assertEquals(
                config.reliabilityKinematicEgress,
                saved.detectionReliability ?: 0f,
                /* absoluteTolerance = */ 0.0001f,
                "kinematic tier, distinguishable in forensics [DET-KINEMATIC-EGRESS-001]",
            )
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("confirmed_kinematic+egress", ended.outcome)
        }

    @Test
    fun should_refine_the_anchor_past_the_initial_window_while_no_step_is_counted() =
        runTest(UnconfinedTestDispatcher()) {
            // Field 2026-07-11 (Redmi, Avenida Sanlúcar): the stop began during the final
            // approach drift — the 30-s window froze the anchor on a 20-m fix 260 m short of the
            // spot, while the real-spot 9-m fix arrived at second 71 of the SAME stop. Until a
            // step is counted every fix of the stop is still the parked car: refinement must
            // stay open for the whole stop.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 6f)) // drive
            val approachLat = 40.001
            val carLat = 40.0028
            nowMs = 1_000L
            locations.emit(GpsPoint(approachLat, -3.7, accuracy = 20f, timestamp = 0L, speed = 0f)) // drift fix
            nowMs = 1_000L + config.initialStopWindowMs + 15_000L // past the 30-s window, same stop
            locations.emit(GpsPoint(carLat, -3.7, accuracy = 8f, timestamp = 0L, speed = 0f)) // real spot, better fix
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(carLat, -3.7, accuracy = 8f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount)
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(carLat, saved.location.latitude, 0.00005, "the better same-stop fix must refine the anchor past the initial window")
        }

    @Test
    fun should_freeze_anchor_by_stable_fixes_on_a_short_trip_before_the_60s_timer() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-SHORT-TRIP-FREEZE-001] Field 2026-07-12 (Oppo, Durango→Glorieta ~2 min): the
            // destination stop never lasted 60 s before the user walked off, so the anchor never
            // froze and the park was lost. With freeze-by-evidence, N dense stopped fixes (~15 s)
            // mature the anchor WELL BEFORE anchorFreezeStopMs (60 s); the stepless walk then
            // confirms kinematically AT the anchor. All timestamps stay under 60 s to prove it is
            // the EVIDENCE path, not the timer.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 6f)) // drive
            val carLat = 40.001
            // Stopped fixes at HIGH_ACCURACY cadence: one to open the stop + anchorFreezeStableFixes
            // more. The freeze fires on the fix whose PRIOR stopped-fix count reaches the threshold.
            repeat(config.anchorFreezeStableFixes + 1) {
                nowMs += 5_000L // 5s, 10s, 15s, 20s … all << anchorFreezeStopMs (60s)
                locations.emit(GpsPoint(carLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            }
            // Stepless walk home: quality pedestrian-band fixes away from the (now frozen) anchor.
            var lat = carLat
            repeat(config.kinematicEgressMinWalkFixes) {
                lat += 0.0001
                nowMs += 5_000L
                locations.emit(GpsPoint(lat, -3.7, accuracy = 10f, timestamp = 0L, speed = 1.3f))
            }

            job.cancelAndJoin()

            assertTrue(nowMs < config.anchorFreezeStopMs, "sanity: whole trace stays under the 60 s timer")
            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "evidence-frozen anchor + kinematic egress must confirm the short trip")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(carLat, saved.location.latitude, 0.00005, "pin at the evidence-frozen anchor")
        }

    @Test
    fun should_nudge_instead_of_saving_when_unattended_timeout_finds_an_unpinned_anchor() =
        runTest(UnconfinedTestDispatcher()) {
            // Measured driving happened, but no stop matured and no egress steps sealed anything:
            // by timeout the anchor is wherever the body last stood — a guess. With 15 minutes of
            // doubt the honest move is asking WHERE the car is, not planting the guess.
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 6f)) // drive
            nowMs = 1_000L
            locations.emit(GpsPoint(40.001, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // brief stop
            nowMs = 11_000L // 10 s — far below anchorFreezeStopMs
            locations.emit(GpsPoint(40.0013, -3.7, accuracy = 10f, timestamp = 0L, speed = 1.2f)) // stepless walk
            val homeLat = 40.0018
            nowMs += 60_000L
            locations.emit(GpsPoint(homeLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // home
            nowMs += config.slowPathGateMs + 5_000L
            locations.emit(GpsPoint(homeLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            nowMs += config.lowNotifTimeoutMs + 5_000L
            locations.emit(GpsPoint(homeLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            assertEquals(1, env.notification.parkingConfirmationCallCount, "prompt must be shown")
            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(homeLat, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "an unpinned anchor must never be planted as a pin")
            assertEquals(1, env.notification.markParkingNudgeCallCount, "the user must be asked where the car is")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_unattended_unpinned_anchor", ended.outcome, "[DET-ANCHOR-FREEZE-001]")
        }

    @Test
    fun should_never_auto_confirm_a_candidate_without_steps() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-SOLID-001 C3 finding] The "vehicleExit+window+egress" decision branch is
            // STRUCTURALLY UNREACHABLE through the real loop: with activityExit=true the scorer
            // takes the fast path (ceiling Medium), so a Candidate can only ever open with
            // hadVehicleExit=false — whose window (5 min) then requires steps to confirm. This
            // test pins the REAL end-to-end behaviour: a stepless Candidate is prompted, dies
            // Rejected at the window, and the ignored prompt resolves via the unattended save
            // (low reliability) — egress without steps NEVER silently AUTO-saves; the human
            // window always runs first. [DET-RECONCILE-001]
            var nowMs = 0L
            val env = setup(clock = { nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 6f)) // drive
            nowMs = 10_000L
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // park
            nowMs = 10_000L + config.slowPath5MinMs + 1_000L
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 10f, timestamp = 0L, speed = 0.1f)) // High → Candidate
            assertEquals(1, env.notification.parkingConfirmationCallCount, "candidate must prompt")
            // Egress ~33 m at walking pace, but NO steps ever counted (phone left in the car).
            nowMs += 10_000L
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f))
            // The (no-exit) 5-min observation window elapses → candidate Rejected, falls to Notified.
            nowMs += config.confirmationObservationWindowMs + 1_000L
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f))
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "stepless egress must never AUTO-save")
            // Nobody answers the prompt → [DET-RECONCILE-001] the timeout saves unattended with
            // low reliability (a real trip + 5-min stop happened; discarding it loses the car).
            nowMs += config.confirmationResponseTimeoutMs + 1_000L
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f))

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "ignored prompt saves unattended")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("confirmed_unattended_timeout", ended.outcome)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-SOLID-001 B3/B4: weak-evidence prompt + enter-arm step veto
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_prompt_instead_of_saving_when_enter_only_arm_never_sees_driving() =
        runTest(UnconfinedTestDispatcher()) {
            // [B3] ENTER-only evidence (bus/taxi-falsifiable) + no driving observed by the stream
            // → all confirm conditions hold but the coordinator must ASK, not save.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch {
                env.coordinator.invoke(locations, armEvidence = ArmEvidence.VerifiedByVehicleEnter(enterToExitMs = 30_000L))
            }

            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 2.8f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "ENTER-only evidence must never save silently [DET-SOLID-001 B3]",
            )
            assertEquals(
                1,
                env.notification.parkingConfirmationCallCount,
                "the user must be asked instead",
            )

            // A user "Sí" then saves at reliability 1.0 (guards bypassed by user confirmation).
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            job.cancelAndJoin()
            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "user tap completes the save")
        }

    @Test
    fun should_veto_enter_arm_when_first_step_arrives_immediately() =
        runTest(UnconfinedTestDispatcher()) {
            // [B4] Veto ON: a VerifiedByVehicleEnter arm whose FIRST step lands right after the
            // arm (no driving seen) is a spurious walking ENTER — evidence degrades and the
            // false-ENTER abort re-arms, so the walking burst aborts with no save AND no prompt.
            val env = setup(config = config.copy(enterArmStepVetoMs = 15_000L))
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch {
                env.coordinator.invoke(locations, armEvidence = ArmEvidence.VerifiedByVehicleEnter(enterToExitMs = 30_000L))
            }

            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f))
            env.stepDetector.emitSteps(1)
            assertFalse(
                env.coordinator.hasDetectedMovement,
                "immediate first step must degrade the ENTER evidence and un-seed [DET-SOLID-001 B4]",
            )
            env.stepDetector.emitSteps(7)
            locations.emit(GpsPoint(40.0051, -3.7, accuracy = 5f, timestamp = 0L, speed = 1.2f))

            job.cancelAndJoin()

            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "vetoed session saves nothing")
            assertEquals(0, env.notification.parkingConfirmationCallCount, "vetoed session prompts nothing")
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-SOLID-001: a driving-speed crossing needs a credible-accuracy fix
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_not_trust_driving_speed_from_a_degraded_accuracy_fix() =
        runTest(UnconfinedTestDispatcher()) {
            // A single walking GPS spike (speed 6 m/s but accuracy 120 m) used to flip
            // hasEverReachedDrivingSpeed and unlock every confirm path — the GPS-noise variant
            // of the walking false positive. The 50 m credibility gate must reject it, so the
            // subsequent step burst still aborts the session. [DET-SOLID-001]
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.005, -3.7, accuracy = 120f, timestamp = 0L, speed = 6f))
            assertFalse(
                env.coordinator.hasDetectedMovement,
                "degraded fix must not count as driving [DET-SOLID-001]",
            )

            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "the walking burst must still abort — the spike opened no confirm path",
            )
        }

    @Test
    fun should_trust_driving_speed_from_a_credible_accuracy_fix() =
        runTest(UnconfinedTestDispatcher()) {
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.005, -3.7, accuracy = 20f, timestamp = 0L, speed = 6f))
            assertTrue(env.coordinator.hasDetectedMovement, "credible fix keeps the normal path")

            job.cancelAndJoin()
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-G-05: unverified exits stay guarded; a late departure verdict upgrades
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_prompt_when_late_departure_verdict_upgrades_a_session_that_never_saw_driving() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-G-05][ANCHOR-LOCK-001] A GEOFENCE_EXIT with no vehicle evidence at arm time
            // arms WITHOUT the seed. When DepartureDetectionWorker later confirms the departure,
            // notifyDepartureConfirmed() seeds the RUNNING session — but its verdict can rest on
            // the same falsifiable ENTER fall-through, so a session that never witnessed driving
            // itself must PROMPT, not save silently (2026-07-04 field incident: the late upgrade
            // silently saved a park the user had been asked about). A user "Sí" completes it.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) } // unverified: no seed

            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 2.8f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            assertFalse(env.coordinator.hasDetectedMovement, "sanity: unverified session starts guarded")

            // The sibling departure pipeline confirms mid-session.
            env.coordinator.notifyDepartureConfirmed()
            assertTrue(
                env.coordinator.hasDetectedMovement,
                "a confirmed departure verdict must seed the running session [DET-G-05]",
            )

            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "verified_late without observed driving must not save silently [ANCHOR-LOCK-001]",
            )
            assertEquals(1, env.notification.parkingConfirmationCallCount, "it must ask instead")

            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "user tap completes the save")
        }

    @Test
    fun should_ignore_departure_verdict_between_sessions() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-G-05] A verdict landing with no session running must not leak a seed into the
            // NEXT session — the walking-exit protection would silently vanish.
            val env = setup()
            env.coordinator.notifyDepartureConfirmed() // no session → no-op

            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 2.8f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            assertFalse(
                env.coordinator.hasDetectedMovement,
                "a between-sessions verdict must not seed the next session [DET-G-05]",
            )
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "walking burst must still abort when the verdict predates the session [DET-G-05]",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // BUG-OPPO-LATE-CONFIRM: EXIT + 8 steps → confirm without waiting for STILL
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_fast_confirm_when_exit_and_steps_arrive_before_slow_path_matures() =
        runTest(UnconfinedTestDispatcher()) {
            // Scenario: real parking on a phone where STILL arrives very late or never.
            // Today the coordinator would wait the slow-path's 5-min continuous-stop
            // requirement before reaching HIGH — and resetting stoppedSince on every walk
            // burst between stops pushes the confirm minutes after the real park. With
            // EXIT + minStepsToConfirm steps in hand, confirm immediately and anchor at
            // bestStopLocation. [BUG-OPPO-LATE-CONFIRM]
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Drive: origin + cross movement threshold.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            assertTrue(env.coordinator.hasDetectedMovement, "sanity: driving speed reached")

            // Park at (40.005, -3.7) — captures bestStopLocation AND the egress anchor in the
            // initial-stop window.
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            // AR EXIT arrives + 8 pedestrian steps fire. No STILL, no 5 min of stop.
            env.coordinator.onVehicleExit()
            env.stepDetector.emitSteps(8)

            // [DET-A] The user has now physically walked away: next stopped fix is ~33 m from the
            // park anchor (40.005 → 40.0053), past minEgressDisplacementMeters=18 m. The egress
            // gate is satisfied and the fast-confirm fires here.
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "EXIT + minStepsToConfirm steps + egress displacement must trigger an immediate confirm",
            )
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(
                40.005,
                saved.location.latitude,
                /* absoluteTolerance = */ 0.00001,
                "confirmed location must be the parked-car position (bestStopLocation), not the walking endpoint",
            )
            assertEquals(
                config.reliabilityVehicleExit,
                saved.detectionReliability ?: 0f,
                /* absoluteTolerance = */ 0.0001f,
                "reliability should be reliabilityVehicleExit — EXIT + steps is the same evidence class as the candidate steps path",
            )
        }

    @Test
    fun should_not_fast_confirm_when_only_exit_without_steps() =
        runTest(UnconfinedTestDispatcher()) {
            // Regression guard: EXIT alone (without the pedestrian-steps proof) must NOT
            // trigger the fast confirm — that's the long-stop-in-car case the slow path's
            // 5-min window deliberately protects against (queue at a garage gate, etc.).
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            env.coordinator.onVehicleExit()
            // No steps emitted.
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "EXIT without steps must not auto-confirm — slow path remains the gate",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-A: egress displacement gate — the Prague false positive
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_not_fast_confirm_when_exit_and_steps_arrive_without_egress_displacement() =
        runTest(UnconfinedTestDispatcher()) {
            // The Prague false positive, replayed. A spurious AR IN_VEHICLE_EXIT fires mid-trip
            // and, stuck in stop-and-go traffic, the phone bouncing in the user's pocket counts
            // ≥ minStepsToConfirm step events — all while the car never moved and the user never
            // left it. Pre-DET-A this satisfied `vehicleExitConfirmed && stepCount >= min` and
            // published a phantom spot. The egress gate requires real displacement from the
            // parked-car anchor, which never happens here, so no spot is saved. [DET-A]
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Drive: origin + cross movement threshold.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            assertTrue(env.coordinator.hasDetectedMovement, "sanity: driving speed reached")

            // Traffic-jam stop at (40.005) — egress anchor pinned here.
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            // Spurious EXIT + 8 bouncing-phone steps. The user is still in the car.
            env.coordinator.onVehicleExit()
            env.stepDetector.emitSteps(8)

            // Subsequent fixes stay essentially at the anchor (~1 m jitter, well under the
            // 18 m gate). The car never drove away, the user never walked away.
            locations.emit(GpsPoint(40.005009, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            locations.emit(GpsPoint(40.005000, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "EXIT + steps WITHOUT egress displacement must NOT confirm — this is the Prague phantom spot",
            )
        }

    @Test
    fun should_fast_confirm_on_steps_and_egress_without_any_vehicle_exit() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-D-03] Steps + egress confirm on their own — no AR IN_VEHICLE_EXIT required. A field
            // trace (2026-06-26) showed the confirm needlessly waiting ~16 s for the AR EXIT while
            // steps+egress were already satisfied. The egress gate is the decisive signal; the exit
            // requirement was redundant and fragile on hardware where EXIT is late/missing.
            val env = setup()
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Drive, then park at (40.005) — bestStopLocation pinned here.
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            // NO onVehicleExit() — only the pedestrian-steps proof + egress displacement.
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // ~33 m away

            job.cancelAndJoin()

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "steps + egress must confirm WITHOUT an AR vehicle-exit [DET-D-03]",
            )
            assertEquals(
                40.005,
                env.parkingRepo.getActiveSession()?.location?.latitude ?: 0.0,
                /* absoluteTolerance = */ 0.00001,
                "confirmed location must be the parked-car position (bestStopLocation)",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DET-C-02: post-confirm hold — errand re-anchor + finalize
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun should_discard_tentative_confirm_and_reanchor_at_final_spot_when_driving_resumes() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-C-02] The "buy tobacco" bug: the user parks at an errand spot, gets out and walks
            // to a kiosk (egress → tentative confirm), then drives on to park properly nearby. The
            // tentative confirm must be DISCARDED when driving resumes within the hold, and the park
            // must re-anchor at the FINAL spot — not pin the kiosk.
            var fakeNow = 1_000_000L
            val holdConfig = ParkingDetectionConfig(confirmHoldMs = 120_000L)
            val env = setup(config = holdConfig, clock = { fakeNow })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            // Drive, then park at the ERRAND spot (40.005); egress → tentative confirm (held, not saved).
            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // egress ~33 m
            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "must NOT confirm yet — held in the post-confirm window [DET-C-02]",
            )

            // 30 s into the hold the errand is over: drive off again → discard the tentative confirm.
            fakeNow += 30_000L
            locations.emit(GpsPoint(40.010, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "tentative confirm discarded — still nothing saved",
            )

            // Park for real at the FINAL spot (40.020); egress → new tentative confirm; let it settle.
            locations.emit(GpsPoint(40.020, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0203, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // egress ~33 m
            fakeNow += 120_001L
            locations.emit(GpsPoint(40.0203, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // hold elapsed

            job.cancelAndJoin()

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "exactly one confirm — the FINAL spot, after the hold settled [DET-C-02]",
            )
            assertEquals(
                40.020,
                env.parkingRepo.getActiveSession()?.location?.latitude ?: 0.0,
                /* absoluteTolerance = */ 0.0001,
                "park must anchor at the FINAL spot (40.020), not the errand stop (40.005)",
            )
        }

    @Test
    fun should_finalize_starved_hold_by_clock_when_gps_dies_after_parking() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-AUDIT-002 T7/M2] The COMMON egress: park, walk into the building, GPS dies.
            // Every hold decision used to wait for the NEXT fix — which never came — and the
            // tentatively-confirmed park died in silence (no pin, no notification). The watchdog
            // clock must finalize it at the pinned location and end the session.
            val holdConfig = ParkingDetectionConfig(confirmHoldMs = 120_000L)
            val env = setup(config = holdConfig)
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(GpsPoint(40.0, -3.7, accuracy = 5f, timestamp = 0L, speed = 6f)) // drive
            locations.emit(GpsPoint(40.001, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // park
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0013, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // egress ~33 m → held
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "held, nothing saved yet")

            // GPS dies — no more fixes, ever. Only virtual time advances.
            testScheduler.advanceUntilIdle()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "the clock, not a fix, must finalize the starved hold [DET-AUDIT-002 T7]")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(40.001, saved.location.latitude, 0.00005, "pin at the parked-car anchor")

            job.cancelAndJoin()
        }

    @Test
    fun should_save_with_user_reliability_when_user_confirms_during_the_hold() =
        runTest(UnconfinedTestDispatcher()) {
            // A "Sí" tapped while the tentative confirm is HELD is the USER-CONFIRMED path: the
            // save must carry reliabilityUserConfirmed (1.0) and the "user" label, not the 0.9 of
            // the auto path that opened the hold — the class KDoc promises it, and the repark
            // guard must never veto a park the user explicitly confirmed. [DET-C-02]
            var fakeNow = 1_000_000L
            val holdConfig = ParkingDetectionConfig(confirmHoldMs = 120_000L)
            val env = setup(config = holdConfig, clock = { fakeNow })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // egress → held
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "held, nothing saved yet")

            // 10 s into the hold the user taps "Sí" on the notification.
            fakeNow += 10_000L
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount)
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertEquals(
                holdConfig.reliabilityUserConfirmed,
                saved.detectionReliability ?: 0f,
                /* absoluteTolerance = */ 0.0001f,
                "a Sí during the hold saves as the USER path (1.0), not the auto reliability [DET-C-02]",
            )
        }

    @Test
    fun should_finalize_tentative_confirm_after_hold_when_car_stays_put() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-C-02] A genuine park: egress → tentative confirm → the car stays put → the hold
            // elapses → finalize at the parked-car position. Nothing is saved during the hold window.
            var fakeNow = 1_000_000L
            val holdConfig = ParkingDetectionConfig(confirmHoldMs = 120_000L)
            val env = setup(config = holdConfig, clock = { fakeNow })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job = launch { env.coordinator.invoke(locations) }

            locations.emit(stationaryFix(lat = 40.0, lon = -3.7))
            locations.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))
            env.stepDetector.emitSteps(8)
            locations.emit(GpsPoint(40.0053, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)) // egress → tentative
            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "held, not confirmed yet [DET-C-02]",
            )

            // Car stays put; hold elapses → finalize on the next stationary fix.
            fakeNow += 120_001L
            locations.emit(GpsPoint(40.005, -3.7, accuracy = 5f, timestamp = 0L, speed = 0f))

            job.cancelAndJoin()

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "finalized exactly once after the hold elapsed [DET-C-02]",
            )
            assertEquals(
                40.005,
                env.parkingRepo.getActiveSession()?.location?.latitude ?: 0.0,
                /* absoluteTolerance = */ 0.0001,
                "finalized at the parked-car position",
            )
        }

    @Test
    fun should_preserve_post_save_card_across_immediate_new_session() =
        runTest(UnconfinedTestDispatcher()) {
            // Scenario: auto-confirm succeeds → user walks → AR fires a SPURIOUS
            // IN_VEHICLE_ENTER → service restarts coordinator → new invoke() begins. The
            // freshly-posted revert card must NOT be wiped by the second session's
            // session-start dismiss. The timestamp gate (savedConfirmPostedAt vs
            // confirmationResponseTimeoutMs=15 min) protects it. [REFACTOR-300-FIX]
            val env = setup()

            // Session 1: drive + user-confirm → savedConfirm card posted.
            val session1 = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job1 = launch { env.coordinator.invoke(session1) }
            session1.emit(stationaryFix(lat = 40.0, lon = -3.7))
            session1.emit(GpsPoint(40.002, -3.7, accuracy = 5f, timestamp = 0L, speed = 10f))
            env.coordinator.onUserConfirmedParking()
            session1.emit(stationaryFix(lat = 40.002, lon = -3.7))
            job1.cancelAndJoin()

            assertEquals(1, env.notification.parkingSavedConfirmCallCount)
            assertEquals(
                "savedConfirm",
                env.notification.confirmationNotifOps.last(),
                "sanity: session 1 must end with the card visible",
            )
            val opsBeforeSession2 = env.notification.confirmationNotifOps.size

            // Session 2: simulate the spurious-ENTER restart. Just enter and exit the
            // coordinator with minimal traffic so its session-start path runs.
            val session2 = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
            val job2 = launch { env.coordinator.invoke(session2) }
            session2.emit(stationaryFix(lat = 40.002, lon = -3.7))
            job2.cancelAndJoin()

            // No new dismiss should have been recorded on the confirmation id. The card
            // remains visible — the LAST op is still the savedConfirm from session 1.
            assertEquals(
                opsBeforeSession2,
                env.notification.confirmationNotifOps.size,
                "session-start dismiss must be skipped while the revert card is still fresh",
            )
            assertEquals(
                "savedConfirm",
                env.notification.confirmationNotifOps.last(),
                "the revert card must remain the last op on PARKING_CONFIRMATION_NOTIFICATION_ID",
            )
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun stationaryFix(lat: Double, lon: Double): GpsPoint =
        GpsPoint(latitude = lat, longitude = lon, accuracy = 5f, timestamp = 0L, speed = 0f)

    private data class TestEnv(
        val coordinator: CoordinatorParkingDetector,
        val parkingRepo: FakeUserParkingRepository,
        val geofence: FakeGeofenceManager,
        val enrichment: FakeParkingEnrichmentScheduler,
        val notification: FakeAppNotificationManager,
        val stepDetector: FakeStepDetectorSource,
        val detectionLogger: FakeDetectionEventLogger,
    )
}

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

    @Test
    fun calle_gavia_001_correct_detection_still_anchors_at_calle_gavia() =
        runTest(UnconfinedTestDispatcher()) {
            // [ANCHOR-LOCK-001 regression guard] A CORRECT field detection: real drive, a traffic
            // stop whose phone jiggle fired 2 spurious steps (must NOT lock the anchor there),
            // real park on Calle Gavia. The session witnessed driving → silent confirm, anchored
            // at the car.
            val replayer = DetectionTraceReplayer(TRACE_CALLE_GAVIA_001)
            val env = buildEnv(clock = { replayer.nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 256)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.VerifiedByVehicleEnter(enterToExitMs = 60_000L),
                )
            }

            replayer.replay(
                emitFix = { locations.emit(it) },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "the correct park must save")
            val saved = env.parkingRepo.getActiveSession()
            assertTrue(
                saved != null && saved.location.latitude in 36.60238..36.60248,
                "park must anchor on Calle Gavia (36.60243), not the traffic stop (36.6027x/36.6029x) " +
                    "— was ${saved?.location?.latitude}",
            )
        }

    @Test
    fun supermarket_001_late_arm_prompts_and_a_user_yes_anchors_at_the_car() =
        runTest(UnconfinedTestDispatcher()) {
            // [ANCHOR-LOCK-001] The real complaint (2026-07-04): exit delivered so late the
            // session armed with the car already parked (stream never saw driving). It must:
            //  1. PROMPT at steps+egress (weak evidence) — never save silently;
            //  2. keep prompting-not-saving even after the departure worker's late upgrade
            //     (verified_late is weak too — pre-fix it silently saved);
            //  3. keep the anchor LOCKED at the car while the user wanders the store
            //     (pre-fix the indoor re-stops re-captured it and the pin drifted inside);
            //  4. on the user's "Sí", save anchored at the CAR.
            val fullTrace = TraceSupermarket001.park + TraceSupermarket001.wander
            val replayer = DetectionTraceReplayer(fullTrace)
            val env = buildEnv(clock = { replayer.nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 256)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.VerifiedByVehicleEnter(enterToExitMs = 120_000L),
                )
            }

            var upgraded = false
            replayer.replay(
                emitFix = { fix ->
                    // The departure worker's late verdict lands mid-wander (as in the field).
                    if (!upgraded && fix.timestamp >= TraceSupermarket001.park.last().tMs + 60_000L) {
                        upgraded = true
                        env.coordinator.notifyDepartureConfirmed()
                    }
                    locations.emit(fix)
                },
                emitStep = { env.stepDetector.emitSteps(1) },
            )

            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "weak evidence must never save silently")
            assertEquals(1, env.notification.parkingConfirmationCallCount, "the user must be asked exactly once")

            // The user answers "Sí" — the save must anchor at the CAR in the lot, not the store.
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(36.602173, -6.256817, accuracy = 9f, timestamp = replayer.nowMs, speed = 0.2f))
            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "user tap completes the save")
            val saved = env.parkingRepo.getActiveSession()
            assertTrue(
                saved != null &&
                    saved.location.latitude in 36.60205..36.60216 &&
                    saved.location.longitude in -6.25690..-6.25675,
                "park must anchor at the car in the lot (36.60212,-6.25682), " +
                    "not drift into the store — was ${saved?.location?.latitude},${saved?.location?.longitude}",
            )
        }

    @Test
    fun enamorados_001_sustained_departure_unfreezes_the_traffic_light_and_confirms_at_the_real_arrival() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-CREDIBLE-DRIVE-001 — the 2026-07-15 field FP, SOLVED at the root] The anchor
            // froze at a traffic stop and MIUI starved every later fix of credible accuracy
            // (10.12 m/s @ acc 52.4 fails ≤50 by 2.4 m) — in the field the pin landed 1.11 km
            // from the car. Displacement corroboration reads the track instead of the fix: the
            // position RAN 366 m from the frozen anchor at 13 m/s average — no walk, no ghost.
            // The anchor unfreezes mid-drive, re-freezes at the REAL arrival (best fix acc 5.7 m,
            // ~13 m from the user-confirmed car position), and the genuine kinematic egress
            // confirms THERE. The 1.11 km FP becomes a correct detection.
            val replayer = DetectionTraceReplayer(TraceEnamorados001.events)
            val env = buildEnv(clock = { replayer.nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 256)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified) }

            var arExitEmitted = false
            replayer.replay(
                emitFix = { fix ->
                    // The field AR IN_VEHICLE→EXIT landed at Δ 868 703 (the replayer only carries
                    // FIX/STEP, so the transition is injected here at its recorded time).
                    if (!arExitEmitted && fix.timestamp >= TraceEnamorados001.AR_EXIT_AT) {
                        arExitEmitted = true
                        env.coordinator.onVehicleExit()
                    }
                    locations.emit(fix)
                },
                emitStep = { env.stepDetector.emitSteps(1) },
            )

            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "the real park must save")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("confirmed_kinematic+egress", ended.outcome)
            val saved = env.parkingRepo.getActiveSession()
            assertTrue(
                saved != null &&
                    saved.location.latitude in 36.59790..36.59806 &&
                    saved.location.longitude in -6.25100..-6.25085,
                "park must anchor at the REAL arrival (~36.59799,-6.25093, 13 m from the " +
                    "user-confirmed car), NOT the frozen traffic light " +
                    "(${TraceEnamorados001.FROZEN_ANCHOR_LAT},${TraceEnamorados001.FROZEN_ANCHOR_LON}) " +
                    "— was ${saved?.location?.latitude},${saved?.location?.longitude}",
            )
        }

    @Test
    fun enamorados_001_without_recovery_fixes_the_ceiling_prompts_and_a_user_yes_anchors_at_the_car() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-ANCHOR-EGRESS-001 — the ceiling as LAST line of defence] Worst-case MIUI
            // variant: the stream never again shows anything above walking pace after the freeze,
            // so no sustained departure can corroborate and the anchor stays frozen at the
            // traffic light. The egress walk at Camelias is born 1.11 km from it — the ceiling
            // must degrade every auto-confirm to a PROMPT, and the user's "Sí" must anchor at
            // the user's CURRENT stop (the doorstep), never at the light.
            val replayer = DetectionTraceReplayer(TraceEnamorados001.eventsWithoutRecovery)
            val env = buildEnv(clock = { replayer.nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 256)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified) }

            var arExitEmitted = false
            replayer.replay(
                emitFix = { fix ->
                    if (!arExitEmitted && fix.timestamp >= TraceEnamorados001.AR_EXIT_AT) {
                        arExitEmitted = true
                        env.coordinator.onVehicleExit()
                    }
                    locations.emit(fix)
                },
                emitStep = { env.stepDetector.emitSteps(1) },
            )

            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "an anchor the egress disowns must never pin silently")
            assertEquals(1, env.notification.parkingConfirmationCallCount, "the user must be asked exactly once")
            assertTrue(
                env.detectionLogger.events.filterIsInstance<DetectionEvent.Decision>()
                    .any { it.outcome == "CONFIRM_DEGRADED_PROMPT" },
                "the degradation must be visible in diagnostics",
            )

            // The user answers "Sí" — the save must anchor at Camelias, not the traffic light.
            env.coordinator.onUserConfirmedParking()
            locations.emit(GpsPoint(36.5976, -6.2506, accuracy = 8f, timestamp = replayer.nowMs, speed = 0.1f))
            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "user tap completes the save")
            val saved = env.parkingRepo.getActiveSession()
            assertTrue(
                saved != null &&
                    saved.location.latitude in 36.5973..36.5978 &&
                    saved.location.longitude in -6.2508..-6.2504,
                "park must anchor at the user's stop at Camelias (${TraceEnamorados001.REAL_CAR_LAT}," +
                    "${TraceEnamorados001.REAL_CAR_LON}), NOT the frozen anchor " +
                    "(${TraceEnamorados001.FROZEN_ANCHOR_LAT},${TraceEnamorados001.FROZEN_ANCHOR_LON}) " +
                    "— was ${saved?.location?.latitude},${saved?.location?.longitude}",
            )
        }

    @Test
    fun enamorados_001_unattended_timeout_with_disowned_anchor_nudges_instead_of_saving() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-ANCHOR-EGRESS-001] Same trace, user IGNORES the prompt: 16 minutes later the
            // response-timeout fires. The unattended save trusted "pinned anchor" alone, which
            // would resurrect the exact FP the decision path degraded — with the egress born away
            // from the anchor it must nudge ("where did you park?") and abort, never pin.
            val quietTail = buildList {
                val lastMs = TraceEnamorados001.eventsWithoutRecovery.maxOf { it.tMs }
                repeat(3) { i ->
                    add(
                        TraceEvent(
                            lastMs + 16 * 60_000L + i * 5_000L, TraceEvent.Kind.FIX,
                            36.5976479, -6.2506502, 7.5f, 0.09f,
                        )
                    )
                }
            }
            val replayer = DetectionTraceReplayer(TraceEnamorados001.eventsWithoutRecovery + quietTail)
            val env = buildEnv(clock = { replayer.nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 256)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified) }

            var arExitEmitted = false
            replayer.replay(
                emitFix = { fix ->
                    if (!arExitEmitted && fix.timestamp >= TraceEnamorados001.AR_EXIT_AT) {
                        arExitEmitted = true
                        env.coordinator.onVehicleExit()
                    }
                    locations.emit(fix)
                },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "no unattended save at a disowned anchor")
            assertEquals(1, env.notification.markParkingNudgeCallCount, "the honest exit is the mark-parking nudge")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_unattended_egress_mismatch", ended.outcome)
        }

    @Test
    fun camelias_hop_001_trip_shorter_than_the_exit_latency_aborts_silently_today() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-HONEST-CLOSE-001 — CHARACTERIZATION, 2026-07-14 field FN] A ~300 m hop whose
            // fence EXIT was delivered with the trip already over (exitLoc at the NEW spot,
            // dep=self_observed). The session watches a pedestrian and the false-ENTER guard
            // kills it at the 8th step — correctly as a session, but SILENTLY: no release of the
            // stale pin, no prompt, no zone. The honest-close ladder will flip the silence.
            val replayer = DetectionTraceReplayer(TRACE_CAMELIAS_HOP_001)
            val env = buildEnv(clock = { replayer.nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 256)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified) }

            replayer.replay(
                emitFix = { locations.emit(it) },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "no pin without measured driving — correct")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_false_enter", ended.outcome, "field outcome reproduced")
            // Today's SILENCE, pinned explicitly — DET-HONEST-CLOSE-001 changes these two.
            assertEquals(0, env.notification.parkingConfirmationCallCount, "no prompt today")
            assertEquals(0, env.notification.markParkingNudgeCallCount, "no nudge today")
        }

    @Test
    fun camelias_oppo_001_walk_entered_anchor_prompts_instead_of_pinning_the_house() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-CREDIBLE-DRIVE-001 — the 2026-07-15 in-house pin, SOLVED] Ground truth (user +
            // Redmi AR EXIT): the car ended at ~36.597877,-6.250989; the field pin landed at the
            // house door 37 m away because the walk back from the reposition ran on a MUTE step
            // counter and its GPS recovery swing (2.5-4.9 m/s, out 68 m and back in 18 s)
            // laundered the walk odometer through the ambiguous band. Now:
            //  1. the mute ambiguous band can no longer prove CAR (the swing stays under the
            //     sustained-departure floor) → the walk odometer survives → the house stop reads
            //     WALK-ENTERED → freeze vetoed, anchor tainted;
            //  2. a walk-entered anchor degrades every auto-confirm to a PROMPT — the honest
            //     "you parked, but not here" (the true spot was never GPS-measured);
            //  3. DET-C-02 still discards the first tentative confirm on the Δ990 driving fix
            //     (this test runs the REAL 2-min hold).
            val replayer = DetectionTraceReplayer(TraceCameliasOppo001.events)
            val env = buildEnv(clock = { replayer.nowMs }, config = ParkingDetectionConfig())
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 700)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified) }

            replayer.replay(
                emitFix = { locations.emit(it) },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            assertEquals(
                0, env.parkingRepo.saveNewParkingSessionCallCount,
                "a walk-entered anchor (house door, 37 m from the car) must never pin silently",
            )
            assertEquals(1, env.notification.parkingConfirmationCallCount, "the user must be asked exactly once")
            assertTrue(
                env.detectionLogger.events.filterIsInstance<DetectionEvent.Decision>()
                    .any { it.outcome == "CONFIRM_DEGRADED_PROMPT" },
                "the degradation must be visible in diagnostics",
            )
        }

    @Test
    fun galeote_oppo_001_deceleration_must_not_taint_the_anchor_as_walk_entered() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-CREDIBLE-DRIVE-001 — CHARACTERIZATION, 2026-07-16 field FN] A textbook drive
            // and park at Calle Galeote 31; the Redmi in the same car confirmed 13 m away. The
            // Oppo's mute step counter turned the final DECELERATION (2.82, 2.95, 1.22, 1.03 m/s
            // rolling to the kerb) into 4 "walk" fixes → anchorWalkFixesAtCapture=4 > 3 → the
            // CORRECT anchor was tainted walk-entered → prompt, unanswered → nudge, no pin.
            // Every arrival traverses the pedestrian band — this fixture pins today's false
            // taint until displacement corroboration lands, then flips to a silent confirm.
            val replayer = DetectionTraceReplayer(TraceGaleoteOppo001.events)
            val env = buildEnv(clock = { replayer.nowMs }, config = ParkingDetectionConfig())
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 700)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.VerifiedByVehicleEnter(enterToExitMs = 60_000L),
                )
            }

            var arExitEmitted = false
            replayer.replay(
                emitFix = { fix ->
                    // The field AR IN_VEHICLE→EXIT landed at Δ 1 323 562 (16 min after the stop).
                    if (!arExitEmitted && fix.timestamp >= TraceGaleoteOppo001.AR_EXIT_AT) {
                        arExitEmitted = true
                        env.coordinator.onVehicleExit()
                    }
                    locations.emit(fix)
                },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            // ── Field behaviour reproduced (the false negative, pinned) ──────────
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "field: no pin")
            assertEquals(1, env.notification.parkingConfirmationCallCount, "field: prompted once")
            assertTrue(
                env.detectionLogger.events.filterIsInstance<DetectionEvent.Decision>()
                    .any { it.outcome == "CONFIRM_DEGRADED_PROMPT" },
                "field: the degradation is visible in diagnostics",
            )
            assertEquals(1, env.notification.markParkingNudgeCallCount, "field: unattended nudge")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_unattended_walk_entered_anchor", ended.outcome, "field outcome reproduced")
        }

    @Test
    fun late_exit_on_foot_001_walk_away_exit_must_abort_silently_forever() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-HONEST-CLOSE-001 — PERMANENT GUARD, 2026-07-15 field] The fence EXIT was
            // delivered late while the user was 1.1 km away ON FOOT and at rest; the car had NOT
            // moved. The silent no-movement abort is CORRECT and must survive the honest-close
            // ladder: the walk explains the distance (no ride proof), so no release, no zone,
            // no prompt — a nag here would assert the car is where the pedestrian is
            // (BUG-WALK-DEPART-001).
            val replayer = DetectionTraceReplayer(TRACE_LATE_EXIT_ON_FOOT_001)
            val env = buildEnv(clock = { replayer.nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 256)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified) }

            replayer.replay(
                emitFix = { locations.emit(it) },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "no pin — the car never moved")
            assertEquals(0, env.notification.parkingConfirmationCallCount, "no prompt — parked-and-away on foot")
            assertEquals(0, env.notification.markParkingNudgeCallCount, "no nudge — parked-and-away on foot")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_no_movement", ended.outcome, "field outcome reproduced")
        }

    // ── Env ───────────────────────────────────────────────────────────────────

    private class Env(
        val coordinator: CoordinatorParkingDetector,
        val parkingRepo: FakeUserParkingRepository,
        val notification: FakeAppNotificationManager,
        val stepDetector: FakeStepDetectorSource,
        val detectionLogger: FakeDetectionEventLogger,
    )

    private fun buildEnv(
        clock: () -> Long,
        // confirmHoldMs=0 by default: most replays end at the confirm moment; traces where the
        // errand-stop discard is load-bearing (Camelias-Oppo reposition) pass the real config.
        config: ParkingDetectionConfig = ParkingDetectionConfig(confirmHoldMs = 0L),
    ): Env {
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

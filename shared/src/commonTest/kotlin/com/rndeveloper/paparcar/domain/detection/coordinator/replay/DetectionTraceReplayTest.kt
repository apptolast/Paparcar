@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.detection.coordinator.replay

import com.rndeveloper.paparcar.fakes.FakeAddressAndPlaceRepository
import com.rndeveloper.paparcar.domain.usecase.notification.ResolveAskedStreetUseCase
import com.rndeveloper.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import com.rndeveloper.paparcar.domain.detection.ArmLabel
import com.rndeveloper.paparcar.domain.detection.CoordinatorParkingDetector
import com.rndeveloper.paparcar.domain.detection.state.DriveProofSource
import com.rndeveloper.paparcar.domain.detection.ArmEvidence
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.util.haversineMeters
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.CalculateParkingConfidenceUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.EvaluateParkingDecisionUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.EvaluateUnattendedParkingSaveUseCase
import com.rndeveloper.paparcar.fakes.FakeAppNotificationManager
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeDepartureEventBus
import com.rndeveloper.paparcar.fakes.FakeDetectionEventLogger
import com.rndeveloper.paparcar.fakes.FakeDetectionPhaseSink
import com.rndeveloper.paparcar.fakes.FakeFinalizeDeducedDeparture
import com.rndeveloper.paparcar.fakes.FakeRetractDeducedDeparture
import com.rndeveloper.paparcar.fakes.FakeGeofenceManager
import com.rndeveloper.paparcar.fakes.FakeParkingEnrichmentScheduler
import com.rndeveloper.paparcar.fakes.FakeStepDetectorSource
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import com.rndeveloper.paparcar.fakes.FakeZoneRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] These files exercise DETECTION, not wording: an
 *  empty geocoder keeps a question from ever naming a street here. The rule has its own test. */
private val noStreet = ResolveAskedStreetUseCase(GetAddressAndPlaceUseCase(FakeAddressAndPlaceRepository()))


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
    fun house_mirage_001_indoor_burst_must_not_re_park_the_car_inside_the_house() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001] The 2026-08-22 20:50 incident (Oppo).
            // Post-fix the arm is UNVERIFIED — the exit's own fix cannot corroborate the exit —
            // and the departure worker DISMISSES the departure 105.6 s in, which retracts whatever
            // trust the trigger had bought. What remains is 50 dead-still fixes and 12 indoor
            // steps, and nothing in that may plant a pin. Pre-fix this exact trace confirmed a
            // phantom park 49 m from the real one, replacing it and deleting its geofence.
            val replayer = DetectionTraceReplayer(TRACE_HOUSE_MIRAGE_001)
            val env = buildEnv(clock = { replayer.nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 256)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.Unverified,
                    armingGeofenceId = "ce3bb858",
                    departureAnchor = GpsPoint(
                        HOUSE_MIRAGE_001_REAL_PIN_LAT,
                        HOUSE_MIRAGE_001_REAL_PIN_LON,
                        accuracy = 2.2f,
                        timestamp = TRACE_HOUSE_MIRAGE_001.first().tMs - 12 * 60_000L,
                        speed = 0f,
                    ),
                    departureFenceRadiusMeters = 83f,
                )
            }

            var dismissalDelivered = false
            replayer.replay(
                emitFix = {
                    // The worker's verdict lands mid-stream, exactly where the device log has it.
                    if (!dismissalDelivered && replayer.nowMs >= HOUSE_MIRAGE_001_DISMISSED_AT_MS) {
                        dismissalDelivered = true
                        env.coordinator.notifyDepartureDismissed("ce3bb858")
                    }
                    locations.emit(it)
                },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            assertTrue(dismissalDelivered, "the trace must reach the worker's dismissal")
            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "an indoor mirage must never re-park the car inside the house",
            )
            // ⚠ It ends `ended`, NOT `aborted_false_enter` — and the difference is worth reading.
            // The mirage did not stop at the trigger: this session's FIRST fix still carries
            // 8.2 m/s at acc 5.6 m, and one such sample is enough to flip
            // `hasEverReachedDrivingSpeed` through the stream lane, which DISARMS the false-ENTER
            // guard even on an unverified arm. What saves this run is the arm LABEL: `self_observed`
            // keeps the repark-plausibility guard in `ConfirmParkingUseCase` awake, and verified
            // labels bypass it. So honesty at the arm is load-bearing here on its own.
            // The residual — a lone credible sample still moving the session lifecycle flag, the
            // hole DET-DRIVE-PROOF-001 deliberately left open — is tracked as
            // DET-LONE-SAMPLE-IS-NOT-A-DRIVE-001. Its replay lives right below.
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("ended", ended.outcome, "no confirm, no prompt — the session just runs out")
        }

    @Test
    fun house_mirage_001_a_verified_arm_label_is_what_the_retraction_takes_away() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001] The seed is the load-bearing half. Same
            // trace, same dismissal — but armed the way the OLD verifier armed it, `verified_speed`
            // on the exit's own echo. Without the retraction this is the exact run that planted the
            // phantom pin; with it, the guard the seed had disarmed does its job.
            val replayer = DetectionTraceReplayer(TRACE_HOUSE_MIRAGE_001)
            val env = buildEnv(clock = { replayer.nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 256)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.VerifiedBySpeed(speedKmh = 36f, accuracyM = 5.5f),
                    armingGeofenceId = "ce3bb858",
                )
            }

            var dismissalDelivered = false
            replayer.replay(
                emitFix = {
                    if (!dismissalDelivered && replayer.nowMs >= HOUSE_MIRAGE_001_DISMISSED_AT_MS) {
                        dismissalDelivered = true
                        env.coordinator.notifyDepartureDismissed("ce3bb858")
                    }
                    locations.emit(it)
                },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "no phantom re-park")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_false_enter", ended.outcome)
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
    fun enamorados_001_unattended_timeout_with_disowned_anchor_saves_zone_at_the_egress_birth() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-ANCHOR-EGRESS-001] Same trace, user IGNORES the prompt: 16 minutes later the
            // response-timeout fires. The unattended save trusted "pinned anchor" alone, which
            // would resurrect the exact FP the decision path degraded — the anchor (a traffic
            // light 1.11 km back) must never be pinned. [DET-FROZEN-COUNTER-001] But the trip WAS
            // measured and the egress BIRTH is where the walking began — the car. The honest exit
            // keeps the park as an approximate ZONE centered there (radius capped) instead of
            // losing it to a nudge nobody sees (field 2026-07-25/26, Redmi: this exact guard
            // turned a fully-measured 33-min drive home into a lost parking).
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

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "the park must be KEPT as a zone, not lost")
            val saved = assertNotNull(env.parkingRepo.getActiveSession())
            assertTrue(saved.isApproximate, "a disowned anchor may only yield an AREA, never an exact pin")
            assertTrue(
                saved.location.latitude != TraceEnamorados001.FROZEN_ANCHOR_LAT,
                "the zone must center on the egress birth, never the disowned anchor",
            )
            assertEquals(0, env.notification.markParkingNudgeCallCount, "the saved-parking card is the ask — no extra nudge")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("confirmed_unattended_zone_egress_mismatch", ended.outcome)
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
            // [DET-HONEST-CLOSE-001] The coordinator SURFACES the abort context so the service can
            // run the honest-close ladder above it: the terminal outcome and the last fix (near
            // Camelias, the new spot) survive invoke's reset().
            assertEquals("aborted_false_enter", env.coordinator.lastSessionOutcome)
            val abortFix = env.coordinator.lastSessionFix
            assertTrue(
                abortFix != null &&
                    abortFix.latitude in 36.5970..36.5978 && abortFix.longitude in -6.2510..-6.2500,
                "the abort fix must be surfaced at the new spot (Camelias), was ${abortFix?.latitude},${abortFix?.longitude}",
            )
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
            // [DET-CREDIBLE-DRIVE-001 — the 2026-07-16 field FN, SOLVED] A textbook drive and
            // park at Calle Galeote 31; the Redmi in the same car confirmed 13 m away, yet the
            // field build degraded to an unanswered prompt: the Oppo's mute step counter turned
            // the final DECELERATION (2.82, 2.95, 1.22, 1.03 m/s rolling to the kerb) into 4
            // "walk" fixes → anchorWalkFixesAtCapture=4 > 3 → the CORRECT anchor was tainted
            // walk-entered. Every arrival traverses the pedestrian band — displacement
            // corroboration now reads the hop the position PROVABLY made (23.7 m in 5 s against
            // 9.9 m of joint accuracy = CAR) and the odometer resets mid-deceleration: only the
            // 2 true pedestrian-band fixes remain (≤ 3), the anchor stays clean, and the park
            // confirms silently at the true anchor. Runs the REAL 2-min confirm hold.
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

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "the correct park must save silently")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("confirmed_steps+egress", ended.outcome)
            assertTrue(
                env.detectionLogger.events.filterIsInstance<DetectionEvent.Decision>()
                    .none { it.outcome == "CONFIRM_DEGRADED_PROMPT" },
                "no degradation — the anchor is the car's rest, not a walk-in",
            )
            assertEquals(0, env.notification.markParkingNudgeCallCount, "no nudge — nothing to ask")
            val saved = env.parkingRepo.getActiveSession()
            assertTrue(
                saved != null &&
                    saved.location.latitude in 36.60875..36.60895 &&
                    saved.location.longitude in -6.27838..-6.27818,
                "park must pin at the true anchor (~${TraceGaleoteOppo001.FIELD_ANCHOR_LAT}," +
                    "${TraceGaleoteOppo001.FIELD_ANCHOR_LON}, 13 m from the Redmi's confirm at " +
                    "${TraceGaleoteOppo001.REDMI_PIN_LAT},${TraceGaleoteOppo001.REDMI_PIN_LON}) " +
                    "— was ${saved?.location?.latitude},${saved?.location?.longitude}",
            )
        }

    @Test
    fun redmi_late_exit_home_001_no_drive_timeout_keeps_the_park_as_a_zone_at_the_locked_anchor() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-NODRIVE-ZONE-001 — the 2026-07-27 20:36 field FN, SOLVED] MIUI delivered the
            // GEOFENCE_EXIT 4 110 m late: the session was born at the destination, its only
            // "driving" a 3-fix burst (vmax 7.02 m/s) the track rightly never corroborates
            // (DET-DRIVE-PROOF-001). 176 LIVE egress steps locked the kerb anchor, the AR
            // vehicle-exit landed in-session, the prompt went unanswered — and the field build's
            // no-drive timeout exited nudge-only, losing a REAL park. Now the conjunction (live
            // egress-scale steps + real walked displacement + vehicular signal) keeps it as an
            // honest AREA at the locked kerb anchor. The same-day mirage (1 step, no AR exit)
            // still dies nudge-only — see the unit guards.
            val replayer = DetectionTraceReplayer(TraceRedmiLateExitHome001.events)
            val env = buildEnv(clock = { replayer.nowMs })
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
                    // The field AR IN_VEHICLE→EXIT landed at Δ 108 227 (2 s after the degraded prompt).
                    if (!arExitEmitted && fix.timestamp >= TraceRedmiLateExitHome001.AR_EXIT_AT) {
                        arExitEmitted = true
                        env.coordinator.onVehicleExit()
                    }
                    locations.emit(fix)
                },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            assertTrue(
                env.detectionLogger.events.filterIsInstance<DetectionEvent.Decision>()
                    .any { it.outcome == "CONFIRM_DEGRADED_PROMPT" },
                "no corroborated driving — the confirm must degrade to a prompt first, as in the field",
            )
            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "the park must be KEPT as a zone, not lost")
            val saved = assertNotNull(env.parkingRepo.getActiveSession())
            assertTrue(saved.isApproximate, "no proven driving may only yield an AREA, never an exact pin")
            assertTrue(
                saved.location.latitude in 36.60380..36.60395 &&
                    saved.location.longitude in -6.23092..-6.23072,
                "zone must center on the locked kerb anchor (~${TraceRedmiLateExitHome001.KERB_ANCHOR_LAT}," +
                    "${TraceRedmiLateExitHome001.KERB_ANCHOR_LON}), not the house door " +
                    "(${TraceRedmiLateExitHome001.HOUSE_LAT},${TraceRedmiLateExitHome001.HOUSE_LON}) " +
                    "— was ${saved.location.latitude},${saved.location.longitude}",
            )
            assertEquals(0, env.notification.markParkingNudgeCallCount, "the saved-parking card is the ask — no extra nudge")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("confirmed_unattended_zone_no_drive_egress", ended.outcome)
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

    @Test
    fun motorway_redmi_001_a_cycling_stamp_must_not_outrank_131_kmh_of_measured_driving() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 — the 2026-08-20 field session] 102 minutes,
            // 967 fixes, 109 in the driving band, 131,4 km/h peak at 4,6 m accuracy, and an AR
            // `IN_VEHICLE EXIT` at the destination. The session still died judged HUMAN-POWERED,
            // saving nothing, because a single AR `ON_BICYCLE` stamp — delivered mid-drive, after
            // the boarding that armed the trip — outranked every measurement the stream made.
            //
            // The stamp is injected here because AR labels never reach the remote trace (the
            // receiver only logs them to logcat); everything else is the field stream 1:1.
            val replayer = DetectionTraceReplayer(TRACE_MOTORWAY_REDMI_001)
            val env = buildEnv(clock = { replayer.nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 512)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.VerifiedByVehicleEnter(enterToExitMs = 60_000L),
                )
            }
            // The boarding AR vouched for at arm time, then the cycling stamp 5 minutes into the
            // motorway leg: by wall-clock order the bicycle is "the last word".
            env.coordinator.onVehicleRide(TRACE_MOTORWAY_REDMI_001.first().tMs - 60_000L)
            var cyclingStamped = false

            replayer.replay(
                emitFix = { fix ->
                    if (!cyclingStamped && fix.timestamp - TRACE_MOTORWAY_REDMI_001.first().tMs >= 300_000L) {
                        cyclingStamped = true
                        env.coordinator.onHumanPoweredRide(fix.timestamp)
                    }
                    locations.emit(fix)
                },
                emitStep = { env.stepDetector.emitSteps(1) },
                emitVehicleExit = { env.coordinator.onVehicleExit() },
            )
            job.cancelAndJoin()

            assertEquals(
                1, env.parkingRepo.saveNewParkingSessionCallCount,
                "a motorway drive that ends parked must leave a pin — the field session left none",
            )
            val ended = env.detectionLogger.events.filterIsInstance<DetectionEvent.SessionEnded>()
            assertTrue(
                ended.isNotEmpty() && ended.single().outcome.startsWith("confirmed_"),
                "the session must reach a verdict of its own; field outcome was " +
                    "'${ended.singleOrNull()?.outcome}' after 102 minutes",
            )
        }

    @Test
    fun motorway_redmi_001_without_the_cycling_stamp_the_same_stream_confirms_at_the_first_stop() =
        runTest(UnconfinedTestDispatcher()) {
            // Control for the test above — the stamp is the ONLY difference between the two runs,
            // and it is what separated this trace from the Oppo's, which confirmed the same
            // arrival at 0.9 one minute 54 seconds after the car stopped.
            val replayer = DetectionTraceReplayer(TRACE_MOTORWAY_REDMI_001)
            val env = buildEnv(clock = { replayer.nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 512)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.VerifiedByVehicleEnter(enterToExitMs = 60_000L),
                )
            }

            replayer.replay(
                emitFix = { locations.emit(it) },
                emitStep = { env.stepDetector.emitSteps(1) },
                emitVehicleExit = { env.coordinator.onVehicleExit() },
            )
            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "the park is unambiguous")
        }

    @Test
    fun camelias_gondola_001_a_stop_that_moved_122_m_must_not_pin_the_mouth_of_the_street() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-STOP-MUST-BE-STILL-IN-SPACE-001 — trip 2 of 2026-08-22, Oppo] Three arrival
            // fixes REPORTED zero speed while the position MEASURED 122.5 m in 9.56 s at 6-11 m
            // accuracy. The field build accepted them as a matured stop, froze the anchor on the
            // third, and pinned the mouth of the street 70 m short of the car. Runs the REAL 2-min
            // hold, as the field did.
            val replayer = DetectionTraceReplayer(TraceCameliasGondola001.events)
            val env = buildEnv(clock = { replayer.nowMs }, config = ParkingDetectionConfig())
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 512)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified) }

            replayer.replay(
                emitFix = { locations.emit(it) },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "the park is real and must save")
            val saved = assertNotNull(env.parkingRepo.getActiveSession())
            assertTrue(
                saved.location.latitude in 36.60862..36.60878 &&
                    saved.location.longitude in -6.27840..-6.27815,
                "pin must land at the car's true rest (~${TraceCameliasGondola001.REAL_SPOT_LAT}," +
                    "${TraceCameliasGondola001.REAL_SPOT_LON}), not the moving 'stop' at the street " +
                    "mouth (${TraceCameliasGondola001.FIELD_PIN_LAT}," +
                    "${TraceCameliasGondola001.FIELD_PIN_LON}) " +
                    "— was ${saved.location.latitude},${saved.location.longitude}",
            )
        }

    @Test
    fun gondola_camelias_001_the_egress_walk_must_not_be_judged_a_bicycle() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001 — trip 1 of 2026-08-22, Redmi] A 75 km/h
            // car trip vetoed as human-powered 36 s AFTER the anchor froze, on steps the log itself
            // labels `egress walk, anchor set`. The pedal-cadence latch must not be able to accuse
            // a walk that begins where the car came to rest.
            //
            // This trace carries all three of that day's fixes at once, and what it pins is the
            // ORDER they now resolve in:
            //  1. the bicycle accusation is gone — no `human_powered` anywhere;
            //  2. the confirm STILL degrades, and that is correct: there is a real 100.5 s GPS
            //     hole between the last driving fix (loc#39, 6.8 m/s) and the first stopped one
            //     (loc#40), so the app genuinely never saw where the car came to rest. The reason
            //     it gives is now the honest one, `anchor_gap_entered` [DET-GAP-ANCHOR-ZONE-001];
            //  3. the user's “Sí” (Δ 584 231, exactly where the field tap landed) completes the
            //     save, at the car's stopped-fix cluster (36.59766,-6.25062) — not out where
            //     the walker was;
            //  4. and because that stop was entered through the hole, the save carries the hole's
            //     doubt as a RADIUS instead of claiming an exact point
            //     [DET-USER-YES-IS-NOT-A-COORDINATE-001].
            //
            // ⚠ What (3) does NOT prove: forcing the answer path to take the current fix
            // instead of the witnessed stop leaves this trace byte-identical, because the user
            // answered while still beside the car. That assertion is a positional regression
            // guard, NOT a test of [DET-CONFIRM-ANCHOR-001] — that guard needs a trace where
            // the tap lands far away, and supermarket_001 above is the one that has it.
            val replayer = DetectionTraceReplayer(TraceGondolaCamelias001.events)
            val env = buildEnv(clock = { replayer.nowMs }, config = ParkingDetectionConfig())
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 512)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified) }

            var rideStamped = false
            var userAnswered = false
            replayer.replay(
                emitFix = { fix ->
                    // The AR IN_VEHICLE ENTER landed mid-drive, carrying its own true time.
                    if (!rideStamped && fix.timestamp >= TraceGondolaCamelias001.AR_RIDE_DELIVERED_AT_MS) {
                        rideStamped = true
                        env.coordinator.onVehicleRide(TraceGondolaCamelias001.AR_RIDE_TRUE_TIME_MS)
                    }
                    // The user's "Sí", at the second the field tap landed.
                    if (!userAnswered && fix.timestamp >= TraceGondolaCamelias001.USER_YES_AT_MS) {
                        userAnswered = true
                        env.coordinator.onUserConfirmedParking()
                    }
                    locations.emit(fix)
                },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            assertTrue(rideStamped && userAnswered, "the trace must reach both the AR stamp and the user's tap")

            val decisions = env.detectionLogger.events.filterIsInstance<DetectionEvent.Decision>()
            assertTrue(
                decisions.none { it.reason == "human_powered" },
                "a measured 75 km/h drive is not a bicycle — decisions were " +
                    decisions.joinToString { "${it.outcome}/${it.reason}" },
            )
            // The doubt that DOES survive is real and must keep saying so: a 100.5 s hole.
            assertTrue(
                decisions.any { it.outcome == "CONFIRM_DEGRADED_PROMPT" && it.reason == "anchor_gap_entered" },
                "the GPS hole must degrade the confirm, and name itself — decisions were " +
                    decisions.joinToString { "${it.outcome}/${it.reason}" },
            )
            assertEquals(1, env.notification.parkingConfirmationCallCount, "the user must be asked exactly once")

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "the user's yes completes the save")
            val saved = assertNotNull(env.parkingRepo.getActiveSession())
            assertTrue(
                saved.location.latitude in 36.59765..36.59785 &&
                    saved.location.longitude in -6.25065..-6.25050,
                "the pin must sit at the car's stopped-fix cluster near " +
                    "${TraceGondolaCamelias001.REAL_SPOT_LAT},${TraceGondolaCamelias001.REAL_SPOT_LON} " +
                    "— was ${saved.location.latitude},${saved.location.longitude}",
            )
            // [DET-USER-YES-IS-NOT-A-COORDINATE-001] The answer settles WHETHER, never WHERE: this
            // stop was entered through the 100.5 s hole, so the save must carry that doubt as a
            // radius instead of asserting an exact point.
            assertTrue(saved.isApproximate, "a gap-born anchor may only be saved as an area")
        }

    @Test
    fun redmi_2808_a_stop_refuted_four_times_must_not_park_the_car_inside_the_house() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-REFUTED-STILLNESS-CANNOT-MATURE-AN-ANCHOR-001 — the 2026-08-28 night FP, 1:1]
            // Driving home on network fixes (64–266 m, declared 0 m/s), the mid-route stop was
            // refuted FOUR times and still matured by TIME, freezing the anchor 3.5 km from the
            // park. Everything downstream inherited the lie: no auto-confirm (egress measured
            // against the bogus anchor), a mirage "sustained departure" from it, a re-freeze on
            // the first indoor fix, and the user's "Sí" pinning the house at reliability 1.0.
            //
            // What the fix must buy on this exact stream: refuted stillness leaves no inheritance,
            // so the save — whichever lane completes it — must either sit AT the car (Oppo ground
            // truth, healthy GPS, 5.25 m) or admit its doubt as an AREA. The one shape that may
            // never come back is the field one: an EXACT pin tens of metres away, indoors.
            //
            // Both guards were verified by NEUTRALIZATION against this fixture:
            //  - refuted-stillness off (evidenceSince=startedAt, no disown) → the replay reproduces
            //    the field pin BYTE FOR BYTE: path=user rel=1.0 exact at 36.6084105,-6.2780907;
            //  - doubt floor off (zoneRadius passthrough) → steps+egress saves an EXACT pin at the
            //    92.9 m network fix, 52 m from the car (the same FP one street over).
            // With both live, steps+egress saves a silent honest ZONE (r≈93 m) covering the car,
            // ~12 minutes before the field build got around to asking.
            val replayer = DetectionTraceReplayer(TRACE_REDMI_2808_REFUTED_STILLNESS)
            val env = buildEnv(clock = { replayer.nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 700)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.Unverified,
                    armingGeofenceId = "5dd8008f",
                    departureAnchor = GpsPoint(
                        REDMI_2808_DEPARTURE_ANCHOR_LAT,
                        REDMI_2808_DEPARTURE_ANCHOR_LON,
                        accuracy = 8.1f,
                        timestamp = TRACE_REDMI_2808_REFUTED_STILLNESS.first().tMs - 187_000L,
                        speed = 0f,
                    ),
                    departureFenceRadiusMeters = 125f,
                )
            }
            // The AR IN_VEHICLE ENTER that locked the vehicle at 00:52:11.
            env.coordinator.onVehicleRide(REDMI_2808_BOARDING_TRUE_TIME_MS)

            var userAnswered = false
            replayer.replay(
                emitFix = { fix ->
                    // The badge tap, at the second the field log recorded it (01:18:12.3).
                    if (!userAnswered && fix.timestamp >= REDMI_2808_USER_YES_AT_MS) {
                        userAnswered = true
                        env.coordinator.onUserConfirmedParking()
                    }
                    locations.emit(fix)
                },
                emitStep = { env.stepDetector.emitSteps(1) },
                emitVehicleExit = { env.coordinator.onVehicleExit() },
            )
            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "the park is real and must save")
            val saved = assertNotNull(env.parkingRepo.getActiveSession())
            val metersFromCar = haversineMeters(
                saved.location.latitude, saved.location.longitude,
                REDMI_2808_REAL_CAR_LAT, REDMI_2808_REAL_CAR_LON,
            )
            // The FP, formalized: an EXACT pin may only claim a spot the evidence puts the car at.
            assertTrue(
                saved.isApproximate || metersFromCar <= 30.0,
                "an exact pin ${metersFromCar.toInt()} m from the car is the field FP (house pin " +
                    "$REDMI_2808_HOUSE_PIN_LAT,$REDMI_2808_HOUSE_PIN_LON was 41 m out) — " +
                    "was ${saved.location.latitude},${saved.location.longitude} " +
                    "isApproximate=${saved.isApproximate}",
            )
            // And wherever the claim centers, it must be the arrival — never the mid-route anchor
            // the refuted stop matured (3.5 km back), which is what the old TIME credit produced.
            assertTrue(
                metersFromCar <= 150.0,
                "the save must center at the arrival, not down the route — " +
                    "was ${saved.location.latitude},${saved.location.longitude}, " +
                    "${metersFromCar.toInt()} m from the car",
            )
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertTrue(
                ended.outcome.startsWith("confirmed_"),
                "the session must end in a confirm (field: confirmed_user on the house pin) — " +
                    "was '${ended.outcome}'",
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

    // ── DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001 ────────────────────────────────────────
    //
    // The pair below is the whole ticket, replayed against the REAL detector on the REAL stream of
    // the superseded session. Identical trace, identical anchor, identical everything except what
    // the supersede handed over. The first test reproduces the field loss; the second is the fix.

    /** The anchor the field arm used: the pin the AR ENTER resolved as "your car". */
    // ── [DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001] the 2026-08-26 park the veto ate ───────────

    /** Where the car sat in Calle Valdés: the session's own first fix, taken beside it on waking. */
    private fun valdesAnchor() = GpsPoint(
        latitude = 36.5961565,
        longitude = -6.2329631,
        accuracy = 26.102f,
        timestamp = TRACE_GONDOLA_2608_CADENCE_VETO.first().tMs - 60_000L,
        speed = 0f,
    )

    /** The AR lane of the 2026-08-26 session, at the true transition times the log recorded.
     *  Injected rather than carried as events — `IN_VEHICLE ENTER` has no `TraceEvent.Kind`, and
     *  inventing one would put a synthetic value inside a fixture that is otherwise the field stream
     *  1:1. There was no `ON_BICYCLE` stamp anywhere in this session; that absence is the point. */
    private fun CoordinatorParkingDetector.stampBoarding() =
        onVehicleRide(TRACE_GONDOLA_2608_CADENCE_VETO.first().tMs - 2_610L + 64_320L)

    @Test
    fun gondola_2608_a_witnessed_boarding_and_a_measured_motor_must_outlive_the_cadence_latch() =
        runTest(UnconfinedTestDispatcher()) {
            // THE FIELD SESSION, 1:1. 341 fixes, 165 steps, 39 minutes. The cadence latched at
            // Δ183 s off six city fixes reading 11-17 km/h; the band clock never recovered because
            // OEM batching spread the in-band fixes 163 s and 200 s apart. The car arrived home at
            // Δ1 354 s, the anchor froze on the spot at Δ1 381 s, 32 egress steps followed — and the
            // app asked instead of saving. Nobody answered, and 15 minutes later the park was gone.
            val replayer = DetectionTraceReplayer(TRACE_GONDOLA_2608_CADENCE_VETO)
            val env = buildEnv(clock = { replayer.nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 512)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.Unverified,
                    departureAnchor = valdesAnchor(),
                    departureFenceRadiusMeters = 85f,
                )
            }
            env.coordinator.stampBoarding()

            replayer.replay(
                emitFix = { locations.emit(it) },
                emitStep = { env.stepDetector.emitSteps(1) },
                emitVehicleExit = { env.coordinator.onVehicleExit() },
            )
            job.cancelAndJoin()

            assertEquals(
                1, env.parkingRepo.saveNewParkingSessionCallCount,
                "the arrival is unambiguous and the veto had two independent refutations available",
            )
            val saved = assertNotNull(env.parkingRepo.getActiveSession())
            // The spot: every fix from Δ1 366 s on sits within a few metres of 36.60874,-6.27817.
            // The traffic stop the anchor first froze on — the one the first prompt fired at — is
            // 36.5973,-6.2252, five kilometres away, so this window discriminates the two.
            assertTrue(
                saved.location.latitude in 36.6085..36.6090 &&
                    saved.location.longitude in -6.2784..-6.2779,
                "the pin must land at Góndola 1, not at the traffic stop the anchor first froze on " +
                    "— was ${saved.location.latitude},${saved.location.longitude}",
            )
            val ended = env.detectionLogger.events.filterIsInstance<DetectionEvent.SessionEnded>()
            assertTrue(
                ended.isNotEmpty() && ended.single().outcome.startsWith("confirmed_"),
                "field outcome was the 15-minute 'Ask(HUMAN_POWERED)'; got '${ended.singleOrNull()?.outcome}'",
            )
        }

    @Test
    fun gondola_2608_the_displacement_refutation_alone_carries_the_same_stream() =
        runTest(UnconfinedTestDispatcher()) {
            // Door B in isolation ON REAL DATA: the boarding is NOT stamped, so the cadence latch
            // stands until something measured refutes it — and the only thing that can is the
            // sustained rate from the anchor (640 m at 24,6 m/s), because the band CLOCK is starved
            // to ~1 s by the same batching the fixture records.
            //
            // ⚠ What this pair does and does not pin down. The two tests together cannot separate
            // the doors by themselves — the test above has BOTH available. The separation was made
            // by neutralising each door in the production source and re-running, and it came out:
            //
            //   both doors live      → both tests pass
            //   door A neutralised   → both pass (the displacement carries them)
            //   door B neutralised   → the test above passes on the BOARDING ALONE; this one fails
            //   both neutralised     → both fail, reproducing the field false negative
            //
            // So door A does carry this real stream on its own; what no permanent test here can
            // assert is that it would still carry it if the trip had never left the city, because
            // this trip did reach 94 km/h. That case is pinned in
            // `HumanPoweredRideTest.should_notVeto_when_cadenceFiredOnACityDriveArHadAlreadyWitnessed`
            // with the numbers the log recorded.
            val replayer = DetectionTraceReplayer(TRACE_GONDOLA_2608_CADENCE_VETO)
            val env = buildEnv(clock = { replayer.nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 512)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.Unverified,
                    departureAnchor = valdesAnchor(),
                    departureFenceRadiusMeters = 85f,
                )
            }

            replayer.replay(
                emitFix = { locations.emit(it) },
                emitStep = { env.stepDetector.emitSteps(1) },
                emitVehicleExit = { env.coordinator.onVehicleExit() },
            )
            job.cancelAndJoin()

            assertEquals(
                1, env.parkingRepo.saveNewParkingSessionCallCount,
                "ground the car provably covered refutes the cadence with no AR help at all",
            )
        }

    private fun gondolaAnchor() = GpsPoint(
        latitude = 36.608368,
        longitude = -6.2781358,
        accuracy = 3.603f,
        timestamp = TRACE_GONDOLA_2508_SUPERSEDE.first().tMs - 60_000L,
        speed = 0f,
    )

    @Test
    fun gondola_2508_supersede_loses_the_park_when_the_successor_inherits_nothing() =
        runTest(UnconfinedTestDispatcher()) {
            // The field build, byte for byte: the successor arms with BoardingAtCar — "waiting for
            // ride proof" — and its own stream can never supply that proof, because the drive it is
            // the tail of happened in the session that was just cancelled. 13,6 km/h peak, then the
            // egress walk, then the anti-walking guard. The car spent the night with no pin.
            val replayer = DetectionTraceReplayer(TRACE_GONDOLA_2508_SUPERSEDE)
            val env = buildEnv(clock = { replayer.nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 256)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.BoardingAtCar,
                    departureAnchor = gondolaAnchor(),
                    departureFenceRadiusMeters = 85f,
                )
            }

            replayer.replay(
                emitFix = { locations.emit(it) },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount, "the field false negative")
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("aborted_false_enter", ended.outcome, "and this is the guard that ate it")
        }

    @Test
    fun gondola_2508_supersede_keeps_the_session_alive_when_the_measured_drive_travels() =
        runTest(UnconfinedTestDispatcher()) {
            // Same stream, same anchor — the ONLY difference is that the supersede handed over what
            // the predecessor had measured (27,2 m/s, corroborated across its track). The seed
            // disarms the anti-walking guards, so the egress walk no longer refutes anything and the
            // session is still ALIVE when the trace runs out.
            //
            // ⚠ What this test does NOT show, and must not claim: the confirm. The field session was
            // killed at Δ78 940 ms, and at that instant the phone was still ~7 m from the car — the
            // egress displacement a `steps+egress` confirm needs simply does not exist inside this
            // window. The Redmi, on the same car with a session nobody superseded, confirmed about
            // two minutes later. So the trace ends `ended` for the honest reason: the replay ran out
            // of recording, not because a verdict was reached. The confirm the seed unlocks is
            // pinned on a trace that contains its moment — see the test below.
            val replayer = DetectionTraceReplayer(TRACE_GONDOLA_2508_SUPERSEDE)
            val env = buildEnv(clock = { replayer.nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 256)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.InheritedDrive(
                        maxSpeedMps = 27.2f,
                        source = DriveProofSource.TRACK_WINDOW,
                    ),
                    departureAnchor = gondolaAnchor(),
                    departureFenceRadiusMeters = 85f,
                )
            }

            replayer.replay(
                emitFix = { locations.emit(it) },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals(
                "ended",
                ended.outcome,
                "the 12 egress steps must no longer refute a drive this trip already proved",
            )
            // And nothing was invented on the way: no pin, no prompt, from evidence that ends early.
            assertEquals(0, env.parkingRepo.saveNewParkingSessionCallCount)
            assertEquals(0, env.notification.parkingConfirmationCallCount)
        }

    /**
     * The other half of the claim, on a trace that DOES contain its confirming moment.
     * `TRACE_BUG_REPARK_WALK_001` already pins that a speed-verified arm confirms at the stop anchor
     * where an unverified one aborts. An inherited drive must buy exactly that same thing — it is
     * the stronger evidence of the two, since it was measured rather than lent.
     */
    @Test
    fun an_inherited_drive_unlocks_the_same_confirm_a_verified_departure_does() =
        runTest(UnconfinedTestDispatcher()) {
            val replayer = DetectionTraceReplayer(TRACE_BUG_REPARK_WALK_001)
            val env = buildEnv(clock = { replayer.nowMs })
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 256)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.InheritedDrive(
                        maxSpeedMps = 27.2f,
                        source = DriveProofSource.TRACK_WINDOW,
                    ),
                )
            }

            replayer.replay(
                emitFix = { locations.emit(it) },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            assertEquals(1, env.parkingRepo.saveNewParkingSessionCallCount, "inherited drive must confirm")
            val saved = env.parkingRepo.getActiveSession()
            assertNotNull(saved)
            assertTrue(
                saved.location.latitude in 36.60460..36.60470,
                "same anchor as the verified arm, was ${saved.location.latitude}",
            )
            assertEquals(
                ArmLabel.INHERITED_DRIVE.persisted,
                saved.armEvidence,
                "and the pin must SAY the drive was inherited — a pin without provenance is not diagnosable",
            )
        }

    // ── DET-GUARDRAILS-KEEP-THE-DOCTRINE-001 · the two FPs that caused the redesign ──────────────
    //
    // The redesign of 2026-08-30 was written from these two field sessions, and until now they
    // existed only as prose in `REDESIGN-DETECTION-SYSTEM.md` and a 6 464-line `parkdiag.log` on a
    // cable. Piece 7 is the one that keeps the other six from being undone by the next hurried fix,
    // and a doctrine defended only by unit tests of the pieces it produced is defended against the
    // shapes we already thought of. These two replay the streams that produced the shapes.

    /**
     * The parafarmacia FP, 1:1. Two things had to be true at once for it to happen, and the replay
     * keeps both: an `enter_at_car` arm that the old weak-evidence list did not contain, and a
     * single Doppler sample standing in for a drive. `ArmEvidence.confirmsSilentlyWithoutMeasuredDrive`
     * closed the first and `DrivingEvidence` the second — this is the stream that proves neither can
     * be reopened by accident.
     *
     * Both were verified by NEUTRALIZATION against this fixture, one at a time, and each brings the
     * field pin back **on its own**:
     *  - `ENTER_AT_CAR` moved back onto the silent-confirm side of
     *    `ArmLabel.confirmsSilentlyWithoutMeasuredDrive` → 1 save;
     *  - the three `drivingEvidence` bars neutralized, arm gate untouched → 1 save.
     *
     * That is the KDoc table on `drivingEvidence` — *"each one killed the false positive on its
     * own"* — demonstrated on the stream instead of stated about it.
     */
    @Test
    fun parafarmacia_2908_one_doppler_sample_must_not_replace_a_good_pin() =
        runTest(UnconfinedTestDispatcher()) {
            val replayer = DetectionTraceReplayer(TRACE_PARAFARMACIA_2908)
            val env = buildEnv(clock = { replayer.nowMs }, config = ParkingDetectionConfig())
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 256)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.BoardingAtCar,
                    armingGeofenceId = "092c74d7",
                    departureAnchor = GpsPoint(
                        PARAFARMACIA_2908_REAL_CAR_LAT,
                        PARAFARMACIA_2908_REAL_CAR_LON,
                        accuracy = 7.1f,
                        timestamp = TRACE_PARAFARMACIA_2908.first().tMs - 88 * 60_000L,
                        speed = 0f,
                    ),
                    departureFenceRadiusMeters = 89f,
                )
            }
            // ⚠ No `onVehicleRide` here, and that is faithful rather than an omission. The receiver
            // stamped [PARAFARMACIA_2908_BOARDING_TRUE_TIME_MS] at 23:47:41.655, and `invoke()`
            // called `reset()` 2.7 s later — `reset()` assigns a whole fresh `DetectionSessionState`,
            // so the stamp was gone before the first fix. What locked the vehicle at 23:47:52 was
            // the speed path, on the lone Doppler sample. Injecting the ride would give this replay
            // an egress witness the field session never had.

            replayer.replay(
                emitFix = { locations.emit(it) },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            assertEquals(
                0,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "the car never moved: 71.6 m out and 64.8 m of it undone 3.5 s later, on ONE fix. " +
                    "Nothing here may plant a pin — the field build planted one at reliability 0.9 " +
                    "and deleted the geofence of the good pin it replaced",
            )
            // What the session is allowed to do instead is ASK, and it does — once, from the
            // scoring lane, exactly as the field build did at 23:53:48. The doctrine's asymmetry
            // is the whole point: the pharmacy trip costs one question at midnight, and the
            // question left unanswered leaves NO pin. The field build asked the same question and
            // then pinned anyway 2 min 40 s later, on the fast path this trace no longer opens.
            assertEquals(
                1,
                env.notification.parkingConfirmationCallCount,
                "under doubt the app asks — it does not decide",
            )
            assertEquals(
                listOf("PROMPT_SHOWN"),
                env.detectionLogger.events.filterIsInstance<DetectionEvent.Decision>().map { it.outcome },
                "and it is the scoring lane's question, never a confirm that got downgraded: " +
                    "there is no confirm here to downgrade",
            )
            assertEquals(0, env.notification.markParkingNudgeCallCount, "no nudge either")
            // The session still runs to the end of its stream rather than aborting early, and that
            // is the honest shape: `hasEverReachedDrivingSpeed` DID flip on the lone sample, which
            // is what disarms the false-ENTER guard. What stops the pin is one step further in —
            // the confirm needs measured driving, and the lone sample is not that.
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("ended", ended.outcome, "no confirm, no prompt — the session just runs out")
        }

    /**
     * The gap-anchor FP, 1:1 plus the quiet tail the timeout needs (see
     * [TRACE_CASA_GAP_ANCHOR_3008_QUIET_TAIL]).
     *
     * This one is not about refusing to save — the drive was real and the park was real, and losing
     * it would be its own failure. It is about WHERE a save is allowed to point when the app admits
     * it never saw the car stop. The field build kept the right shape (an area, reliability 0.5) and
     * centred it on the one fix in the window the evidence had already ruled out.
     *
     * Neutralization, and the layering it exposed — **two guards move the centre, by different
     * amounts, and neither is redundant**:
     *  - `DET-STOP-MUST-BE-STILL-IN-SPACE-001` off (the 120 m jump 1.5 s after the gap fix stops
     *    refuting the stop) but `bestWitnessedCenter` live → still **passes**: the refinement alone
     *    catches it;
     *  - `bestWitnessedCenter` off (`center = anchor`) but the stillness guard live → **red at 36 m**
     *    from the rest: the guard alone gets the centre off the gap fix, and not all the way home;
     *  - both off → the field FP verbatim, **158 m out at 36.6098405,-6.2784644**, which is why the
     *    ≥100 m half of this test has a witness and is not a claim that cannot fail.
     *
     * And the other two assertions, each seen red on its own: `doubtMeters` divided by 8 → radius
     * 61.9 m instead of 250; the drive-resumed retraction suppressed → the errand-stop question
     * never withdrawn, so the sequence loses its middle row.
     */
    @Test
    fun casa_gap_anchor_3008_the_zone_must_centre_on_the_rest_it_witnessed() =
        runTest(UnconfinedTestDispatcher()) {
            val replayer = DetectionTraceReplayer(TRACE_CASA_GAP_ANCHOR_3008 + TRACE_CASA_GAP_ANCHOR_3008_QUIET_TAIL)
            val env = buildEnv(clock = { replayer.nowMs }, config = ParkingDetectionConfig())
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 1200)
            val job = launch {
                env.coordinator.invoke(
                    locations,
                    armEvidence = ArmEvidence.Unverified,
                    armingGeofenceId = "c6a57fad",
                    departureAnchor = GpsPoint(
                        CASA_GAP_3008_DEPARTURE_ANCHOR_LAT,
                        CASA_GAP_3008_DEPARTURE_ANCHOR_LON,
                        accuracy = 22.3f,
                        timestamp = TRACE_CASA_GAP_ANCHOR_3008.first().tMs - 84 * 60_000L,
                        speed = 0f,
                    ),
                    departureFenceRadiusMeters = 89f,
                )
            }

            var boarded = false
            var secondLeg = false
            var firstExit = false
            var arrivalExit = false
            replayer.replay(
                emitFix = { fix ->
                    if (!boarded && fix.timestamp >= CASA_GAP_3008_BOARDING_TRUE_TIME_MS) {
                        boarded = true
                        env.coordinator.onVehicleRide(CASA_GAP_3008_BOARDING_TRUE_TIME_MS)
                    }
                    if (!firstExit && fix.timestamp >= CASA_GAP_3008_FIRST_EXIT_AT_MS) {
                        firstExit = true
                        env.coordinator.onVehicleExit(CASA_GAP_3008_FIRST_EXIT_AT_MS)
                    }
                    if (!secondLeg && fix.timestamp >= CASA_GAP_3008_SECOND_LEG_TRUE_TIME_MS) {
                        secondLeg = true
                        env.coordinator.onVehicleRide(CASA_GAP_3008_SECOND_LEG_TRUE_TIME_MS)
                    }
                    if (!arrivalExit && fix.timestamp >= CASA_GAP_3008_ARRIVAL_EXIT_AT_MS) {
                        arrivalExit = true
                        env.coordinator.onVehicleExit(CASA_GAP_3008_ARRIVAL_EXIT_AT_MS)
                    }
                    locations.emit(fix)
                },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            assertTrue(
                boarded && firstExit && secondLeg && arrivalExit,
                "the trace must reach both boardings and both vehicle exits",
            )
            val decisions = env.detectionLogger.events.filterIsInstance<DetectionEvent.Decision>()
            // The two-leg trip, reproduced: the errand stop asks, the resumed drive RETRACTS that
            // question, and the arrival asks again naming the hole. A build that stopped retracting
            // would leave the first question standing and time it out at the errand stop.
            assertEquals(
                listOf(
                    "CONFIRM_DEGRADED_PROMPT/weak_evidence",
                    "PROMPT_RETRACTED/drive_resumed",
                    "CONFIRM_DEGRADED_PROMPT/anchor_gap_entered",
                ),
                decisions.map { "${it.outcome}/${it.reason}" }
                    .filter { it.startsWith("CONFIRM_DEGRADED_PROMPT") || it.startsWith("PROMPT_RETRACTED") },
                "the field's own sequence of questions",
            )

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "the drive and the park are both real — losing them is not the fix for pointing wrong",
            )
            val saved = assertNotNull(env.parkingRepo.getActiveSession())
            assertTrue(
                saved.isApproximate,
                "a stop entered through a 198 s hole was never witnessed: area only, never an exact pin",
            )
            val fromRest = haversineMeters(
                saved.location.latitude, saved.location.longitude,
                CASA_GAP_3008_WITNESSED_REST_LAT, CASA_GAP_3008_WITNESSED_REST_LON,
            )
            val fromFieldCentre = haversineMeters(
                saved.location.latitude, saved.location.longitude,
                CASA_GAP_3008_FIELD_ZONE_CENTRE_LAT, CASA_GAP_3008_FIELD_ZONE_CENTRE_LON,
            )
            assertTrue(
                fromRest <= 30.0,
                "the zone must centre on the rest the session WITNESSED (18 fixes, ≤12 m accuracy, " +
                    "6.7 m of spread) — was ${fromRest.toInt()} m from it, at " +
                    "${saved.location.latitude},${saved.location.longitude}",
            )
            assertTrue(
                fromFieldCentre >= 100.0,
                "and never again on the gap anchor: of the 216 fixes after the hole, only the " +
                    "anchor itself came within 100 m of it — was ${fromFieldCentre.toInt()} m away",
            )
            // [DET-NO-CLOCK-PLANTS-A-PIN-001] The centre got better; the doubt did not get smaller.
            // The hole is what sizes the radius, and the hole is unchanged.
            assertEquals(
                250f,
                saved.zoneRadiusMeters,
                "a better centre does not shrink the doubt — the field radius was 250 m and the " +
                    "198 s hole that sized it is still there",
            )
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("confirmed_unattended_zone_gap_anchor", ended.outcome)
            assertEquals(0, env.notification.markParkingNudgeCallCount, "the saved zone is the ask — no extra nudge")
        }

    /**
     * The circle both doors draw over `TraceCameliasOppo001`'s walk-entered anchor. It is the 60 m
     * FLOOR rather than the measured walk-in bound (29,5 m, which is under it), so the two tests
     * below name it once instead of each repeating a number whose provenance is easy to misread.
     */
    private val UNANSWERED_ZONE_RADIUS_METERS = 60f

    // ── The two doors out of one session, and they must agree ────────────────────────────────────
    //
    // `TraceCameliasOppo001` carried REAL_CAR and FIELD_PIN from the day it was recorded and no test
    // read either, because the replay above asserts "no silent pin, one question" and on that run
    // there is no pin to locate [TEST-A-TRACE-WHOSE-GROUND-TRUTH-IS-NEVER-ASSERTED-001]. What the
    // unread ground truth was hiding is not a coordinate but a SHAPE: the same stream and the same
    // walk-entered anchor left the session by two doors, and the doors disagreed about how much the
    // app admits it does not know. The unanswered timeout drew an area; a user's "Sí" pinned exactly,
    // 37 m from the car, on the very coordinate the field build got wrong.
    //
    // [DET-A-USER-YES-DOES-NOT-SHRINK-A-WALK-ENTERED-DOUBT-001] closed that, and the pair below is
    // how it stays closed: the two tests assert the SAME radius from the same anchor, so a change
    // that teaches one door something the other does not know goes red.

    @Test
    fun camelias_oppo_001_an_unanswered_prompt_draws_the_walk_in_doubt_as_a_zone() =
        runTest(UnconfinedTestDispatcher()) {
            val replayer = DetectionTraceReplayer(
                TraceCameliasOppo001.events + TraceCameliasOppo001.quietTail,
            )
            val env = buildEnv(clock = { replayer.nowMs }, config = ParkingDetectionConfig())
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 700)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified) }

            replayer.replay(
                emitFix = { locations.emit(it) },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            assertEquals(
                1,
                env.parkingRepo.saveNewParkingSessionCallCount,
                "a bounded doubt costs precision, never the park — with the walk-in offset gone " +
                    "this verdict falls back to asking and the parking is lost",
            )
            val saved = assertNotNull(env.parkingRepo.getActiveSession())
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("confirmed_unattended_zone_walk_entered_anchor", ended.outcome)
            val fromCar = haversineMeters(
                saved.location.latitude, saved.location.longitude,
                TraceCameliasOppo001.REAL_CAR_LAT, TraceCameliasOppo001.REAL_CAR_LON,
            )
            // The centre is still the pedestrian's spot — the true one was never GPS-measured, so
            // there is nowhere better to put it. What makes the save honest is that an AREA is
            // drawn at all. ⚠ The radius is the 60 m FLOOR, not the measured walk-in offset, which
            // on this trace is smaller (the walk back ran on a mute counter, so the bound comes
            // from the GPS span of the walk-band run). The offset's job here is to LICENSE the
            // area, not to size it — and the floor happens to cover the 37 m of real error.
            val radius = assertNotNull(saved.zoneRadiusMeters, "the walk-in doubt must be drawn")
            assertTrue(
                radius >= fromCar,
                "the walk-in doubt must be drawn wide enough to hold the car: r=$radius " +
                    "against ${fromCar.toInt()} m of real error",
            )
            assertEquals(
                UNANSWERED_ZONE_RADIUS_METERS,
                radius,
                "the circle the twin test below compares itself against",
            )
        }

    /**
     * [DET-A-USER-YES-DOES-NOT-SHRINK-A-WALK-ENTERED-DOUBT-001] **The tap changes the reliability, not
     * the geometry.**
     *
     * Identical stream, identical anchor, and the only change from the test above is that the user
     * taps "Sí" instead of ignoring the question. That answer proves the park — and proves nothing
     * about the place, which is what this file's own header has always said. Until this ticket it
     * turned the 60 m zone into an **exact pin at the same coordinate**, 37 m from the car and within
     * a metre of [TraceCameliasOppo001.FIELD_PIN_LAT]/[TraceCameliasOppo001.FIELD_PIN_LON] — the very
     * point the field build got wrong.
     *
     * ⚠️ **The number that decided the fix.** The measured walk-in bound on this trace is **29,5 m**,
     * comfortably UNDER the 60 m floor — so simply feeding it to the old `> floor` gate would have
     * changed nothing here. What makes the pin unsupportable is not the bound's size but what it is:
     * a LOWER bound (the walk was only partly seen) on how wrong the PLACE is. The real error is
     * 37 m, larger than the bound that was supposed to reassure us.
     */
    @Test
    fun camelias_oppo_001_a_user_yes_keeps_that_same_doubt_as_a_zone() =
        runTest(UnconfinedTestDispatcher()) {
            val replayer = DetectionTraceReplayer(TraceCameliasOppo001.events)
            val env = buildEnv(clock = { replayer.nowMs }, config = ParkingDetectionConfig())
            val locations = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 700)
            val job = launch { env.coordinator.invoke(locations, armEvidence = ArmEvidence.Unverified) }

            var answered = false
            replayer.replay(
                emitFix = { fix ->
                    // The tap lands on the first fix after the question, which is the best case for
                    // the app: the user is still standing where the prompt found them.
                    if (!answered && env.notification.parkingConfirmationCallCount > 0) {
                        answered = true
                        env.coordinator.onUserConfirmedParking()
                    }
                    locations.emit(fix)
                },
                emitStep = { env.stepDetector.emitSteps(1) },
            )
            job.cancelAndJoin()

            assertTrue(answered, "the trace must reach the prompt and answer it")
            val saved = assertNotNull(env.parkingRepo.getActiveSession())
            val ended = env.detectionLogger.events
                .filterIsInstance<DetectionEvent.SessionEnded>().single()
            assertEquals("confirmed_user", ended.outcome)

            val fromCar = haversineMeters(
                saved.location.latitude, saved.location.longitude,
                TraceCameliasOppo001.REAL_CAR_LAT, TraceCameliasOppo001.REAL_CAR_LON,
            )
            val fromFieldPin = haversineMeters(
                saved.location.latitude, saved.location.longitude,
                TraceCameliasOppo001.FIELD_PIN_LAT, TraceCameliasOppo001.FIELD_PIN_LON,
            )
            // The CENTRE is unchanged and that is deliberate: the cascade keeps a walk-entered anchor
            // rather than demoting to whatever fix the user answered from, because a door 40 m away
            // is a worse guess than the stop the session measured [DET-CONFIRM-ANCHOR-001]. What the
            // ticket changed is the claim made about that centre, not the centre.
            assertTrue(
                fromFieldPin < 1.0,
                "the tap still centres where the session's anchor is — was ${fromFieldPin.toInt()} m " +
                    "from the field pin",
            )
            assertTrue(
                fromCar in 30.0..45.0,
                "and that is ~37 m from where the car actually was — was ${fromCar.toInt()} m",
            )
            val radius = assertNotNull(
                saved.zoneRadiusMeters,
                "a tap over a walk-entered anchor may not claim an exact point: the answer proves " +
                    "the park, and the anchor is still the pedestrian's spot",
            )
            assertTrue(
                radius >= fromCar,
                "and the area must hold the car: r=$radius against ${fromCar.toInt()} m of real error",
            )
            // The invariant that keeps the two doors from drifting apart again: same session, same
            // anchor, same doubt — so the same circle, whoever ends the session.
            assertEquals(
                UNANSWERED_ZONE_RADIUS_METERS,
                radius,
                "the tap and the timeout must draw the SAME circle over the same anchor",
            )
        }

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
                resolveAskedStreet = noStreet,
                notificationPort = notification,
                vehicleRepository = vehicleRepo,
            ),
            resolveAskedStreet = noStreet,
            notificationPort = notification,
            vehicleRepository = vehicleRepo,
            stepDetector = stepDetector,
            config = config,
            detectionEventLogger = detectionLogger,
            evaluateParkingDecision = EvaluateParkingDecisionUseCase(config),
            // [DET-DI-DETECTION-MODULE-001] Was the coordinator's own constructor default; the
            // instance is identical, it is just built where it can be seen.
            evaluateUnattendedParkingSave = EvaluateUnattendedParkingSaveUseCase(config),
            // [DET-COORDINATOR-NO-OPTIONAL-DEPS-001] Recording fakes — the replays drive the
            // detection loop from a trace; none of them asserts on these lanes.
            phaseSink = FakeDetectionPhaseSink(),
            finalizeDeducedDeparture = FakeFinalizeDeducedDeparture(),
            retractDeducedDeparture = FakeRetractDeducedDeparture(),
            clock = clock,
        )
        return Env(coordinator, parkingRepo, notification, stepDetector, detectionLogger)
    }
}

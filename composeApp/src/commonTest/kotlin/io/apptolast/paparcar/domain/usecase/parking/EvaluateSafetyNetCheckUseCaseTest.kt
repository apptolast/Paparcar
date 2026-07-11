package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * [DET-SAFETY-NET-001] Pure decision core of the parked-session safety net. The net only acts on a
 * departure IN PROGRESS (credible driving speed far from the car): inside fence → cure · far +
 * driving + anchor → dispatch · far + driving + no anchor → prompt · **far + stationary → None**
 * (parked-and-away on foot must never nag — field incident 2026-07-05). The anchor keeps auto at the
 * same bus/taxi risk envelope as the geofence EXIT.
 */
class EvaluateSafetyNetCheckUseCaseTest {

    private val config = ParkingDetectionConfig()
    private val useCase = EvaluateSafetyNetCheckUseCase(config)

    private val nowMs = 1_700_000_000_000L
    private val freshAnchor = nowMs - 5 * 60_000L // seen at the car 5 min ago
    private val drivingMps = 8f // 28.8 km/h ≥ minimumDepartureSpeedKmh (10)

    /** Parked 2 h ago: evidence from the last hour is admissible; anything older than the
     *  session itself is not ([DET-SESSION-BIRTH-001]). */
    private val sessionStartMs = nowMs - 2 * 60 * 60_000L

    private fun session(
        geofenceId: String? = "geof-1",
        accuracy: Float = 10f,
        sizeCategory: VehicleSize? = null,
    ) = UserParking(
        id = "session-1",
        location = GpsPoint(BASE_LAT, BASE_LON, accuracy = accuracy, timestamp = sessionStartMs, speed = 0f),
        geofenceId = geofenceId,
        sizeCategory = sizeCategory,
    )

    /** A fix [meters] north of the parked car. */
    private fun fixAtMeters(
        meters: Double,
        speedMps: Float = 0f,
        accuracy: Float = 10f,
        atMs: Long = nowMs,
    ) = GpsPoint(
        latitude = BASE_LAT + meters / METERS_PER_DEGREE_LAT,
        longitude = BASE_LON,
        accuracy = accuracy,
        timestamp = atMs,
        speed = speedMps,
    )

    private fun evaluate(
        session: UserParking = session(),
        fix: GpsPoint,
        lastSeenNearCarAtMs: Long? = null,
        stepsSinceAnchor: Long? = null,
        lastVehicleEnteredAtMs: Long? = null,
        exitDeliveredAtMs: Long? = null,
        userPresent: Boolean = false,
    ) = useCase(session, fix, lastSeenNearCarAtMs, nowMs, stepsSinceAnchor, lastVehicleEnteredAtMs, exitDeliveredAtMs, userPresent)

    @Test
    fun should_returnNone_when_sessionHasNoGeofence() {
        assertEquals(SafetyNetAction.None, evaluate(session(geofenceId = null), fixAtMeters(1_000.0, drivingMps)))
    }

    @Test
    fun should_cureGeofence_when_fixIsInsideTheFenceRadius() {
        // Radius for null size + accuracy 10 = 80 + 10*1.5 = 95 m.
        val action = evaluate(fix = fixAtMeters(50.0))
        val cure = assertIs<SafetyNetAction.CureGeofence>(action)
        assertEquals("geof-1", cure.geofenceId)
        assertEquals(config.geofenceRadiusFor(null, 10f), cure.radiusMeters)
    }

    @Test
    fun should_cureGeofence_withSizeAwareRadius_when_vehicleIsVan() {
        // VAN radius = 120 + 10*1.5 = 135 m: a 130 m fix is inside for the van…
        assertIs<SafetyNetAction.CureGeofence>(evaluate(session(sizeCategory = VehicleSize.VAN_HIGH), fixAtMeters(130.0)))
        // …but outside the default-size fence (95 m) — the ambiguous ring.
        assertEquals(SafetyNetAction.None, evaluate(fix = fixAtMeters(130.0, drivingMps)))
    }

    @Test
    fun should_returnNone_when_fixIsInTheAmbiguousRing() {
        // Between the fence radius (95 m) and watchdogFarThresholdMeters (300 m).
        assertEquals(SafetyNetAction.None, evaluate(fix = fixAtMeters(200.0, drivingMps)))
    }

    // ── The core fix: far + stationary must be SILENT ────────────────────────────

    @Test
    fun should_returnNone_when_farButStationary_walkedAwayToDinner() {
        // Parked, walked 2 km to dinner over 28 min: the step budget matches the displacement
        // (2 km ≈ 2 667 steps at 0.75 m/stride) → parked-and-away on foot, silent. [DET-RECONCILE-001]
        val anchor = nowMs - 28 * 60_000L
        val action = evaluate(
            fix = fixAtMeters(2_000.0, speedMps = 0f),
            lastSeenNearCarAtMs = anchor,
            stepsSinceAnchor = 2_600L,
        )
        assertEquals(SafetyNetAction.None, action)
    }

    @Test
    fun should_returnNone_when_farStationary_withStaleAnchor() {
        // The classic dinner: hours away on foot — the anchor expired long ago. Never nag.
        val staleAnchor = nowMs - config.vehicleEnterWindowMs - 60_000L
        val action = evaluate(fix = fixAtMeters(5_000.0, speedMps = 0f), lastSeenNearCarAtMs = staleAnchor)
        assertEquals(SafetyNetAction.None, action)
    }

    // ── [DET-RECONCILE-001] Step budget: the trip already happened while we slept ─

    @Test
    fun should_dispatchPreconfirmed_when_displacementWithoutTheStepsToWalkIt() {
        // Field trace 2026-07-06 (Oppo): EXIT delivered post-trip, user parked 986 m away with
        // ~10 steps on the counter, anchor 13 min old. Walking 986 m needs ~1 300 steps — the
        // user was DRIVEN from their own car. Must release without asking.
        val action = evaluate(
            fix = fixAtMeters(986.0, speedMps = 0f),
            lastSeenNearCarAtMs = nowMs - 13 * 60_000L,
            stepsSinceAnchor = 10L,
        )
        val dispatch = assertIs<SafetyNetAction.DispatchDeparture>(action)
        assertEquals("geof-1", dispatch.geofenceId)
        assertEquals(true, dispatch.preconfirmed)
    }

    @Test
    fun should_dispatchPreconfirmed_when_anchorOldInTimeButFreshInSteps() {
        // Field trace 2026-07-07 22:41 (Oppo): anchor sealed 46 min earlier, only 113 steps
        // walked since, wake-up fix 4 344 m away. The wall clock had expired the anchor while the
        // user sat right next to the car — but 113 steps ≤ a boarding's worth means they never
        // WALKED anywhere: the displacement was a drive that started at the car. Must release.
        val action = evaluate(
            fix = fixAtMeters(4_344.0, speedMps = 0f),
            lastSeenNearCarAtMs = nowMs - 46 * 60_000L,
            stepsSinceAnchor = 113L,
        )
        val dispatch = assertIs<SafetyNetAction.DispatchDeparture>(action)
        assertEquals(true, dispatch.preconfirmed)
    }

    @Test
    fun should_dispatchLive_when_drivingFarAndAnchorFreshOnlyBySteps() {
        // Same step-fresh anchor, caught MID-drive: live dispatch (worker re-verifies by speed).
        val action = evaluate(
            fix = fixAtMeters(2_000.0, speedMps = drivingMps),
            lastSeenNearCarAtMs = nowMs - 3 * 60 * 60_000L,
            stepsSinceAnchor = 40L,
        )
        val dispatch = assertIs<SafetyNetAction.DispatchDeparture>(action)
        assertEquals(false, dispatch.preconfirmed)
    }

    @Test
    fun should_promptStillParked_when_drivingButAnchorStaleInTimeAndSteps() {
        // Walked 800+ steps since last at the car, hours ago, now moving at driving speed — a
        // vehicle boarded away from the car. Only the user can disambiguate.
        assertIs<SafetyNetAction.PromptStillParked>(
            evaluate(
                fix = fixAtMeters(2_000.0, speedMps = drivingMps),
                lastSeenNearCarAtMs = nowMs - 3 * 60 * 60_000L,
                stepsSinceAnchor = 800L,
            ),
        )
    }

    @Test
    fun should_returnNone_when_stepBudgetMatchesWalking_shortRange() {
        // 500 m with ~600 steps: walked. Silent.
        val action = evaluate(
            fix = fixAtMeters(500.0, speedMps = 0f),
            lastSeenNearCarAtMs = freshAnchor,
            stepsSinceAnchor = 600L,
        )
        assertEquals(SafetyNetAction.None, action)
    }

    @Test
    fun should_returnNone_when_walkedToABusStopThenRode_busGuard() {
        // Walked ~400 m to a bus stop (~530 steps) then rode 5 km. The RELATIVE check alone
        // passes (530 ≪ 6 667×0.4) — the ABSOLUTE boarding cap must veto: that many steps means
        // the vehicle was boarded AWAY from the car. Silent. [DET-RECONCILE-001]
        val action = evaluate(
            fix = fixAtMeters(5_000.0, speedMps = 0f),
            lastSeenNearCarAtMs = freshAnchor,
            stepsSinceAnchor = 530L,
        )
        assertEquals(SafetyNetAction.None, action)
    }

    @Test
    fun should_returnNone_when_stepBudgetVerdict_butAnchorMissing() {
        // Few steps + far, but never seen at the car — could be a bus boarded elsewhere. Silent.
        val action = evaluate(
            fix = fixAtMeters(986.0, speedMps = 0f),
            lastSeenNearCarAtMs = null,
            stepsSinceAnchor = 10L,
        )
        assertEquals(SafetyNetAction.None, action)
    }

    @Test
    fun should_returnNone_when_stepBudgetVerdict_butFixAccuracyDegraded() {
        // The displacement itself is not trustworthy on a 120 m fix — no verdict.
        val action = evaluate(
            fix = fixAtMeters(986.0, speedMps = 0f, accuracy = 120f),
            lastSeenNearCarAtMs = freshAnchor,
            stepsSinceAnchor = 10L,
        )
        assertEquals(SafetyNetAction.None, action)
    }

    @Test
    fun should_dispatchPreconfirmed_when_noCounterButPedestrianPhysicsImpossible() {
        // No step counter (mute hardware — Redmi 2026-07-06): 5 km from the car 5 min after
        // being AT it = 16.7 m/s sustained. No pedestrian does that. Release.
        val action = evaluate(
            fix = fixAtMeters(5_000.0, speedMps = 0f),
            lastSeenNearCarAtMs = nowMs - 5 * 60_000L,
            stepsSinceAnchor = null,
        )
        assertEquals(true, assertIs<SafetyNetAction.DispatchDeparture>(action).preconfirmed)
    }

    @Test
    fun should_returnNone_when_noCounterAndDisplacementWalkable() {
        // No counter and 500 m in 20 min (0.4 m/s) is a perfectly walkable stroll — silent.
        val action = evaluate(
            fix = fixAtMeters(500.0, speedMps = 0f),
            lastSeenNearCarAtMs = nowMs - 20 * 60_000L,
            stepsSinceAnchor = null,
        )
        assertEquals(SafetyNetAction.None, action)
    }

    @Test
    fun should_dateTripToAnchorSeal_when_stepBudgetProvesTheRide() {
        // The dispatcher needs the trip's start to gate spot publishing — for the step budget
        // the seal is the last provable at-the-car moment. [DET-EXIT-TRUST-001]
        val seal = nowMs - 13 * 60_000L
        val action = evaluate(
            fix = fixAtMeters(986.0, speedMps = 0f),
            lastSeenNearCarAtMs = seal,
            stepsSinceAnchor = 10L,
        )
        assertEquals(seal, assertIs<SafetyNetAction.DispatchDeparture>(action).tripStartedAtMs)
    }

    // ── [DET-RIDE-PROOF-001] Frozen counter: delta≈0 over a walkable displacement ─

    @Test
    fun should_returnNone_when_zeroStepsOverWalkableDisplacement_frozenCounter() {
        // Field 2026-07-09 12:39 (Redmi): cumulative counter FROZEN (307 all day while the
        // session detector counted 157 steps). Raw delta=0 over 354 m in 17 min read as "was
        // driven" and backfilled a phantom parking mid-walk. 354 m in 17 min is a stroll — a
        // silent counter over a walkable displacement is what a dead counter produces: the
        // counter loses its voice and no other proof exists here. Silent.
        val action = evaluate(
            fix = fixAtMeters(354.0, speedMps = 0f),
            lastSeenNearCarAtMs = nowMs - 17 * 60_000L,
            stepsSinceAnchor = 0L,
        )
        assertEquals(SafetyNetAction.None, action)
    }

    @Test
    fun should_dispatchPreconfirmed_when_zeroStepsButDisplacementBeyondPedestrianReach() {
        // The OTHER reading of delta=0 — sat in the car the whole trip: 5 km in 10 min is
        // physically impossible on foot, so the zero is believable and the budget verdict
        // stands, position bounded (trusted steps travel with the dispatch for the backfill).
        val action = evaluate(
            fix = fixAtMeters(5_000.0, speedMps = 0f),
            lastSeenNearCarAtMs = nowMs - 10 * 60_000L,
            stepsSinceAnchor = 0L,
        )
        val dispatch = assertIs<SafetyNetAction.DispatchDeparture>(action)
        assertEquals(true, dispatch.preconfirmed)
        assertEquals(0L, dispatch.trustedStepsSinceAnchor)
    }

    @Test
    fun should_dispatchViaArBoarding_when_frozenCounterButBoardingAfterSeal() {
        // Dead-counter device on a REAL short hop: the muted counter falls back to the
        // mute-counter proofs, and the post-seal AR boarding still catches the ride — with NO
        // trusted steps, so downstream must not backfill a guessed position.
        val seal = nowMs - 14 * 60_000L
        val boarding = nowMs - 2 * 60_000L
        val action = evaluate(
            fix = fixAtMeters(576.0, speedMps = 0f),
            lastSeenNearCarAtMs = seal,
            stepsSinceAnchor = 0L,
            lastVehicleEnteredAtMs = boarding,
        )
        val dispatch = assertIs<SafetyNetAction.DispatchDeparture>(action)
        assertEquals(true, dispatch.preconfirmed)
        assertEquals(boarding, dispatch.tripStartedAtMs)
        assertEquals(null, dispatch.trustedStepsSinceAnchor)
    }

    // ── [DET-EXIT-TRUST-001] AR boarding: the ride proof for mute-counter devices ─

    @Test
    fun should_dispatchPreconfirmed_when_muteCounterButArBoardingAfterSeal() {
        // Field trace 2026-07-07 12:14 (Redmi, mute counter): sealed at the car 12:00, AR
        // IN_VEHICLE ENTER 12:12, wake-up 576 m away 2 min later. Physics over the 14-min-old
        // seal reads 0.68 m/s (walkable) and misses the 2-min hop — the boarding stamped AFTER
        // the seal is what proves the ride started at the car. Release, dated to the boarding.
        val seal = nowMs - 14 * 60_000L
        val boarding = nowMs - 2 * 60_000L
        val action = evaluate(
            fix = fixAtMeters(576.0, speedMps = 0f),
            lastSeenNearCarAtMs = seal,
            stepsSinceAnchor = null,
            lastVehicleEnteredAtMs = boarding,
        )
        val dispatch = assertIs<SafetyNetAction.DispatchDeparture>(action)
        assertEquals(true, dispatch.preconfirmed)
        assertEquals(boarding, dispatch.tripStartedAtMs)
    }

    @Test
    fun should_returnNone_when_arEnterPredatesTheAnchorSeal() {
        // ENTER from a PREVIOUS ride, then the user was seen back at the car (seal), then walked
        // 400 m: boarding must come AFTER the seal to tie the ride to the car. Silent.
        val boarding = nowMs - 20 * 60_000L
        val seal = nowMs - 5 * 60_000L
        val action = evaluate(
            fix = fixAtMeters(400.0, speedMps = 0f),
            lastSeenNearCarAtMs = seal,
            stepsSinceAnchor = null,
            lastVehicleEnteredAtMs = boarding,
        )
        assertEquals(SafetyNetAction.None, action)
    }

    @Test
    fun should_returnNone_when_arBoardingButAnchorMissing() {
        // A boarding with no anchor is any bus in town — never an auto release.
        val action = evaluate(
            fix = fixAtMeters(2_000.0, speedMps = 0f),
            lastSeenNearCarAtMs = null,
            stepsSinceAnchor = null,
            lastVehicleEnteredAtMs = nowMs - 3 * 60_000L,
        )
        assertEquals(SafetyNetAction.None, action)
    }

    // ── [DET-CONJUNCTION-001] EXIT delivery ∧ AR boarding: two OS events agreeing ─

    @Test
    fun should_dispatchConjunction_when_staleExitAndBoardingPairWithinWindow() {
        // Field replay 2026-07-08 21:42-21:45 (Redmi, cinema): EXIT delivered mid-drive at
        // d=1222 m (stale → recorded as evidence), AR IN_VEHICLE ENTER 72 s later, wake-up fix
        // acc=64 m, step counter mute, anchor expired by clock. No single sense could prove the
        // drive — the two OS events pairing within minutes can. Release, dated to the boarding.
        val exitDelivered = nowMs - 3 * 60_000L
        val boarding = nowMs - 2 * 60_000L
        val action = evaluate(
            fix = fixAtMeters(1_222.0, speedMps = 0f, accuracy = 64f),
            lastSeenNearCarAtMs = null,
            stepsSinceAnchor = null,
            lastVehicleEnteredAtMs = boarding,
            exitDeliveredAtMs = exitDelivered,
        )
        val dispatch = assertIs<SafetyNetAction.DispatchDeparture>(action)
        assertEquals(true, dispatch.preconfirmed)
        assertEquals(boarding, dispatch.tripStartedAtMs)
    }

    @Test
    fun should_prompt_when_conjunctionPairsButDisplacementIsWalkable() {
        // [DET-RIDE-PROOF-001] Both OS events fired and paired — but the position is only 350 m
        // out with the boarding 10 min old: perfectly walkable, i.e. a phantom ENTER next to a
        // late-delivered walking EXIT (the Redmi hairdresser cascade). Two nominations still
        // never RELEASE — but the fence's own broken record with a mute counter is exactly the
        // "far without means" case the contract says must ASK, not stay silent. [DET-AR-FIRST-001 F4]
        val action = evaluate(
            fix = fixAtMeters(350.0, speedMps = 0f),
            lastSeenNearCarAtMs = null,
            stepsSinceAnchor = null,
            lastVehicleEnteredAtMs = nowMs - 10 * 60_000L,
            exitDeliveredAtMs = nowMs - 8 * 60_000L,
        )
        assertEquals(SafetyNetAction.PromptStillParked("geof-1"), action)
    }

    @Test
    fun should_prompt_when_exitAndBoardingTooFarApart_busAfterWalk() {
        // The fence broke while walking out; a bus was boarded 18 min later. The pair does not
        // RELEASE (outside the pairing window) — but a broken-fence record + far + mute counter
        // is undecidable: ask the user instead of 4 silent hours (field 2026-07-10, Oppo).
        // Ignoring the prompt leaves the session untouched. [DET-AR-FIRST-001 F4]
        val action = evaluate(
            fix = fixAtMeters(2_000.0, speedMps = 0f),
            lastSeenNearCarAtMs = null,
            stepsSinceAnchor = null,
            lastVehicleEnteredAtMs = nowMs - 2 * 60_000L,
            exitDeliveredAtMs = nowMs - 20 * 60_000L,
        )
        assertEquals(SafetyNetAction.PromptStillParked("geof-1"), action)
    }

    @Test
    fun should_prompt_when_conjunctionBoardingPredatesTheSession() {
        // [DET-SESSION-BIRTH-001] The boarding belongs to the trip that CREATED this parking
        // (an OEM re-delivery — field 2026-07-08 18:52) — it can never RELEASE. The post-session
        // exit record still stands as contrary evidence with no means to verify → ask.
        // [DET-AR-FIRST-001 F4]
        val action = evaluate(
            fix = fixAtMeters(2_000.0, speedMps = 0f),
            lastSeenNearCarAtMs = null,
            stepsSinceAnchor = null,
            lastVehicleEnteredAtMs = sessionStartMs - 60_000L,
            exitDeliveredAtMs = sessionStartMs + 2 * 60_000L,
        )
        assertEquals(SafetyNetAction.PromptStillParked("geof-1"), action)
    }

    @Test
    fun should_returnNone_when_conjunctionExitPredatesTheSession() {
        // [DET-SESSION-BIRTH-001] An EXIT recorded for a previous life of this fence (poisoned
        // state, re-registrations) cannot pair against the current session.
        val action = evaluate(
            fix = fixAtMeters(2_000.0, speedMps = 0f),
            lastSeenNearCarAtMs = null,
            stepsSinceAnchor = null,
            lastVehicleEnteredAtMs = sessionStartMs + 2 * 60_000L,
            exitDeliveredAtMs = sessionStartMs - 60_000L,
        )
        assertEquals(SafetyNetAction.None, action)
    }

    @Test
    fun should_returnNone_when_stepsSayWalked_evenWithPairedExitAndBoarding() {
        // A LIVE counter that matches walking is the ground truth — the conjunction never
        // overrides it (doctrine: steps outrank everything).
        val action = evaluate(
            fix = fixAtMeters(2_000.0, speedMps = 0f),
            lastSeenNearCarAtMs = nowMs - 28 * 60_000L,
            stepsSinceAnchor = 2_600L,
            lastVehicleEnteredAtMs = nowMs - 2 * 60_000L,
            exitDeliveredAtMs = nowMs - 3 * 60_000L,
        )
        assertEquals(SafetyNetAction.None, action)
    }

    @Test
    fun should_returnNone_when_stepsSayWalked_evenWithRecentArEnter() {
        // A LIVE counter that matches walking is the ground truth: the user walked to a stop and
        // boarded a bus. The recent ENTER must not override the step verdict. [bus-after-walk]
        val action = evaluate(
            fix = fixAtMeters(2_000.0, speedMps = 0f),
            lastSeenNearCarAtMs = nowMs - 28 * 60_000L,
            stepsSinceAnchor = 2_600L,
            lastVehicleEnteredAtMs = nowMs - 3 * 60_000L,
        )
        assertEquals(SafetyNetAction.None, action)
    }

    @Test
    fun should_returnNone_when_farAndWalkingPace() {
        // ~1.2 m/s (walking) is well below the driving threshold → silent.
        assertEquals(SafetyNetAction.None, evaluate(fix = fixAtMeters(500.0, speedMps = 1.2f)))
    }

    // ── Live departure (driving speed) ───────────────────────────────────────────

    @Test
    fun should_dispatchDeparture_when_farAtDrivingSpeedWithFreshAnchor() {
        val action = evaluate(fix = fixAtMeters(500.0, drivingMps), lastSeenNearCarAtMs = freshAnchor)
        assertEquals("geof-1", assertIs<SafetyNetAction.DispatchDeparture>(action).geofenceId)
    }

    @Test
    fun should_promptStillParked_when_drivingFarWithoutAnchor_busCase() {
        // In a vehicle far from the car but never seen at the fence recently — bus/taxi/expired anchor.
        val action = evaluate(fix = fixAtMeters(2_000.0, drivingMps), lastSeenNearCarAtMs = null)
        assertIs<SafetyNetAction.PromptStillParked>(action)
    }

    @Test
    fun should_promptStillParked_when_anchorIsStale() {
        val staleAnchor = nowMs - config.vehicleEnterWindowMs - 1
        assertIs<SafetyNetAction.PromptStillParked>(
            evaluate(fix = fixAtMeters(500.0, drivingMps), lastSeenNearCarAtMs = staleAnchor),
        )
    }

    @Test
    fun should_returnNone_when_farAtApparentDrivingSpeedButDegradedAccuracy() {
        // A degraded fix can fake driving speed while stationary/walking — not a credible departure.
        assertEquals(
            SafetyNetAction.None,
            evaluate(fix = fixAtMeters(500.0, speedMps = 8f, accuracy = 120f), lastSeenNearCarAtMs = freshAnchor),
        )
    }

    // ── [DET-ANCHOR-FREEZE-001] Ask-when-blind: the app is OPEN and no proof explains "far" ─

    @Test
    fun should_promptStillParked_when_userPresentFarAndEveryProofIsBlind() {
        // Field 2026-07-11 (Redmi): app opened at the destination, 1 912 m from an "active"
        // parking; anchor expired by clock, counter mute, no boarding, no recorded EXIT — three
        // silent ticks while the user stared at a stale pin. With the user IN the app the
        // question costs nothing: ask.
        val staleAnchor = nowMs - config.vehicleEnterWindowMs - 60_000L
        val action = evaluate(
            fix = fixAtMeters(1_912.0, speedMps = 0f, accuracy = 100f),
            lastSeenNearCarAtMs = staleAnchor,
            stepsSinceAnchor = null,
            userPresent = true,
        )
        assertIs<SafetyNetAction.PromptStillParked>(action)
    }

    @Test
    fun should_returnNone_when_userPresentButTrustedStepsExplainTheWalk() {
        // Same app-open moment on a HEALTHY device after walking 2 km to dinner: the trusted
        // step count explains the displacement on foot — the normal parked-and-away state must
        // stay silent even in-app (SAFETYNET-STATIONARY-001).
        val action = evaluate(
            fix = fixAtMeters(2_000.0, speedMps = 0f),
            lastSeenNearCarAtMs = nowMs - 28 * 60_000L,
            stepsSinceAnchor = 2_600L,
            userPresent = true,
        )
        assertEquals(SafetyNetAction.None, action)
    }

    @Test
    fun should_returnNone_when_blindButUserNotPresent() {
        // The SAME blind situation on a background tick must stay silent — a shade notification
        // every 15 min at the dinner table is the nag SAFETYNET-STATIONARY-001 forbids.
        val staleAnchor = nowMs - config.vehicleEnterWindowMs - 60_000L
        val action = evaluate(
            fix = fixAtMeters(1_912.0, speedMps = 0f, accuracy = 100f),
            lastSeenNearCarAtMs = staleAnchor,
            stepsSinceAnchor = null,
            userPresent = false,
        )
        assertEquals(SafetyNetAction.None, action)
    }

    private companion object {
        const val BASE_LAT = 36.6024
        const val BASE_LON = -6.2766
        const val METERS_PER_DEGREE_LAT = 111_320.0
    }
}

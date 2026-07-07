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

    private fun session(
        geofenceId: String? = "geof-1",
        accuracy: Float = 10f,
        sizeCategory: VehicleSize? = null,
    ) = UserParking(
        id = "session-1",
        location = GpsPoint(BASE_LAT, BASE_LON, accuracy = accuracy, timestamp = nowMs - 60_000L, speed = 0f),
        geofenceId = geofenceId,
        sizeCategory = sizeCategory,
    )

    /** A fix [meters] north of the parked car. */
    private fun fixAtMeters(
        meters: Double,
        speedMps: Float = 0f,
        accuracy: Float = 10f,
    ) = GpsPoint(
        latitude = BASE_LAT + meters / METERS_PER_DEGREE_LAT,
        longitude = BASE_LON,
        accuracy = accuracy,
        timestamp = nowMs,
        speed = speedMps,
    )

    private fun evaluate(
        session: UserParking = session(),
        fix: GpsPoint,
        lastSeenNearCarAtMs: Long? = null,
        stepsSinceAnchor: Long? = null,
    ) = useCase(session, fix, lastSeenNearCarAtMs, nowMs, stepsSinceAnchor)

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

    private companion object {
        const val BASE_LAT = 36.6024
        const val BASE_LON = -6.2766
        const val METERS_PER_DEGREE_LAT = 111_320.0
    }
}

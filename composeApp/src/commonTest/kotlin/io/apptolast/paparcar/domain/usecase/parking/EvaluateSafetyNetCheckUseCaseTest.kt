package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * [DET-SAFETY-NET-001] Pure decision core of the parked-session safety net:
 * inside fence → cure · far + evidence + position anchor → dispatch · anything weaker → prompt.
 * The anchor (seen inside the car's fence recently) is what keeps this at the same bus/taxi risk
 * envelope as the geofence EXIT — driving evidence far from the car NEVER releases on its own.
 */
class EvaluateSafetyNetCheckUseCaseTest {

    private val config = ParkingDetectionConfig()
    private val useCase = EvaluateSafetyNetCheckUseCase(config)

    private val nowMs = 1_700_000_000_000L
    private val freshAnchor = nowMs - 5 * 60_000L // seen at the car 5 min ago

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
        lastVehicleEnteredAt: Long? = null,
        lastSeenNearCarAtMs: Long? = null,
    ) = useCase(session, fix, lastVehicleEnteredAt, lastSeenNearCarAtMs, nowMs)

    @Test
    fun should_returnNone_when_sessionHasNoGeofence() {
        val action = evaluate(session(geofenceId = null), fixAtMeters(1_000.0))
        assertEquals(SafetyNetAction.None, action)
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
        val action = evaluate(session(sizeCategory = VehicleSize.VAN_HIGH), fixAtMeters(130.0))
        assertIs<SafetyNetAction.CureGeofence>(action)
        // …but outside the default-size fence (95 m) — the ambiguous ring.
        assertEquals(SafetyNetAction.None, evaluate(fix = fixAtMeters(130.0)))
    }

    @Test
    fun should_returnNone_when_fixIsInTheAmbiguousRing() {
        // Between the fence radius (95 m) and watchdogFarThresholdMeters (300 m).
        assertEquals(SafetyNetAction.None, evaluate(fix = fixAtMeters(200.0)))
    }

    @Test
    fun should_dispatchDeparture_when_farWithRecentVehicleEnterAndAnchor() {
        val action = evaluate(
            fix = fixAtMeters(500.0),
            lastVehicleEnteredAt = nowMs - 60_000L,
            lastSeenNearCarAtMs = freshAnchor,
        )
        val dispatch = assertIs<SafetyNetAction.DispatchDeparture>(action)
        assertEquals("geof-1", dispatch.geofenceId)
    }

    @Test
    fun should_dispatchDeparture_when_farAtCredibleDrivingSpeedAndAnchor() {
        // 8 m/s = 28.8 km/h ≥ minimumDepartureSpeedKmh (10), accuracy 10 ≤ minGpsAccuracyForDriving (50).
        val action = evaluate(
            fix = fixAtMeters(500.0, speedMps = 8f, accuracy = 10f),
            lastSeenNearCarAtMs = freshAnchor,
        )
        assertIs<SafetyNetAction.DispatchDeparture>(action)
    }

    @Test
    fun should_promptStillParked_when_drivingFarWithoutAnchor() {
        // The bus/friend's-car case: vehicle evidence far from the car, but the phone was never
        // seen inside the fence recently — movement did NOT start at the car. Never auto-release.
        val action = evaluate(
            fix = fixAtMeters(2_000.0, speedMps = 11f, accuracy = 10f),
            lastVehicleEnteredAt = nowMs - 60_000L,
            lastSeenNearCarAtMs = null,
        )
        assertIs<SafetyNetAction.PromptStillParked>(action)
    }

    @Test
    fun should_promptStillParked_when_anchorIsStale() {
        val staleAnchor = nowMs - config.vehicleEnterWindowMs - 1
        val action = evaluate(
            fix = fixAtMeters(500.0, speedMps = 8f, accuracy = 10f),
            lastSeenNearCarAtMs = staleAnchor,
        )
        assertIs<SafetyNetAction.PromptStillParked>(action)
    }

    @Test
    fun should_promptStillParked_when_farAtDrivingSpeedWithDegradedAccuracy() {
        // A degraded fix can fake driving speed while walking — same rule as the pre-arm verifier.
        val action = evaluate(
            fix = fixAtMeters(500.0, speedMps = 8f, accuracy = 120f),
            lastSeenNearCarAtMs = freshAnchor,
        )
        assertIs<SafetyNetAction.PromptStillParked>(action)
    }

    @Test
    fun should_promptStillParked_when_farWithoutAnyEvidence() {
        val action = evaluate(fix = fixAtMeters(500.0), lastSeenNearCarAtMs = freshAnchor)
        val prompt = assertIs<SafetyNetAction.PromptStillParked>(action)
        assertEquals("geof-1", prompt.geofenceId)
    }

    @Test
    fun should_promptStillParked_when_vehicleEnterIsOlderThanTheWindow() {
        val stale = nowMs - config.vehicleEnterWindowMs - 1
        val action = evaluate(
            fix = fixAtMeters(500.0),
            lastVehicleEnteredAt = stale,
            lastSeenNearCarAtMs = freshAnchor,
        )
        assertIs<SafetyNetAction.PromptStillParked>(action)
    }

    @Test
    fun should_promptStillParked_when_vehicleEnterIsStampedInTheFuture() {
        // Strict ordering, same as VerifyDepartureEvidenceUseCase: an ENTER after "now" is not evidence.
        val action = evaluate(
            fix = fixAtMeters(500.0),
            lastVehicleEnteredAt = nowMs + 5_000L,
            lastSeenNearCarAtMs = freshAnchor,
        )
        assertIs<SafetyNetAction.PromptStillParked>(action)
    }

    private companion object {
        const val BASE_LAT = 36.6024
        const val BASE_LON = -6.2766
        const val METERS_PER_DEGREE_LAT = 111_320.0
    }
}

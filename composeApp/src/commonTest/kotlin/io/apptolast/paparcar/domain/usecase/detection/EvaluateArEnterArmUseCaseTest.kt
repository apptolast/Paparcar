package io.apptolast.paparcar.domain.usecase.detection

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.model.UserParking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DET-AR-FIRST-001] The AR ENTER arm ladder: only a boarding tied to the user's OWN car may arm
 * (inside the fence, or conjunction with the fence's broken-EXIT record); a bus/taxi ENTER, a
 * stale re-delivery, or a blind ENTER (no fix) never does — the bus false positive that killed
 * the legacy AR-proximity arm must stay dead.
 */
class EvaluateArEnterArmUseCaseTest {

    private val config = ParkingDetectionConfig()
    private val useCase = EvaluateArEnterArmUseCase(config)

    private val nowMs = 1_700_000_000_000L
    private val sessionStartMs = nowMs - 2 * 60 * 60_000L
    private val freshEnterMs = nowMs - 60_000L // 1 min lag — well inside the pairing window

    private fun session(geofenceId: String? = "geof-1") = UserParking(
        id = "session-1",
        location = GpsPoint(BASE_LAT, BASE_LON, accuracy = 10f, timestamp = sessionStartMs, speed = 0f),
        geofenceId = geofenceId,
    )

    /** A fix [meters] north of the parked car. */
    private fun fixAtMeters(meters: Double, accuracy: Float = 10f) = GpsPoint(
        latitude = BASE_LAT + meters / METERS_PER_DEGREE_LAT,
        longitude = BASE_LON,
        accuracy = accuracy,
        timestamp = nowMs,
        speed = 0f,
    )

    @Test
    fun should_returnNoSession_when_noActiveParking() {
        val decision = useCase(
            session = null,
            fix = fixAtMeters(0.0),
            enterTrueTimeMs = freshEnterMs,
            nowMs = nowMs,
            recentStaleExitRecorded = false,
        )
        assertEquals(ArEnterDecision.NoSession, decision)
    }

    @Test
    fun should_returnStaleEnter_when_lagExceedsPairingWindow() {
        // An OEM re-delivery arriving minutes late lost its exemption moment and its data value.
        val decision = useCase(
            session = session(),
            fix = fixAtMeters(0.0),
            enterTrueTimeMs = nowMs - config.exitEnterPairWindowMs - 1_000L,
            nowMs = nowMs,
            recentStaleExitRecorded = true,
        )
        assertEquals(ArEnterDecision.StaleEnter, decision)
    }

    @Test
    fun should_returnStaleEnter_when_boardingPredatesTheSession() {
        // [DET-SESSION-BIRTH-001] A boarding older than the parking belongs to the trip that
        // created it — never evidence of leaving it, however fresh the delivery looks.
        val bornJustNow = session().copy(
            location = GpsPoint(BASE_LAT, BASE_LON, accuracy = 10f, timestamp = nowMs - 30_000L, speed = 0f),
        )
        val decision = useCase(
            session = bornJustNow,
            fix = fixAtMeters(0.0),
            enterTrueTimeMs = nowMs - 60_000L, // fresh lag, but before the session's birth
            nowMs = nowMs,
            recentStaleExitRecorded = true,
        )
        assertEquals(ArEnterDecision.StaleEnter, decision)
    }

    @Test
    fun should_returnNoFix_when_positionUnavailable() {
        val decision = useCase(
            session = session(),
            fix = null,
            enterTrueTimeMs = freshEnterMs,
            nowMs = nowMs,
            recentStaleExitRecorded = true,
        )
        assertEquals(ArEnterDecision.NoFix, decision)
    }

    @Test
    fun should_armAtCar_when_boardingInsideOwnFence() {
        // Default size (null) + accuracy 10 → radius = 80 + 10×1.5 = 95 m; fix at 40 m is inside.
        val decision = useCase(
            session = session(),
            fix = fixAtMeters(40.0),
            enterTrueTimeMs = freshEnterMs,
            nowMs = nowMs,
            recentStaleExitRecorded = false,
        )
        assertEquals(ArEnterDecision.ArmAtCar("geof-1"), decision)
    }

    @Test
    fun should_armMidTrip_when_farButOwnFenceReportedBroken() {
        // The exit∧enter conjunction: the OS itself says this fence broke recently, and a fresh
        // boarding agrees — two independent events describing one drive-away.
        val decision = useCase(
            session = session(),
            fix = fixAtMeters(900.0),
            enterTrueTimeMs = freshEnterMs,
            nowMs = nowMs,
            recentStaleExitRecorded = true,
        )
        assertEquals(ArEnterDecision.ArmMidTrip("geof-1"), decision)
    }

    @Test
    fun should_returnTickOnly_when_farWithoutBrokenFenceRecord() {
        // A vehicle boarded away from the car with no fence evidence: a bus. Never arm.
        val decision = useCase(
            session = session(),
            fix = fixAtMeters(900.0),
            enterTrueTimeMs = freshEnterMs,
            nowMs = nowMs,
            recentStaleExitRecorded = false,
        )
        assertEquals(ArEnterDecision.TickOnly, decision)
    }

    @Test
    fun should_returnNoSession_when_sessionHasNoGeofence() {
        val decision = useCase(
            session = session(geofenceId = null),
            fix = fixAtMeters(40.0),
            enterTrueTimeMs = freshEnterMs,
            nowMs = nowMs,
            recentStaleExitRecorded = false,
        )
        assertEquals(ArEnterDecision.NoSession, decision)
    }

    private companion object {
        const val BASE_LAT = 36.6
        const val BASE_LON = -6.23
        const val METERS_PER_DEGREE_LAT = 111_320.0
    }
}

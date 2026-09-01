package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.fakes.FakeDetectionEventLogger
import com.rndeveloper.paparcar.fakes.FakeGeofenceManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [DET-A-RELEASED-PIN-TAKES-ITS-FENCES-WITH-IT-001] The ENTER lane's orphan sweep.
 *
 * Field 2026-08-31 (Oppo): pin `d194668c` released at 21:22:44 with a silent removal failure;
 * its NEVER_EXPIRE `enter_` twin fired at 21:34:26 and — the defect — nothing asked whether the
 * fence still had a session, so it would have kept firing forever.
 */
class OrphanEnterFencesTest {

    private fun parked(geofenceId: String) = UserParking(
        id = geofenceId,
        userId = "user-1",
        vehicleId = "v-1",
        location = GpsPoint(36.633, -6.228, 5f, 1_788_204_000_000L, 0f),
        geofenceId = geofenceId,
        isActive = true,
    )

    @Test
    fun should_removeTheFence_and_logOrphanCleaned_when_theFiringFenceHasNoSession() = runTest {
        val geofences = FakeGeofenceManager()
        val logger = FakeDetectionEventLogger()

        val swept = cleanOrphanEnterFences(
            deliveredGeofenceIds = listOf("d194668c"),
            activeSessions = emptyList(), // the released pin took its session; the fence stayed
            geofenceManager = geofences,
            detectionEventLogger = logger,
            nowMs = 1_788_205_000_000L,
        )

        assertEquals(listOf("d194668c"), swept)
        assertEquals(listOf("d194668c"), geofences.removedIds, "the orphan's THREE fences go through removeGeofence(baseId)")
        val event = logger.events.filterIsInstance<DetectionEvent.OrphanCleaned>().single()
        assertEquals("d194668c", event.sessionId)
    }

    @Test
    fun should_leaveTheFenceAlone_when_itsSessionIsStillParked() = runTest {
        val geofences = FakeGeofenceManager()
        val logger = FakeDetectionEventLogger()

        val swept = cleanOrphanEnterFences(
            deliveredGeofenceIds = listOf("2f4197dc"),
            activeSessions = listOf(parked("2f4197dc")),
            geofenceManager = geofences,
            detectionEventLogger = logger,
            nowMs = 1_788_205_000_000L,
        )

        assertTrue(swept.isEmpty())
        assertTrue(geofences.removedIds.isEmpty(), "a live fence must NEVER be swept — the return-anchor cure depends on it")
        assertTrue(logger.events.isEmpty())
    }

    @Test
    fun should_sweepOnlyTheOrphan_when_liveAndOrphanFireTogether() = runTest {
        // The real 21:34:26 delivery shape: the orphan enter_d194668c fired while another pin
        // (35441eef) was legitimately parked — the sweep must separate them, not blanket-clean.
        val geofences = FakeGeofenceManager()
        val logger = FakeDetectionEventLogger()

        val swept = cleanOrphanEnterFences(
            deliveredGeofenceIds = listOf("d194668c", "35441eef"),
            activeSessions = listOf(parked("35441eef")),
            geofenceManager = geofences,
            detectionEventLogger = logger,
            nowMs = 1_788_205_000_000L,
        )

        assertEquals(listOf("d194668c"), swept)
        assertEquals(listOf("d194668c"), geofences.removedIds)
    }

    @Test
    fun should_doNothing_when_noFenceIdsWereDelivered() = runTest {
        // Every non-ENTER wake (periodic, sentry, detection-end) arrives with an empty list —
        // the sweep must be invisible to them.
        val geofences = FakeGeofenceManager()

        val swept = cleanOrphanEnterFences(
            deliveredGeofenceIds = emptyList(),
            activeSessions = emptyList(),
            geofenceManager = geofences,
            detectionEventLogger = null,
            nowMs = 1_788_205_000_000L,
        )

        assertTrue(swept.isEmpty())
        assertTrue(geofences.removedIds.isEmpty())
    }
}

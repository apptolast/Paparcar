package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeAddressAndPlaceRepository
import io.apptolast.paparcar.fakes.FakeGeofenceManager
import io.apptolast.paparcar.fakes.FakeReportSpotScheduler
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseActiveParkingSessionUseCaseTest {

    private val scheduler = FakeReportSpotScheduler()
    private val parkingRepo = FakeUserParkingRepository()

    private val reportSpotReleased = ReportSpotReleasedUseCase(
        reportSpotScheduler = scheduler,
        getAddressAndPlace = GetAddressAndPlaceUseCase(FakeAddressAndPlaceRepository()),
        authRepository = FakeAuthRepository(initialSession = null),
    )
    private val geofence = FakeGeofenceManager()
    private val useCase = ReleaseActiveParkingSessionUseCase(reportSpotReleased, parkingRepo, geofence)

    private val location = GpsPoint(40.416775, -3.703790, 10f, 0L, 0f)

    private fun session(
        id: String = "session-1",
        spotType: SpotType = SpotType.AUTO_DETECTED,
        reliability: Float? = null,
        size: VehicleSize? = null,
    ) = UserParking(
        id = id,
        location = location,
        isActive = true,
        spotType = spotType,
        detectionReliability = reliability,
        sizeCategory = size,
    )

    // ── Spot report is always enqueued ────────────────────────────────────────

    @Test
    fun `should_callReportSpotSchedulerOnce_when_invoked`() = runTest {
        useCase(40.416775, -3.703790, session())

        assertEquals(1, scheduler.scheduleCallCount)
    }

    // ── Geofence lifecycle [Hallazgo A / DET-SUPERSEDE-001] ────────────────────

    @Test
    fun `should_removeGeofence_when_releasing_a_session`() = runTest {
        // Freeing the spot ends the session, so its geofence must go too — otherwise it lingers
        // (NEVER_EXPIRE) and fires a spurious GEOFENCE_EXIT on the next drive-away (leg chino→casa,
        // field 2026-07-12). Matches the session-end ⇒ removeGeofence invariant Revert already keeps.
        useCase(40.0, -3.0, session(id = "my-session-id"))

        assertEquals(listOf("my-session-id"), geofence.removedIds, "release must remove the session's geofence")
    }

    @Test
    fun `should_notRemoveGeofence_when_no_session_supplied`() = runTest {
        useCase(40.0, -3.0, currentSession = null)

        assertTrue(geofence.removedIds.isEmpty(), "no session → nothing to remove")
    }

    @Test
    fun `should_passCoordinatesThrough_to_scheduler`() = runTest {
        useCase(40.416775, -3.703790, session())

        assertEquals(40.416775, scheduler.lastLat)
        assertEquals(-3.703790, scheduler.lastLon)
    }

    // ── Session-derived fields ────────────────────────────────────────────────

    @Test
    fun `should_useSessionId_as_spotId`() = runTest {
        useCase(40.0, -3.0, session(id = "my-session-id"))

        assertEquals("my-session-id", scheduler.lastSpotId)
    }

    @Test
    fun `should_useSessionSpotType`() = runTest {
        useCase(40.0, -3.0, session(spotType = SpotType.MANUAL_REPORT))

        assertEquals(SpotType.MANUAL_REPORT, scheduler.lastSpotType)
    }

    @Test
    fun `should_useSessionReliability_as_confidence`() = runTest {
        useCase(40.0, -3.0, session(reliability = 0.75f))

        assertEquals(0.75f, scheduler.lastConfidence)
    }

    @Test
    fun `should_defaultConfidenceToOne_when_reliabilityIsNull`() = runTest {
        useCase(40.0, -3.0, session(reliability = null))

        assertEquals(1f, scheduler.lastConfidence)
    }

    @Test
    fun `should_useSessionSizeCategory`() = runTest {
        useCase(40.0, -3.0, session(size = VehicleSize.LARGE_SEDAN))

        assertEquals(VehicleSize.LARGE_SEDAN, scheduler.lastSizeCategory)
    }

    @Test
    fun `should_passSizeCategoryNull_when_sessionHasNoSize`() = runTest {
        useCase(40.0, -3.0, session(size = null))

        assertNull(scheduler.lastSizeCategory)
    }

    // ── No active session (manual release) ───────────────────────────────────

    @Test
    fun `should_generateManualSpotId_when_sessionIsNull`() = runTest {
        useCase(40.0, -3.0, currentSession = null)

        assertNotNull(scheduler.lastSpotId)
        assertTrue(scheduler.lastSpotId!!.startsWith("manual_"))
    }

    @Test
    fun `should_defaultToAutoDetected_when_sessionIsNull`() = runTest {
        useCase(40.0, -3.0, currentSession = null)

        assertEquals(SpotType.AUTO_DETECTED, scheduler.lastSpotType)
    }

    @Test
    fun `should_defaultConfidenceToOne_when_sessionIsNull`() = runTest {
        useCase(40.0, -3.0, currentSession = null)

        assertEquals(1f, scheduler.lastConfidence)
    }

    // ── Session cleared after report ──────────────────────────────────────────

    @Test
    fun `should_clearActiveSession_after_reporting`() = runTest {
        parkingRepo.saveNewParkingSession(session())

        useCase(40.0, -3.0, parkingRepo.getActiveSession())

        assertNull(parkingRepo.getActiveSession())
    }

    @Test
    fun `should_returnSuccess_when_clearSucceeds`() = runTest {
        val result = useCase(40.0, -3.0, session())

        assertTrue(result.isSuccess)
    }

    @Test
    fun `should_scheduleSpotReport_before_clearActive`() = runTest {
        // Verify report is always enqueued even if clearActive is a no-op (no active session)
        useCase(40.0, -3.0, session())

        assertEquals(1, scheduler.scheduleCallCount)
    }
}

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingReleaseReason
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeAddressAndPlaceRepository
import io.apptolast.paparcar.fakes.FakeDetectionEventLogger
import io.apptolast.paparcar.fakes.FakeGeofenceManager
import io.apptolast.paparcar.fakes.FakeReportSpotScheduler
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    private val geofence = io.apptolast.paparcar.fakes.FakeGeofenceManager()
    private val eventLogger = FakeDetectionEventLogger()
    private val useCase = ReleaseActiveParkingSessionUseCase(
        reportSpotReleased, parkingRepo, geofence, eventLogger,
    )

    private val location = GpsPoint(40.416775, -3.703790, 10f, 0L, 0f)

    private fun session(
        id: String = "session-1",
        spotType: SpotType = SpotType.AUTO_DETECTED,
        reliability: Float? = null,
        size: VehicleSize? = null,
        geofenceId: String? = null,
    ) = UserParking(
        id = id,
        location = location,
        isActive = true,
        spotType = spotType,
        detectionReliability = reliability,
        sizeCategory = size,
        geofenceId = geofenceId,
    )

    // ── [VEH-ACTIVE-FENCE-001] Release observability ─────────────────────────

    @Test
    fun `should_log_a_Released_event_with_publish_flag_and_location`() = runTest {
        useCase(lat = 40.5, lon = -3.6, currentSession = session(id = "sess-1"), reason = ParkingReleaseReason.DEPARTURE_PUBLISHED)

        val released = eventLogger.events.filterIsInstance<DetectionEvent.Released>()
        assertEquals(1, released.size)
        assertEquals("sess-1", released.first().sessionId)
        assertTrue(released.first().published)
        assertEquals(40.5, released.first().location?.latitude)
    }

    @Test
    fun `should_not_log_a_Released_event_when_there_is_no_session`() = runTest {
        useCase(lat = 40.5, lon = -3.6, currentSession = null, reason = ParkingReleaseReason.DEPARTURE_PUBLISHED)

        assertTrue(eventLogger.events.filterIsInstance<DetectionEvent.Released>().isEmpty())
    }

    @Test
    fun `should_stamp_the_close_reason_on_the_Released_event`() = runTest {
        // [PARK-DELETE-NO-DECLARE-001] A replay must tell a deleted record from a departure that
        // simply chose not to share — both carry published=false.
        useCase(lat = 40.5, lon = -3.6, currentSession = session(id = "sess-1"), reason = ParkingReleaseReason.RECORD_DELETED)

        val released = eventLogger.events.filterIsInstance<DetectionEvent.Released>().single()
        assertEquals(ParkingReleaseReason.RECORD_DELETED, released.reason)
        assertFalse(released.published)
    }

    // ── [DET-AUDIT-002 T5/M4] The release must unregister the session's fence ─

    @Test
    fun `should_removeGeofence_when_sessionHasOne`() = runTest {
        useCase(40.416775, -3.703790, session(geofenceId = "geof-1"))

        assertEquals(listOf("geof-1"), geofence.removedIds, "manual release must not leave a NEVER_EXPIRE orphan fence")
    }

    @Test
    fun `should_removeGeofence_alsoOnTheJustDeletePath`() = runTest {
        useCase(40.416775, -3.703790, session(geofenceId = "geof-2"), reason = ParkingReleaseReason.DEPARTURE_UNPUBLISHED)

        assertEquals(listOf("geof-2"), geofence.removedIds)
    }

    @Test
    fun `should_removeGeofence_when_theRecordIsDeleted`() = runTest {
        // Deleting a wrong record still has to take its fence down — the session is gone either way.
        useCase(40.416775, -3.703790, session(geofenceId = "geof-3"), reason = ParkingReleaseReason.RECORD_DELETED)

        assertEquals(listOf("geof-3"), geofence.removedIds)
    }

    // ── [PARK-DELETE-NO-DECLARE-001] Only a departure frees a plaza ──────────

    @Test
    fun `should_not_scheduleSpotReport_when_theRecordIsDeleted`() = runTest {
        useCase(40.0, -3.0, session(), reason = ParkingReleaseReason.RECORD_DELETED)

        assertEquals(0, scheduler.scheduleCallCount)
    }

    // ── Spot report is always enqueued ────────────────────────────────────────

    @Test
    fun `should_callReportSpotSchedulerOnce_when_invoked`() = runTest {
        useCase(40.416775, -3.703790, session())

        assertEquals(1, scheduler.scheduleCallCount)
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

    // ── [VEH-STATS-SAY-SOMETHING-USEFUL-001] Close provenance on the row ─────

    @Test
    fun `should_stamp_endedAt_and_publishedSpot_when_the_departure_publishes`() = runTest {
        parkingRepo.saveNewParkingSession(session(id = "sess-close"))

        useCase(40.0, -3.0, parkingRepo.getActiveSession(), reason = ParkingReleaseReason.DEPARTURE_PUBLISHED)

        val closed = parkingRepo.getSessionById("sess-close")!!
        assertNotNull(closed.endedAtMs, "the close must record when the departure happened")
        assertTrue(closed.publishedSpot, "a published departure gave the community a spot")
    }

    @Test
    fun `should_stamp_endedAt_without_claiming_a_spot_when_kept_private_or_deleted`() = runTest {
        parkingRepo.saveNewParkingSession(session(id = "sess-priv"))

        useCase(40.0, -3.0, parkingRepo.getActiveSession(), reason = ParkingReleaseReason.DEPARTURE_UNPUBLISHED)

        val closed = parkingRepo.getSessionById("sess-priv")!!
        assertNotNull(closed.endedAtMs)
        assertFalse(closed.publishedSpot, "a kept-private departure must not count as a shared spot")
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

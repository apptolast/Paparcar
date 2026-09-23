package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingReleaseReason
import com.rndeveloper.paparcar.domain.model.SpotType
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import com.rndeveloper.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.SessionEndReason
import com.rndeveloper.paparcar.fakes.FakeAnalyticsTracker
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeAddressAndPlaceRepository
import com.rndeveloper.paparcar.fakes.FakeDetectionEventLogger
import com.rndeveloper.paparcar.fakes.FakeGeofenceManager
import com.rndeveloper.paparcar.fakes.FakeReportSpotScheduler
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeZoneRepository
import com.rndeveloper.paparcar.domain.model.Zone
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import com.rndeveloper.paparcar.domain.detection.PendingSpotRelease
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
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
    private val geofence = com.rndeveloper.paparcar.fakes.FakeGeofenceManager()
    private val eventLogger = FakeDetectionEventLogger()
    private val tracker = FakeAnalyticsTracker()
    private val zoneRepo = FakeZoneRepository()
    private val prefs = FakeAppPreferences()
    private val useCase = ReleaseActiveParkingSessionUseCase(
        reportSpotReleased, parkingRepo, geofence, eventLogger, zoneRepo, tracker, prefs,
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

    /** A private zone drawn around [location] — where every session in this file is parked. */
    private fun privateZoneHere(
        id: String = "zone-garage",
        name: String = "Garaje",
        radiusMeters: Float = 50f,
    ) = Zone(
        id = id,
        userId = "u-1",
        name = name,
        lat = location.latitude,
        lon = location.longitude,
        iconKey = "HOME",
        createdAt = 0L,
        radiusMeters = radiusMeters,
        isPrivate = true,
    )

    // ── [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] ───────
    // The tests this file did NOT have. It had "zone" tests all along — but of
    // `zoneRadiusMeters`, the honest-close doubt circle. Two different things called zone in one
    // file is how a green suite looked like it had checked this.

    @Test
    fun `should_notPublishTheSpot_when_theUserLeavesFromInsideAPrivateZone`() = runTest {
        zoneRepo.zones = listOf(privateZoneHere())
        parkingRepo.saveNewParkingSession(session(id = "sess-garage"))

        val result = useCase(location.latitude, location.longitude, parkingRepo.getActiveSession())

        assertEquals(0, scheduler.scheduleCallCount, "the plaza of your own garage is not the community's")
        assertEquals(false, result.getOrNull(), "…and the UI must not thank the user for a plaza")
        val closed = parkingRepo.getSessionById("sess-garage")!!
        assertNotNull(closed.endedAtMs, "the session still closes — only the publication is gated")
        assertFalse(closed.publishedSpot)
    }

    @Test
    fun `should_notPublishTheSpot_when_theZoneWasDrawnAfterTheCarWasAlreadyParked`() = runTest {
        // The session carries NO `privateZoneId`: it was confirmed before the zone existed, which
        // is exactly the case the stamped verdict could never see. The gate asks the zones as they
        // are now, so protection starts the moment the user draws it.
        parkingRepo.saveNewParkingSession(session(id = "sess-late-zone"))
        assertNull(parkingRepo.getActiveSession()!!.privateZoneId, "precondition: nothing was stamped")
        zoneRepo.zones = listOf(privateZoneHere())

        val result = useCase(location.latitude, location.longitude, parkingRepo.getActiveSession())

        assertEquals(0, scheduler.scheduleCallCount)
        assertEquals(false, result.getOrNull())
    }

    @Test
    fun `should_publishTheSpot_when_theZoneThatCoveredThisParkWasDeleted`() = runTest {
        // The mirror case, and the reason the stamp had to go rather than merely be re-read: a
        // session stamped `privateZoneId` stayed silent forever, even after the user withdrew the
        // privacy. `zoneRepo` is empty, so the same park now frees a plaza.
        parkingRepo.saveNewParkingSession(session(id = "sess-ex-zone").copy(privateZoneId = "zone-garage"))

        val result = useCase(location.latitude, location.longitude, parkingRepo.getActiveSession())

        assertEquals(1, scheduler.scheduleCallCount, "the zone is gone; the place is public again")
        assertEquals(true, result.getOrNull())
    }

    @Test
    fun `should_publishTheSpot_when_theOnlyZoneNearbyIsNotPrivate`() = runTest {
        // A habitual zone the user did NOT mark private is a navigation shortcut, nothing more.
        // `FakeZoneRepository.getPrivateZonesSnapshot` filters it out, as the real one does.
        zoneRepo.zones = listOf(privateZoneHere(id = "zone-work", name = "Trabajo").copy(isPrivate = false))

        val result = useCase(location.latitude, location.longitude, session())

        assertEquals(1, scheduler.scheduleCallCount)
        assertEquals(true, result.getOrNull())
    }

    // ── [VEH-ACTIVE-FENCE-001] Release observability ─────────────────────────

    @Test
    fun `should_log_a_Released_event_with_publish_flag_and_location`() = runTest {
        useCase(lat = 40.5, lon = -3.6, currentSession = session(id = "sess-1"), reason = ParkingReleaseReason.DEPARTURE)

        val released = eventLogger.events.filterIsInstance<DetectionEvent.Released>()
        assertEquals(1, released.size)
        assertEquals("sess-1", released.first().sessionId)
        assertTrue(released.first().published)
        assertEquals(40.5, released.first().location?.latitude)
    }

    @Test
    fun `should_not_log_a_Released_event_when_there_is_no_session`() = runTest {
        useCase(lat = 40.5, lon = -3.6, currentSession = null, reason = ParkingReleaseReason.DEPARTURE)

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

    // ── Product analytics [ANALYTICS-EVENTS-001] ─────────────────────────────

    @Test
    fun `should_emit_session_ended_as_manual_release_when_clear_succeeds`() = runTest {
        useCase(lat = 40.5, lon = -3.6, currentSession = session(id = "sess-1"))

        val ended = tracker.events.filterIsInstance<AnalyticsEvent.ParkingSessionEnded>().single()
        assertEquals(SessionEndReason.MANUAL_RELEASE, ended.reason)
    }

    @Test
    fun `should_emit_no_session_ended_when_there_is_no_session`() = runTest {
        useCase(lat = 40.5, lon = -3.6, currentSession = null)

        assertTrue(tracker.events.filterIsInstance<AnalyticsEvent.ParkingSessionEnded>().isEmpty())
    }

    // ── [DET-AUDIT-002 T5/M4] The release must unregister the session's fence ─

    @Test
    fun `should_removeGeofence_when_sessionHasOne`() = runTest {
        useCase(40.416775, -3.703790, session(geofenceId = "geof-1"))

        assertEquals(listOf("geof-1"), geofence.removedIds, "manual release must not leave a NEVER_EXPIRE orphan fence")
    }

    @Test
    fun `should_removeGeofence_alsoWhenNothingIsPublished`() = runTest {
        // [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] There is no longer a
        // "leave without sharing" button; a departure that publishes nothing is one out of a
        // private zone, and the fence has to come down all the same.
        zoneRepo.zones = listOf(privateZoneHere())

        useCase(40.416775, -3.703790, session(geofenceId = "geof-2"))

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

        useCase(40.0, -3.0, parkingRepo.getActiveSession(), reason = ParkingReleaseReason.DEPARTURE)

        val closed = parkingRepo.getSessionById("sess-close")!!
        assertNotNull(closed.endedAtMs, "the close must record when the departure happened")
        assertTrue(closed.publishedSpot, "a published departure gave the community a spot")
    }

    @Test
    fun `should_stamp_endedAt_without_claiming_a_spot_when_theRecordIsDeleted`() = runTest {
        parkingRepo.saveNewParkingSession(session(id = "sess-priv"))

        useCase(40.0, -3.0, parkingRepo.getActiveSession(), reason = ParkingReleaseReason.RECORD_DELETED)

        val closed = parkingRepo.getSessionById("sess-priv")!!
        assertNotNull(closed.endedAtMs)
        assertFalse(closed.publishedSpot, "a deleted record must not count as a shared spot")
    }

    // ── [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] "Me voy" inherits the session's shape ───

    @Test
    fun `should_publishAZoneSpot_when_theReleasedSessionWasABoundedZone`() = runTest {
        useCase(40.0, -3.0, session(reliability = 1f).copy(zoneRadiusMeters = 60f))

        assertEquals(60f, scheduler.lastZoneRadiusMeters)
        assertEquals(0.5f, scheduler.lastConfidence, "the release witnesses the departure, not the place")
        assertEquals(true, scheduler.lastProvisional)
    }

    @Test
    fun `should_withholdTheSpotButStillClear_when_theZoneIsWiderThanTheCeiling`() = runTest {
        parkingRepo.saveNewParkingSession(session(id = "sess-wide").copy(zoneRadiusMeters = 250f))

        val result = useCase(40.0, -3.0, parkingRepo.getActiveSession(), reason = ParkingReleaseReason.DEPARTURE)

        assertEquals(0, scheduler.scheduleCallCount, "a neighbourhood is not a plaza")
        assertEquals(false, result.getOrNull(), "the caller must learn nothing went out — no thank-you toast")
        val closed = parkingRepo.getSessionById("sess-wide")!!
        assertNotNull(closed.endedAtMs, "the session still closes")
        assertFalse(closed.publishedSpot, "and its row must not claim a spot it never gave")
    }

    @Test
    fun `should_returnTrue_when_aSpotActuallyWentOut`() = runTest {
        val result = useCase(40.0, -3.0, session())

        assertEquals(true, result.getOrNull())
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

    @Test
    fun should_retireTheBoardingStamp_when_theUserReleasesAPlazaThatWasAlreadyAnnounced() = runTest {
        // [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] The trap this closes, found by asking what
        // happens when the user taps "Me voy" right after boarding:
        //
        //   board → plaza out as RELEASING → "Me voy" publishes the REAL spot on the SAME id →
        //   the coordinator session ends with the stamp still live → its teardown WITHDRAWS the
        //   spot the user just published.
        //
        // A real plaza taken down seconds after going out. The publish and the stamp's retirement
        // have to travel together, exactly as they do on the automatic departure path.
        prefs.storedSpotRelease = PendingSpotRelease("session-1", "veh-A", nominatedAtMs = 1_000L)

        useCase(40.0, -3.0, session(id = "session-1"))

        assertNull(
            prefs.storedSpotRelease,
            "the release promoted the document; leaving the stamp lets the teardown undo it",
        )
    }

    @Test
    fun should_leaveAnotherSessionsStampAlone_when_releasingThisOne() = runTest {
        // Keyed by id, so releasing one car never disarms the nomination of another.
        prefs.storedSpotRelease = PendingSpotRelease("some-other-session", "veh-B", nominatedAtMs = 1_000L)

        useCase(40.0, -3.0, session(id = "session-1"))

        assertNotNull(prefs.storedSpotRelease)
    }
}

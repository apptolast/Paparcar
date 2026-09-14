package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import com.rndeveloper.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.domain.analytics.SessionEndReason
import com.rndeveloper.paparcar.fakes.FakeAddressAndPlaceRepository
import com.rndeveloper.paparcar.fakes.FakeAnalyticsTracker
import com.rndeveloper.paparcar.fakes.FakeBtConnectStampReader
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeDetectionEventLogger
import com.rndeveloper.paparcar.fakes.FakeGeofenceManager
import com.rndeveloper.paparcar.fakes.FakeReportSpotScheduler
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeZoneRepository
import com.rndeveloper.paparcar.domain.model.Zone
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-HANDOFF-NOT-MANUAL-001 §B] The other half of a deduced departure: the commit, moved from the
 * guess to the moment a drive is actually MEASURED.
 */
class FinalizeDeducedDepartureUseCaseTest {

    /**
     * A private zone drawn over [point]. [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001]
     * The gate no longer reads the session's `privateZoneId` stamp — it asks the zones as they are
     * — so a private-zone case is set up by the user HAVING the zone, which is also how a real
     * account reaches this state.
     */
    private fun privateZoneOver(point: GpsPoint) = Zone(
        id = "zone-1",
        userId = "user-42",
        name = "Garaje",
        lat = point.latitude,
        lon = point.longitude,
        iconKey = "HOME",
        createdAt = 0L,
        radiusMeters = 50f,
        isPrivate = true,
    )

    /** Where every session in this file is parked — see [session]. */
    private val park = GpsPoint(40.4, -3.7, 8f, 1_000_000L, 0f)

    private fun session(
        id: String = "session-1",
        vehicleId: String? = "v-1",
        pendingAtMs: Long? = 1_000_000L,
        privateZoneId: String? = null,
    ) = UserParking(
        id = id,
        userId = "user-42",
        vehicleId = vehicleId,
        location = GpsPoint(40.4, -3.7, 8f, 1_000_000L, 0f),
        geofenceId = id,
        isActive = true,
        privateZoneId = privateZoneId,
        provisionalDepartureAtMs = pendingAtMs,
    )

    @Test
    fun should_promote_the_spot_and_release_the_car_when_a_drive_is_finally_measured() = runTest {
        // The deduction was right: the car really did leave. Now — and only now — the spot earns
        // its full lifetime and the session is released.
        val repo = FakeUserParkingRepository(initialSession = session())
        val spotScheduler = FakeReportSpotScheduler()
        val geofence = FakeGeofenceManager()

        val finalized = buildUseCase(repo, spotScheduler, geofence)("v-1")

        assertTrue(finalized)
        assertEquals(1, spotScheduler.scheduleCallCount, "the same spot is re-published…")
        assertEquals("session-1", spotScheduler.lastSpotId, "…under the same id (one document)")
        assertEquals(false, spotScheduler.lastProvisional, "…with the full TTL")
        assertFalse(repo.getActiveSessionByGeofence("session-1") != null, "the car is released")
        assertEquals(listOf("session-1"), geofence.removedIds)
    }

    @Test
    fun should_do_nothing_when_the_session_has_no_pending_deduction() = runTest {
        // The normal case for every WITNESSED departure: it already committed at departure time, so
        // a drive proven later finds nothing outstanding. Also the bus/taxi case — a drive measured
        // by a phone whose car never moved must not release that car.
        val repo = FakeUserParkingRepository(initialSession = session(pendingAtMs = null))
        val spotScheduler = FakeReportSpotScheduler()
        val geofence = FakeGeofenceManager()

        val finalized = buildUseCase(repo, spotScheduler, geofence)("v-1")

        assertFalse(finalized)
        assertEquals(0, spotScheduler.scheduleCallCount)
        assertTrue(repo.getActiveSessionByGeofence("session-1") != null, "the car stays parked")
        assertEquals(emptyList(), geofence.removedIds)
    }

    // ── [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] The promotion keeps the session's shape ─

    @Test
    fun should_promoteAsAZoneSpot_when_theSessionWasABoundedZone() = runTest {
        // The drive just measured proves the HOUR of the departure, never the PLACE — the
        // promotion keeps the radius, the capped confidence and the short lifetime.
        val repo = FakeUserParkingRepository(
            initialSession = session().copy(zoneRadiusMeters = 60f, detectionReliability = 1f),
        )
        val spotScheduler = FakeReportSpotScheduler()

        val finalized = buildUseCase(repo, spotScheduler)("v-1")

        assertTrue(finalized)
        assertEquals(60f, spotScheduler.lastZoneRadiusMeters)
        assertEquals(true, spotScheduler.lastProvisional, "position doubt does not expire with a drive")
        assertEquals(0.5f, spotScheduler.lastConfidence, "a user 'Sí' proves the park, not the spot")
    }

    @Test
    fun should_promoteNothingButStillReleaseTheCar_when_theZoneIsWiderThanTheCeiling() = runTest {
        // A withheld zone published nothing at deduction time, so there is nothing to promote —
        // exactly the private-zone shape of this flow.
        val repo = FakeUserParkingRepository(
            initialSession = session().copy(zoneRadiusMeters = 250f, detectionReliability = 0.5f),
        )
        val spotScheduler = FakeReportSpotScheduler()
        val geofence = FakeGeofenceManager()

        val finalized = buildUseCase(repo, spotScheduler, geofence)("v-1")

        assertTrue(finalized)
        assertEquals(0, spotScheduler.scheduleCallCount)
        assertFalse(repo.getActiveSessionByGeofence("session-1") != null, "the car is still released")
    }

    @Test
    fun should_do_nothing_when_the_vehicle_is_unknown_or_has_no_active_session() = runTest {
        val repo = FakeUserParkingRepository(initialSession = session(vehicleId = "other-car"))
        val useCase = buildUseCase(repo)

        assertFalse(useCase(null), "no vehicle locked yet")
        assertFalse(useCase("v-1"), "another car's pending deduction is not this trip's business")
    }

    @Test
    fun should_not_publish_a_private_zone_but_still_release_the_car() = runTest {
        // A private zone was never advertised, so there is nothing to promote — the release still
        // has to happen, or the car would stay pinned at a place it has provably left.
        val repo = FakeUserParkingRepository(initialSession = session())
        val spotScheduler = FakeReportSpotScheduler()
        val geofence = FakeGeofenceManager()
        val zones = FakeZoneRepository().apply { this.zones = listOf(privateZoneOver(park)) }

        val finalized = buildUseCase(repo, spotScheduler, geofence, zones = zones)("v-1")

        assertTrue(finalized)
        assertEquals(0, spotScheduler.scheduleCallCount)
        assertFalse(repo.getActiveSessionByGeofence("session-1") != null)
    }

    @Test
    fun should_stamp_the_close_with_the_deduced_departure_moment_not_the_proof_moment() = runTest {
        // The car left at deducedAt; the drive was only PROVEN later. The row must record the
        // real departure, and the promoted spot counts as its publication.
        // [VEH-STATS-SAY-SOMETHING-USEFUL-001]
        val repo = FakeUserParkingRepository(initialSession = session(pendingAtMs = 777_000L))

        buildUseCase(repo)("v-1")

        val closed = repo.getSessionById("session-1")!!
        assertEquals(777_000L, closed.endedAtMs)
        assertTrue(closed.publishedSpot)
    }

    @Test
    fun should_not_claim_a_spot_for_a_private_zone_close() = runTest {
        val repo = FakeUserParkingRepository(initialSession = session())
        val zones = FakeZoneRepository().apply { this.zones = listOf(privateZoneOver(park)) }

        buildUseCase(repo, zones = zones)("v-1")

        assertFalse(repo.getSessionById("session-1")!!.publishedSpot)
    }

    private fun buildUseCase(
        repo: FakeUserParkingRepository = FakeUserParkingRepository(),
        spotScheduler: FakeReportSpotScheduler = FakeReportSpotScheduler(),
        geofence: FakeGeofenceManager = FakeGeofenceManager(),
        tracker: FakeAnalyticsTracker? = null,
        btConnectStamps: FakeBtConnectStampReader? = null,
        zones: FakeZoneRepository = FakeZoneRepository(),
    ) = FinalizeDeducedDepartureUseCase(
        userParkingRepository = repo,
        reportSpotReleased = ReportSpotReleasedUseCase(
            reportSpotScheduler = spotScheduler,
            getAddressAndPlace = GetAddressAndPlaceUseCase(repository = FakeAddressAndPlaceRepository()),
            authRepository = FakeAuthRepository(initialSession = FakeAuthRepository.authenticatedSession(userId = "user-42")),
            // The SAME tracker as the outer use case: proves the promotion emits no spot event
            // while the close emits its session_ended. [ANALYTICS-EVENTS-001]
            analyticsTracker = tracker,
        ),
        geofenceService = geofence,
        detectionEventLogger = FakeDetectionEventLogger(),
        analyticsTracker = tracker,
        btConnectStamps = btConnectStamps,
        zoneRepository = zones,
    )

    // ── Product analytics [ANALYTICS-EVENTS-001] ─────────────────────────────

    @Test
    fun should_emit_session_ended_as_departure_deduced_and_no_second_spot_event() = runTest {
        // The provisional publish already counted this spot when the deduction fired; the
        // promotion here must add only the session close.
        val tracker = FakeAnalyticsTracker()
        val repo = FakeUserParkingRepository(initialSession = session())

        buildUseCase(repo = repo, tracker = tracker)("v-1")

        val ended = tracker.events.filterIsInstance<AnalyticsEvent.ParkingSessionEnded>().single()
        assertEquals(SessionEndReason.DEPARTURE_DEDUCED, ended.reason)
        assertTrue(
            tracker.events.filterIsInstance<AnalyticsEvent.SpotReleased>().isEmpty(),
            "the promotion re-publishes the same document and must not count a second spot",
        )
    }

    @Test
    fun should_promoteNothing_when_thePairedCarConnectedElsewhereSinceThePark() = runTest {
        // [DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS-001] La SEGUNDA puerta a la comunidad. Una sesión
        // puede haberse marcado provisional legítimamente y que el coche lo mueva otra persona
        // DESPUÉS; promover entonces publicaría la plaza fantasma con la vida COMPLETA, que es peor
        // que la provisional. El barrido de consumidores es lo que encontró esta puerta.
        val repo = FakeUserParkingRepository(initialSession = session(pendingAtMs = 1_500_000L))
        val spotScheduler = FakeReportSpotScheduler()
        val stamps = FakeBtConnectStampReader().apply {
            stamp(
                "v-1",
                atMs = 2_000_000L,
                fix = GpsPoint(40.4126, -3.7, 8f, 2_000_000L, 0f), // ~1,4 km al norte del pin
            )
        }

        buildUseCase(repo, spotScheduler, btConnectStamps = stamps)("v-1")

        assertEquals(0, spotScheduler.scheduleCallCount, "no se promueve una plaza que el coche ya no ocupa")
    }
}

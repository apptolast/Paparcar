package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.fakes.FakeAddressAndPlaceRepository
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeDetectionEventLogger
import io.apptolast.paparcar.fakes.FakeGeofenceManager
import io.apptolast.paparcar.fakes.FakeReportSpotScheduler
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
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
        val repo = FakeUserParkingRepository(initialSession = session(privateZoneId = "zone-1"))
        val spotScheduler = FakeReportSpotScheduler()
        val geofence = FakeGeofenceManager()

        val finalized = buildUseCase(repo, spotScheduler, geofence)("v-1")

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
        val repo = FakeUserParkingRepository(initialSession = session(privateZoneId = "zone-1"))

        buildUseCase(repo)("v-1")

        assertFalse(repo.getSessionById("session-1")!!.publishedSpot)
    }

    private fun buildUseCase(
        repo: FakeUserParkingRepository = FakeUserParkingRepository(),
        spotScheduler: FakeReportSpotScheduler = FakeReportSpotScheduler(),
        geofence: FakeGeofenceManager = FakeGeofenceManager(),
    ) = FinalizeDeducedDepartureUseCase(
        userParkingRepository = repo,
        reportSpotReleased = ReportSpotReleasedUseCase(
            reportSpotScheduler = spotScheduler,
            getAddressAndPlace = GetAddressAndPlaceUseCase(repository = FakeAddressAndPlaceRepository()),
            authRepository = FakeAuthRepository(initialSession = FakeAuthRepository.authenticatedSession(userId = "user-42")),
        ),
        geofenceService = geofence,
        detectionEventLogger = FakeDetectionEventLogger(),
    )
}

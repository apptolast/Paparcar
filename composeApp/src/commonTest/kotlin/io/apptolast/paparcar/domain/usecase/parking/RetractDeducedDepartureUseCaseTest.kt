package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.SpotTtlPolicy
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.fakes.FakeDetectionEventLogger
import io.apptolast.paparcar.fakes.FakeSpotRepository
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [DET-HANDOFF-NOT-MANUAL-001 §B.3] The losing half of a deduced departure: the trip it was deduced
 * from ended having measured no drive at all, so the community spot is taken back.
 */
class RetractDeducedDepartureUseCaseTest {

    private fun session(
        id: String = "session-1",
        vehicleId: String? = "v-1",
        pendingAtMs: Long? = PENDING_AT_MS,
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
    fun should_withdraw_the_spot_when_the_trip_ended_without_ever_measuring_a_drive() = runTest {
        // Field 2026-08-19: the phone left on a bicycle, the car never moved, and a stranger was
        // being offered a space that was never freed.
        val parkings = FakeUserParkingRepository(initialSession = session())
        val spots = FakeSpotRepository()

        val retracted = buildUseCase(parkings, spots)()

        assertEquals(1, retracted)
        assertEquals(listOf("session-1"), spots.retractedSpotIds, "the spot the deduction published")
    }

    @Test
    fun should_keep_the_car_and_its_pending_marker_when_a_spot_is_withdrawn() = runTest {
        // Retracting withdraws a REPORT, it does not close the case. The session survives (nothing
        // measured says the car moved) and so does the marker — which is also the "this deduction
        // already spent its one publication" guard, without which the safety net would re-deduce
        // the same departure 15 minutes later and republish the same wrong guess.
        val parkings = FakeUserParkingRepository(initialSession = session())

        buildUseCase(parkings)()

        val stillThere = parkings.getActiveSessionByGeofence("session-1")
        assertNotNull(stillThere, "the car stays parked")
        assertEquals(1_000_000L, stillThere.provisionalDepartureAtMs, "and the deduction stays pending")
    }

    @Test
    fun should_do_nothing_when_no_departure_is_pending() = runTest {
        // The normal end of every ordinary trip — including every witnessed departure, which
        // committed at departure time and left nothing outstanding.
        val parkings = FakeUserParkingRepository(initialSession = session(pendingAtMs = null))
        val spots = FakeSpotRepository()

        assertEquals(0, buildUseCase(parkings, spots)())
        assertTrue(spots.retractedSpotIds.isEmpty())
    }

    @Test
    fun should_not_withdraw_anything_for_a_private_zone_because_nothing_was_ever_published() = runTest {
        val parkings = FakeUserParkingRepository(initialSession = session(privateZoneId = "zone-1"))
        val spots = FakeSpotRepository()

        assertEquals(0, buildUseCase(parkings, spots)())
        assertTrue(spots.retractedSpotIds.isEmpty())
    }

    @Test
    fun should_report_zero_when_the_withdrawal_itself_fails() = runTest {
        // Offline, or the expiry sweep already deleted the document. Nothing else to do: the short
        // provisional TTL is the floor precisely because this call can fail.
        val parkings = FakeUserParkingRepository(initialSession = session())
        val spots = FakeSpotRepository().apply { retractResult = Result.failure(IllegalStateException("offline")) }

        assertEquals(0, buildUseCase(parkings, spots)())
        assertEquals(listOf("session-1"), spots.retractedSpotIds, "it was attempted")
    }

    // [DET-RETRACT-DENIED-FOREVER-001] The withdrawal is only attempted while the provisional spot
    // could still be out there, so every case above has to sit inside that window — a minute after
    // the deduction. Before the bound existed this was the wall clock, which put every fixture
    // decades past the TTL without anyone noticing, because nothing checked.
    @Test
    fun should_not_attempt_a_withdrawal_once_the_provisional_ttl_has_run_out() = runTest {
        // Field 2026-08-23 → 26 (Oppo): a departure 60 min stale cleared WITHOUT publishing, and the
        // marker stayed. Every session end since then tried to withdraw a spot that had never
        // existed — 256 times over five days, each one answered PERMISSION_DENIED because the rules
        // dereference `resource.data` on a document that is not there.
        val parkings = FakeUserParkingRepository(initialSession = session())
        val spots = FakeSpotRepository()

        val retracted = RetractDeducedDepartureUseCase(
            userParkingRepository = parkings,
            spotRepository = spots,
            detectionEventLogger = FakeDetectionEventLogger(),
            nowMs = { PENDING_AT_MS + SpotTtlPolicy.PROVISIONAL_SPOT_TTL_MS + 1 },
        )()

        assertEquals(0, retracted)
        assertTrue(spots.retractedSpotIds.isEmpty(), "nothing left to withdraw — the TTL already did it")
    }

    @Test
    fun should_still_withdraw_on_the_last_millisecond_of_the_provisional_window() = runTest {
        val parkings = FakeUserParkingRepository(initialSession = session())
        val spots = FakeSpotRepository()

        val retracted = RetractDeducedDepartureUseCase(
            userParkingRepository = parkings,
            spotRepository = spots,
            detectionEventLogger = FakeDetectionEventLogger(),
            nowMs = { PENDING_AT_MS + SpotTtlPolicy.PROVISIONAL_SPOT_TTL_MS },
        )()

        assertEquals(1, retracted, "still inside the window — the spot can still be out there")
    }

    private fun buildUseCase(
        parkings: FakeUserParkingRepository = FakeUserParkingRepository(),
        spots: FakeSpotRepository = FakeSpotRepository(),
    ) = RetractDeducedDepartureUseCase(
        userParkingRepository = parkings,
        spotRepository = spots,
        detectionEventLogger = FakeDetectionEventLogger(),
        nowMs = { PENDING_AT_MS + 60_000L },
    )

    private companion object {
        const val PENDING_AT_MS = 1_000_000L
    }
}

package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.fakes.FakeDetectionEventLogger
import com.rndeveloper.paparcar.fakes.FakeGeofenceManager
import com.rndeveloper.paparcar.fakes.FakeSpotRepository
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [DET-A-TRAIN-IS-NOT-A-CAR-001] Giving the car back after a departure the trip itself refuted.
 *
 * The sibling of `RetractDeducedDepartureUseCaseTest`, and the difference between them is the whole
 * reason this use case exists: there, the departure was DEDUCED and never took the car, so there was
 * a spot to withdraw and nothing to restore. Here it was WITNESSED — 45 seconds after the arm, by a
 * train pulling out of a platform — so the session was closed and the plaza published for real.
 */
class RestoreRefutedDepartureUseCaseTest {

    private fun departedSession(
        id: String = FENCE,
        vehicleId: String? = "v-1",
        publishedSpot: Boolean = true,
        retractedAtMs: Long? = null,
        isActive: Boolean = false,
    ) = UserParking(
        id = id,
        userId = "user-42",
        vehicleId = vehicleId,
        location = GpsPoint(36.6040534, -6.2181624, 8f, 1_000_000L, 0f),
        geofenceId = id,
        isActive = isActive,
        endedAtMs = if (isActive) null else DEPARTED_AT_MS,
        publishedSpot = publishedSpot,
        retractedAtMs = retractedAtMs,
    )

    private fun buildUseCase(
        parkings: FakeUserParkingRepository,
        spots: FakeSpotRepository = FakeSpotRepository(),
        fences: FakeGeofenceManager = FakeGeofenceManager(),
    ) = RestoreRefutedDepartureUseCase(
        userParkingRepository = parkings,
        spotRepository = spots,
        geofenceManager = fences,
        config = ParkingDetectionConfig(),
        detectionEventLogger = FakeDetectionEventLogger(),
        nowMs = { NOW_MS },
    )

    @Test
    fun should_reopenTheParking_when_theTripThatTookItWasNoRoadVehicle() = runTest {
        val parkings = FakeUserParkingRepository(initialSession = departedSession())

        val restored = buildUseCase(parkings)(FENCE, "v-1")

        assertTrue(restored)
        val back = parkings.getActiveSessionByVehicle("v-1")
        assertNotNull(back, "the car must be parked again, where it actually is")
        assertEquals(FENCE, back.id)
        // `endedAtMs` cleared on purpose: the departure never happened, so the REAL one — whenever
        // it comes — must be able to date itself. A left-behind stamp is a close that cannot be
        // re-closed, which is what the DAO's COALESCE would otherwise guarantee.
        assertNull(back.endedAtMs, "a car that never left has no departure instant")
        assertFalse(back.publishedSpot, "…and freed no plaza, because the one it freed is withdrawn")
    }

    @Test
    fun should_withdrawThePlaza_when_theDepartureThatPublishedItIsRefuted() = runTest {
        val parkings = FakeUserParkingRepository(initialSession = departedSession())
        val spots = FakeSpotRepository()

        buildUseCase(parkings, spots)(FENCE, "v-1")

        assertEquals(
            listOf(FENCE),
            spots.retractedSpotIds,
            "a stranger must stop being offered a space that was never freed",
        )
    }

    @Test
    fun should_registerTheFenceAgain_so_theNextDepartureStillHasATrigger() = runTest {
        // Field 2026-08-19 priced this omission on the deduced path: "the geofence removal took
        // their next EXIT trigger with them". A restored pin with no fence is a car that can never
        // announce leaving again.
        val parkings = FakeUserParkingRepository(initialSession = departedSession())
        val fences = FakeGeofenceManager()

        buildUseCase(parkings, fences = fences)(FENCE, "v-1")

        assertEquals(listOf(FENCE), fences.createdIds)
    }

    @Test
    fun should_notWithdrawAnything_when_theDepartureNeverPublishedAPlaza() = runTest {
        // A private zone, a stale departure, a withheld shape — several reasons end here, and all
        // of them mean there is no document out there to take back. `retractSpot` is an UPDATE
        // against a document that does not exist, which answers PERMISSION_DENIED rather than
        // NOT_FOUND [DET-RETRACT-DENIED-FOREVER-001].
        val parkings = FakeUserParkingRepository(initialSession = departedSession(publishedSpot = false))
        val spots = FakeSpotRepository()

        val restored = buildUseCase(parkings, spots)(FENCE, "v-1")

        assertTrue(restored, "the car still comes back")
        assertEquals(emptyList(), spots.retractedSpotIds)
    }

    // ── The three refusals ─────────────────────────────────────────────────────

    @Test
    fun should_refuse_when_theCarAlreadyHasANewerPark() = runTest {
        // [DET-ASSERTION-OUTRANKS-INFERENCE-001] The user marked their parking at the destination,
        // or a genuine later trip pinned one. Whatever put it there knows more than a refuted trip
        // does, and two active sessions for one car is a state nothing downstream can read.
        val parkings = FakeUserParkingRepository(initialSession = departedSession())
        parkings.saveNewParkingSession(departedSession(id = "marked-by-hand", isActive = true))

        val restored = buildUseCase(parkings)(FENCE, "v-1")

        assertFalse(restored)
        assertEquals(0, parkings.restoreParkingSessionCallCount, "the repository is never even asked")
    }

    @Test
    fun should_refuse_when_theSessionWasNeverClosed() = runTest {
        // A deduced departure keeps its session. That case has its own undo, and running both
        // would make the two fight over one row.
        val parkings = FakeUserParkingRepository(initialSession = departedSession(isActive = true))

        assertFalse(buildUseCase(parkings)(FENCE, "v-1"))
        assertEquals(0, parkings.restoreParkingSessionCallCount)
    }

    @Test
    fun should_refuse_when_thereIsNoSessionBehindTheFence() = runTest {
        val parkings = FakeUserParkingRepository(initialSession = departedSession())

        assertFalse(buildUseCase(parkings)("a-fence-nobody-parked-at", "v-1"))
    }

    private companion object {
        const val FENCE = "park-by-the-station"
        const val DEPARTED_AT_MS = 1_789_472_801_366L
        const val NOW_MS = 1_789_489_000_000L
    }
}

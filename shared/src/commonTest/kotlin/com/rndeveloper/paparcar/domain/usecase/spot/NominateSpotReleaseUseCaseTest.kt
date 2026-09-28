package com.rndeveloper.paparcar.domain.usecase.spot

import com.rndeveloper.paparcar.domain.detection.PendingSpotRelease
import com.rndeveloper.paparcar.domain.detection.SpotReleaseAction
import com.rndeveloper.paparcar.domain.detection.SpotReleaseLane
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import com.rndeveloper.paparcar.fakes.FakeAddressAndPlaceRepository
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeDetectionEventLogger
import com.rndeveloper.paparcar.fakes.FakeReportSpotScheduler
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeZoneRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-A-RELEASING-SPOT-MUST-SAY-IT-WENT-OUT-001] Every ending of the boarding lane reaches the
 * remote trace, and says which lane produced it.
 *
 * The use case had no test at all, which is how it came to be the only detection verdict whose
 * answer never left the device: the 27-09-2026 headphone field-test has the night's whole trace in
 * Firestore and not one event of this lane. A refusal that cannot be cited is a refusal that cannot
 * be diagnosed. [DET-VERDICT-NOT-PREDICATE-001]
 */
class NominateSpotReleaseUseCaseTest {

    private val nowMs = 1_000_000L
    private val location = GpsPoint(36.6331017, -6.22814, 11.3f, nowMs, 0f)

    private val zoneRepo = FakeZoneRepository()
    private val scheduler = FakeReportSpotScheduler()
    private val prefs = FakeAppPreferences()
    private val logger = FakeDetectionEventLogger()

    /** The boarded car's live plaza, or none at all. */
    private fun useCaseWith(session: UserParking?) = NominateSpotReleaseUseCase(
        userParkingRepository = FakeUserParkingRepository(initialSession = session),
        zoneRepository = zoneRepo,
        evaluate = EvaluateSpotReleaseNominationUseCase(),
        reportSpotReleased = ReportSpotReleasedUseCase(
            reportSpotScheduler = scheduler,
            getAddressAndPlace = GetAddressAndPlaceUseCase(FakeAddressAndPlaceRepository()),
            authRepository = FakeAuthRepository(initialSession = null),
        ),
        appPreferences = prefs,
        detectionEventLogger = logger,
        nowMs = { nowMs },
    )

    private fun parkedCar(id: String = "park-1", vehicleId: String = "veh-A") = UserParking(
        id = id,
        location = location,
        isActive = true,
        vehicleId = vehicleId,
    )

    private fun released(): List<DetectionEvent.SpotRelease> =
        logger.events.filterIsInstance<DetectionEvent.SpotRelease>()

    @Test
    fun should_recordTheNominationWithItsLane_when_aBoardedCarStillHoldsAPlaza() = runTest {
        val useCase = useCaseWith(parkedCar())

        assertTrue(useCase("veh-A", boardingIsCredible = true, lane = SpotReleaseLane.BT))

        val event = released().single()
        assertEquals(SpotReleaseAction.NOMINATED, event.action)
        assertEquals(SpotReleaseLane.BT, event.lane)
        // The plaza's id IS the parking session's id — that is the promotion mechanism, and it is
        // what lets this event be joined to the park with no column of its own.
        assertEquals("park-1", event.sessionId)
        // The park's own fix travels, so the trace says from WHERE the plaza was claimed.
        assertEquals(location.latitude, event.location?.latitude)
    }

    @Test
    fun should_quoteTheRefusal_when_thisSessionAlreadySpentItsNomination() = runTest {
        // The refusal from the 27-09 field test that could not be checked: a session that has
        // nominated once is spent for good — the stamp survives even a withdrawal — so every later
        // boarding of the SAME park is silent. Whether that is what happened was unknowable,
        // because the reason never left the phone.
        val useCase = useCaseWith(parkedCar())
        prefs.storedSpotRelease = PendingSpotRelease("park-1", "veh-A", nowMs - 1)

        assertFalse(useCase("veh-A", boardingIsCredible = true, lane = SpotReleaseLane.BT))

        val event = released().single()
        assertEquals(SpotReleaseAction.SKIPPED, event.action)
        assertEquals(EvaluateSpotReleaseNominationUseCase.REASON_ALREADY_NOMINATED, event.reason)
        assertEquals(SpotReleaseLane.BT, event.lane)
        assertEquals(0, scheduler.scheduleCallCount, "nothing may go out on a refusal")
    }

    @Test
    fun should_quoteTheRefusal_when_theLaneItselfDoesNotVouchForTheBoarding() = runTest {
        // A reconnect inside the disconnect hunt: the head unit blinked, nobody boarded. Exactly
        // the shape a bench test with headphones produces, and it looked identical to a broken lane.
        val useCase = useCaseWith(parkedCar())

        assertFalse(useCase("veh-A", boardingIsCredible = false, lane = SpotReleaseLane.BT))

        assertEquals(
            EvaluateSpotReleaseNominationUseCase.REASON_NOT_A_BOARDING,
            released().single().reason,
        )
    }

    @Test
    fun should_fileTheRefusalUnderTheDailyLedger_when_thereIsNoPlazaToFreeAtAll() = runTest {
        // No session means no parking document to hang the event from. It still has to be readable,
        // so it goes under the daily trigger ledger — the same out-of-session convention TRIGGER
        // and PROCESS_DEATH already use.
        val useCase = useCaseWith(null)

        assertFalse(useCase("veh-A", boardingIsCredible = true, lane = SpotReleaseLane.COORDINATOR))

        val event = released().single()
        assertEquals(EvaluateSpotReleaseNominationUseCase.REASON_NO_PLAZA, event.reason)
        assertTrue(event.sessionId.startsWith("triggers_"), "filed under ${event.sessionId}")
    }
}

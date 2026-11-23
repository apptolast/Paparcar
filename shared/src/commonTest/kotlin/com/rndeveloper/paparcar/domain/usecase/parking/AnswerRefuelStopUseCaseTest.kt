package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.RefuelStopAnswer
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] */
class AnswerRefuelStopUseCaseTest {

    private fun stop(
        id: String = "s-pump",
        refuelStop: RefuelStopAnswer? = RefuelStopAnswer.PENDING,
        endedAtMs: Long? = 9_000L,
    ) = UserParking(
        id = id,
        userId = "u-1",
        vehicleId = "veh-1",
        location = GpsPoint(40.4301, -3.7038, 7f, 5_000L, 0f),
        isActive = false,
        endedAtMs = endedAtMs,
        refuelStop = refuelStop,
    )

    @Test
    fun should_countTowardsTheFigures_when_theUserSaysYes() = runTest {
        val repo = FakeUserParkingRepository(initialSessions = listOf(stop()))

        AnswerRefuelStopUseCase(repo)(sessionId = "s-pump", refuelled = true)

        assertEquals(RefuelStopAnswer.CONFIRMED, repo.getSessionById("s-pump")?.refuelStop)
    }

    @Test
    fun should_closeTheQuestionWithoutCounting_when_theUserSaysNo() = runTest {
        val repo = FakeUserParkingRepository(initialSessions = listOf(stop()))

        AnswerRefuelStopUseCase(repo)(sessionId = "s-pump", refuelled = false)

        assertEquals(RefuelStopAnswer.DISMISSED, repo.getSessionById("s-pump")?.refuelStop)
    }

    @Test
    fun should_leaveAnOrdinaryParkAlone_when_anAnswerArrivesForIt() {
        // The guard exists because an answer must never invent a refuel: a stale screen, a widget,
        // a retried write — none of them may turn a park the app never asked about into a tankful.
        runTest {
            val repo = FakeUserParkingRepository(
                initialSessions = listOf(stop(id = "s-plain", refuelStop = null)),
            )

            AnswerRefuelStopUseCase(repo)(sessionId = "s-plain", refuelled = true)

            assertNull(repo.getSessionById("s-plain")?.refuelStop)
        }
    }

    @Test
    fun should_stopOfferingTheQuestion_when_itHasBeenAnswered() = runTest {
        val repo = FakeUserParkingRepository(initialSessions = listOf(stop()))
        assertEquals("s-pump", repo.observePendingRefuelStop().first()?.id)

        AnswerRefuelStopUseCase(repo)(sessionId = "s-pump", refuelled = true)

        assertNull(repo.observePendingRefuelStop().first())
    }
}

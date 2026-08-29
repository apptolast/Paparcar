package com.rndeveloper.paparcar.domain.usecase.spot

import com.rndeveloper.paparcar.domain.model.SpotVoteOutcome
import com.rndeveloper.paparcar.fakes.FakeSpotRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001] The votes used to increment a counter nothing
 * read. These tests pin the consequence: a nearby "gone" withdraws the spot, a nearby "still
 * there" restarts its age, and a distant vote writes nothing at all.
 */
class SendSpotSignalUseCaseTest {

    private val nearby = 10.0
    private val faraway = 5_000.0

    @Test
    fun should_retractTheSpot_when_aWitnessSaysItIsGone() = runTest {
        val repo = FakeSpotRepository()

        val outcome = SendSpotSignalUseCase(repo)("sp_1", accepted = false, distanceMeters = nearby)

        assertEquals(SpotVoteOutcome.RETRACT, outcome.getOrNull())
        assertEquals(listOf("sp_1"), repo.retractedSpotIds)
        assertTrue(repo.refreshedSpotIds.isEmpty())
    }

    @Test
    fun should_refreshTheSpot_when_aWitnessSaysItIsStillThere() = runTest {
        val repo = FakeSpotRepository()

        val outcome = SendSpotSignalUseCase(repo)("sp_1", accepted = true, distanceMeters = nearby)

        assertEquals(SpotVoteOutcome.REFRESH, outcome.getOrNull())
        assertEquals(listOf("sp_1"), repo.refreshedSpotIds)
        assertTrue(repo.retractedSpotIds.isEmpty())
    }

    @Test
    fun should_stillRecordTheRawSignal_when_theVoteCounts() {
        // The counters remain the raw record and still feed communityConfidence(). What changed is
        // that the vote no longer ONLY does that.
        runTest {
            val repo = FakeSpotRepository()

            SendSpotSignalUseCase(repo)("sp_1", accepted = false, distanceMeters = nearby)

            assertEquals(1, repo.signalCallCount)
            assertEquals(false, repo.lastSignalAccepted)
        }
    }

    @Test
    fun should_writeNothingAtAll_when_theVoterIsTooFarAway() = runTest {
        // Not even the counter. Without the proximity gate one tap could withdraw a spot from
        // anywhere, and that gate is exactly what makes trusting a single voice safe.
        val repo = FakeSpotRepository()

        val outcome = SendSpotSignalUseCase(repo)("sp_1", accepted = false, distanceMeters = faraway)

        assertEquals(SpotVoteOutcome.IGNORED_TOO_FAR, outcome.getOrNull())
        assertEquals(0, repo.signalCallCount)
        assertTrue(repo.retractedSpotIds.isEmpty())
        assertTrue(repo.refreshedSpotIds.isEmpty())
    }

    @Test
    fun should_writeNothingAtAll_when_thereIsNoLocationFix() = runTest {
        val repo = FakeSpotRepository()

        val outcome = SendSpotSignalUseCase(repo)("sp_1", accepted = true, distanceMeters = null)

        assertEquals(SpotVoteOutcome.IGNORED_TOO_FAR, outcome.getOrNull())
        assertEquals(0, repo.signalCallCount)
        assertTrue(repo.refreshedSpotIds.isEmpty())
    }

    @Test
    fun should_failWithoutRetracting_when_theSignalWriteFails() = runTest {
        // The consequence must not be applied on top of a signal that never landed.
        val repo = FakeSpotRepository().apply {
            signalResult = Result.failure(IllegalStateException("offline"))
        }

        val outcome = SendSpotSignalUseCase(repo)("sp_1", accepted = false, distanceMeters = nearby)

        assertTrue(outcome.isFailure)
        assertTrue(repo.retractedSpotIds.isEmpty())
    }

    @Test
    fun should_reportFailure_when_theRetractionItselfFails() = runTest {
        val repo = FakeSpotRepository().apply {
            retractResult = Result.failure(IllegalStateException("no network"))
        }

        val outcome = SendSpotSignalUseCase(repo)("sp_1", accepted = false, distanceMeters = nearby)

        assertTrue(outcome.isFailure)
    }
}

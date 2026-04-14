package io.apptolast.paparcar.domain.usecase.spot

import io.apptolast.paparcar.fakes.FakeSpotRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SendSpotSignalUseCaseTest {

    private val repository = FakeSpotRepository()
    private val useCase = SendSpotSignalUseCase(repository)

    @Test
    fun should_returnSuccess_when_repositorySucceeds() = runTest {
        val result = useCase("spot-1", accepted = true)
        assertTrue(result.isSuccess)
    }

    @Test
    fun should_delegateAcceptSignal_to_repository() = runTest {
        useCase("spot-42", accepted = true)
        assertEquals(1, repository.signalCallCount)
        assertEquals(true, repository.lastSignalAccepted)
    }

    @Test
    fun should_delegateRejectSignal_to_repository() = runTest {
        useCase("spot-42", accepted = false)
        assertEquals(1, repository.signalCallCount)
        assertEquals(false, repository.lastSignalAccepted)
    }

    @Test
    fun should_returnFailure_when_repositoryFails() = runTest {
        repository.signalResult = Result.failure(RuntimeException("network error"))
        val result = useCase("spot-1", accepted = true)
        assertTrue(result.isFailure)
    }
}

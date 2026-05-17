package io.apptolast.paparcar.domain.usecase.user

import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeUserProfileRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GetOrCreateUserProfileUseCaseTest {

    private val session = FakeAuthRepository.authenticatedSession()
    private val fakeAuth = FakeAuthRepository(initialSession = session)
    private val fakeProfileRepo = FakeUserProfileRepository()

    private val useCase = GetOrCreateUserProfileUseCase(
        userProfileRepository = fakeProfileRepo,
        authRepository = fakeAuth,
    )

    // ── Auth session ──────────────────────────────────────────────────────────

    @Test
    fun `fails with error when no authenticated session`() = runTest {
        val noAuthCase = GetOrCreateUserProfileUseCase(
            userProfileRepository = fakeProfileRepo,
            authRepository = FakeAuthRepository(initialSession = null),
        )

        val result = noAuthCase()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("no authenticated session") == true)
    }

    // ── Profile retrieval ─────────────────────────────────────────────────────

    @Test
    fun `returns profile on success`() = runTest {
        val result = useCase()

        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
        assertEquals(session.userId, result.getOrNull()?.userId)
    }

    @Test
    fun `calls getOrCreateProfile exactly once`() = runTest {
        useCase()

        assertEquals(1, fakeProfileRepo.getOrCreateCallCount)
    }

    @Test
    fun `propagates failure when getOrCreateProfile fails`() = runTest {
        fakeProfileRepo.getOrCreateResult = Result.failure(RuntimeException("Firestore down"))

        val result = useCase()

        assertTrue(result.isFailure)
    }
}

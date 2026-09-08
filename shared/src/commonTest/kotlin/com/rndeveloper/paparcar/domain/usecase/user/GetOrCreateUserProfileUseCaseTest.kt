package com.rndeveloper.paparcar.domain.usecase.user

import com.apptolast.baselogin.domain.model.AuthResult
import com.rndeveloper.paparcar.fakes.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.FakeUserProfileRepository
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

    // ── Freshness: the profile sees the freshest session available ────────────
    // [PROFILE-PHOTO-STUCK-AND-STALE-001]

    @Test
    fun `uses the refreshed session when refreshSession succeeds`() = runTest {
        fakeAuth.refreshSessionResult =
            AuthResult.Success(session.copy(photoUrl = "https://new/photo.jpg"))

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals("https://new/photo.jpg", fakeProfileRepo.lastSession?.photoUrl)
    }

    @Test
    fun `falls back to the cached session when refreshSession fails`() = runTest {
        // The fake's default refreshSessionResult is a Failure — offline, expired, whatever.
        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(1, fakeAuth.refreshSessionCallCount)
        assertEquals(session.userId, fakeProfileRepo.lastSession?.userId)
    }

    @Test
    fun `does not attempt a refresh when there is no session at all`() = runTest {
        val noAuth = FakeAuthRepository(initialSession = null)
        val noAuthCase = GetOrCreateUserProfileUseCase(
            userProfileRepository = fakeProfileRepo,
            authRepository = noAuth,
        )

        noAuthCase()

        assertEquals(0, noAuth.refreshSessionCallCount)
    }
}

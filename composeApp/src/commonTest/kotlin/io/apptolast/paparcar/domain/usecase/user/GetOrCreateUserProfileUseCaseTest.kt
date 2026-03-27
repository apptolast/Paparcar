package io.apptolast.paparcar.domain.usecase.user

import com.apptolast.customlogin.domain.model.AuthState
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
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
    private val fakeParkingRepo = FakeUserParkingRepository()

    private val useCase = GetOrCreateUserProfileUseCase(
        userProfileRepository = fakeProfileRepo,
        userParkingRepository = fakeParkingRepo,
        authRepository = fakeAuth,
    )

    // ── Auth session ──────────────────────────────────────────────────────────

    @Test
    fun `fails with error when no authenticated session`() = runTest {
        val noAuthCase = GetOrCreateUserProfileUseCase(
            userProfileRepository = fakeProfileRepo,
            userParkingRepository = fakeParkingRepo,
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

    // ── Parking history sync ──────────────────────────────────────────────────

    @Test
    fun `triggers parking history sync after successful profile retrieval`() = runTest {
        useCase()

        assertEquals(1, fakeParkingRepo.syncCallCount)
    }

    @Test
    fun `still returns profile even when parking history sync fails`() = runTest {
        fakeParkingRepo.syncResult = Result.failure(RuntimeException("network error"))

        val result = useCase()

        // sync failure is non-fatal — profile is still returned
        assertTrue(result.isSuccess)
        assertEquals(1, fakeParkingRepo.syncCallCount)
    }

    @Test
    fun `triggers sync even when Room already has sessions`() = runTest {
        // Pre-populate the fake repo to simulate an existing device with local data
        repeat(3) { i ->
            fakeParkingRepo.saveSession(
                io.apptolast.paparcar.domain.model.UserParking(
                    id = "existing-$i",
                    userId = session.userId,
                    location = io.apptolast.paparcar.domain.model.GpsPoint(0.0, 0.0, 0f, 0L, 0f),
                )
            )
        }

        useCase()

        // Sync must always run — covers multi-device and re-login scenarios
        assertEquals(1, fakeParkingRepo.syncCallCount)
    }

    @Test
    fun `does not trigger sync when profile retrieval fails`() = runTest {
        fakeProfileRepo.getOrCreateResult = Result.failure(RuntimeException("Firestore down"))

        useCase()

        assertEquals(0, fakeParkingRepo.syncCallCount)
    }
}

package io.apptolast.paparcar.domain.usecase.user

import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeSpotRepository
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import io.apptolast.paparcar.fakes.FakeUserProfileRepository
import io.apptolast.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeleteAccountUseCaseTest {

    private val session = FakeAuthRepository.authenticatedSession()

    private fun buildDeps(withSession: Boolean = true) = object {
        val auth = FakeAuthRepository(initialSession = if (withSession) session else null)
        val parking = FakeUserParkingRepository()
        val vehicles = FakeVehicleRepository()
        val profile = FakeUserProfileRepository()
        val spots = FakeSpotRepository()
        val useCase get() = DeleteAccountUseCase(auth, parking, vehicles, profile, spots)
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `should_returnSuccess_when_allDeletionsSucceed`() = runTest {
        val d = buildDeps()
        val result = d.useCase()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should_callAllRepos_when_allDeletionsSucceed`() = runTest {
        val d = buildDeps()
        d.useCase()
        assertEquals(1, d.parking.deleteAllDataCallCount)
        assertEquals(1, d.vehicles.deleteAllDataCallCount)
        assertEquals(1, d.spots.clearCacheCallCount)
        assertEquals(1, d.profile.deleteAllDataCallCount)
        assertEquals(1, d.auth.deleteAccountCallCount)
    }

    // ── No active session ─────────────────────────────────────────────────────

    @Test
    fun `should_returnFailure_when_noActiveSession`() = runTest {
        val d = buildDeps(withSession = false)
        val result = d.useCase()
        assertTrue(result.isFailure)
    }

    @Test
    fun `should_notCallAnyRepo_when_noActiveSession`() = runTest {
        val d = buildDeps(withSession = false)
        d.useCase()
        assertEquals(0, d.parking.deleteAllDataCallCount)
        assertEquals(0, d.vehicles.deleteAllDataCallCount)
        assertEquals(0, d.spots.clearCacheCallCount)
        assertEquals(0, d.profile.deleteAllDataCallCount)
        assertEquals(0, d.auth.deleteAccountCallCount)
    }

    // ── Abort chain on parking failure ────────────────────────────────────────

    @Test
    fun `should_returnFailure_when_parkingDeletionFails`() = runTest {
        val d = buildDeps()
        d.parking.deleteAllDataResult = Result.failure(Exception("db error"))
        assertTrue(d.useCase().isFailure)
    }

    @Test
    fun `should_notCallAuthDelete_when_parkingDeletionFails`() = runTest {
        val d = buildDeps()
        d.parking.deleteAllDataResult = Result.failure(Exception("db error"))
        d.useCase()
        assertEquals(0, d.auth.deleteAccountCallCount)
    }

    // ── Abort chain on vehicle failure ────────────────────────────────────────

    @Test
    fun `should_returnFailure_when_vehicleDeletionFails`() = runTest {
        val d = buildDeps()
        d.vehicles.deleteAllDataResult = Result.failure(Exception("db error"))
        assertTrue(d.useCase().isFailure)
    }

    @Test
    fun `should_notCallAuthDelete_when_vehicleDeletionFails`() = runTest {
        val d = buildDeps()
        d.vehicles.deleteAllDataResult = Result.failure(Exception("db error"))
        d.useCase()
        assertEquals(0, d.auth.deleteAccountCallCount)
    }

    // ── Abort chain on profile failure ────────────────────────────────────────

    @Test
    fun `should_returnFailure_when_profileDeletionFails`() = runTest {
        val d = buildDeps()
        d.profile.deleteAllDataResult = Result.failure(Exception("firestore error"))
        assertTrue(d.useCase().isFailure)
    }

    @Test
    fun `should_notCallAuthDelete_when_profileDeletionFails`() = runTest {
        val d = buildDeps()
        d.profile.deleteAllDataResult = Result.failure(Exception("firestore error"))
        d.useCase()
        assertEquals(0, d.auth.deleteAccountCallCount)
    }

    // ── Auth deletion failure ─────────────────────────────────────────────────

    @Test
    fun `should_returnFailure_when_authDeleteFails`() = runTest {
        val d = buildDeps()
        d.auth.deleteAccountResult = Result.failure(Exception("network error"))
        assertTrue(d.useCase().isFailure)
    }

    @Test
    fun `should_haveCalledAllRepos_when_authDeleteFails`() = runTest {
        val d = buildDeps()
        d.auth.deleteAccountResult = Result.failure(Exception("network error"))
        d.useCase()
        assertEquals(1, d.parking.deleteAllDataCallCount)
        assertEquals(1, d.vehicles.deleteAllDataCallCount)
        assertEquals(1, d.spots.clearCacheCallCount)
        assertEquals(1, d.profile.deleteAllDataCallCount)
    }
}

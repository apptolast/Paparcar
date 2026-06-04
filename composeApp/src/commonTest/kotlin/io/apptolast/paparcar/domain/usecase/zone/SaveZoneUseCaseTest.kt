@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.zone

import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.model.ZoneIcon
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeZoneRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class SaveZoneUseCaseTest {

    private val session = FakeAuthRepository.authenticatedSession(userId = "user-99")

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `should return success with zone when authenticated`() = runTest {
        val useCase = buildUseCase()

        val result = useCase(name = "Casa", lat = 40.41, lon = -3.70)

        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
    }

    @Test
    fun `should persist zone in repository`() = runTest {
        val repo = FakeZoneRepository()
        val useCase = buildUseCase(repo = repo)

        useCase(name = "Trabajo", lat = 40.42, lon = -3.71)

        assertNotNull(repo.savedZone)
    }

    @Test
    fun `should assign authenticated userId to zone`() = runTest {
        val repo = FakeZoneRepository()
        val useCase = buildUseCase(repo = repo)

        useCase(name = "Gym", lat = 40.43, lon = -3.72)

        assertEquals(session.userId, repo.savedZone!!.userId)
    }

    @Test
    fun `should generate a non-blank zone id`() = runTest {
        val repo = FakeZoneRepository()
        val useCase = buildUseCase(repo = repo)

        useCase(name = "Casa", lat = 40.41, lon = -3.70)

        assertTrue(repo.savedZone!!.id.isNotBlank())
    }

    @Test
    fun `should generate unique ids for consecutive saves`() = runTest {
        val repo = FakeZoneRepository()
        val useCase = buildUseCase(repo = repo)

        useCase(name = "Zone A", lat = 40.41, lon = -3.70)
        val idA = repo.savedZone!!.id
        useCase(name = "Zone B", lat = 40.42, lon = -3.71)
        val idB = repo.savedZone!!.id

        assertTrue(idA != idB)
    }

    @Test
    fun `should trim whitespace from name`() = runTest {
        val repo = FakeZoneRepository()
        val useCase = buildUseCase(repo = repo)

        useCase(name = "  Casa  ", lat = 40.41, lon = -3.70)

        assertEquals("Casa", repo.savedZone!!.name)
    }

    @Test
    fun `should use default iconKey when not specified`() = runTest {
        val repo = FakeZoneRepository()
        val useCase = buildUseCase(repo = repo)

        useCase(name = "X", lat = 40.41, lon = -3.70)

        assertEquals(ZoneIcon.DEFAULT, repo.savedZone!!.iconKey)
    }

    @Test
    fun `should use provided iconKey when specified`() = runTest {
        val repo = FakeZoneRepository()
        val useCase = buildUseCase(repo = repo)

        useCase(name = "Trabajo", lat = 40.41, lon = -3.70, iconKey = "WORK")

        assertEquals("WORK", repo.savedZone!!.iconKey)
    }

    @Test
    fun `should use default radius when not specified`() = runTest {
        val repo = FakeZoneRepository()
        val useCase = buildUseCase(repo = repo)

        useCase(name = "X", lat = 40.41, lon = -3.70)

        assertEquals(Zone.DEFAULT_RADIUS_METERS, repo.savedZone!!.radiusMeters)
    }

    @Test
    fun `should clamp radius below MIN to MIN_RADIUS_METERS`() = runTest {
        val repo = FakeZoneRepository()
        val useCase = buildUseCase(repo = repo)

        useCase(name = "X", lat = 40.41, lon = -3.70, radiusMeters = 10f)

        assertEquals(Zone.MIN_RADIUS_METERS, repo.savedZone!!.radiusMeters)
    }

    @Test
    fun `should clamp radius above MAX to MAX_RADIUS_METERS`() = runTest {
        val repo = FakeZoneRepository()
        val useCase = buildUseCase(repo = repo)

        useCase(name = "X", lat = 40.41, lon = -3.70, radiusMeters = 9_999f)

        assertEquals(Zone.MAX_RADIUS_METERS, repo.savedZone!!.radiusMeters)
    }

    @Test
    fun `should accept a valid radius within bounds`() = runTest {
        val repo = FakeZoneRepository()
        val useCase = buildUseCase(repo = repo)

        useCase(name = "X", lat = 40.41, lon = -3.70, radiusMeters = 200f)

        assertEquals(200f, repo.savedZone!!.radiusMeters)
    }

    @Test
    fun `should set isPrivate to false by default`() = runTest {
        val repo = FakeZoneRepository()
        val useCase = buildUseCase(repo = repo)

        useCase(name = "X", lat = 40.41, lon = -3.70)

        assertFalse(repo.savedZone!!.isPrivate)
    }

    @Test
    fun `should set isPrivate to true when specified`() = runTest {
        val repo = FakeZoneRepository()
        val useCase = buildUseCase(repo = repo)

        useCase(name = "Garaje", lat = 40.41, lon = -3.70, isPrivate = true)

        assertTrue(repo.savedZone!!.isPrivate)
    }

    // ── Auth failure ──────────────────────────────────────────────────────────

    @Test
    fun `should return NotAuthenticated failure when no session`() = runTest {
        val useCase = buildUseCase(auth = FakeAuthRepository(initialSession = null))

        val result = useCase(name = "Casa", lat = 40.41, lon = -3.70)

        assertTrue(result.isFailure)
        assertIs<PaparcarError.Auth.NotAuthenticated>(result.exceptionOrNull())
    }

    @Test
    fun `should not save zone when not authenticated`() = runTest {
        val repo = FakeZoneRepository()
        val useCase = buildUseCase(repo = repo, auth = FakeAuthRepository(initialSession = null))

        useCase(name = "Casa", lat = 40.41, lon = -3.70)

        assertTrue(repo.zones.isEmpty())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildUseCase(
        repo: FakeZoneRepository = FakeZoneRepository(),
        auth: FakeAuthRepository = FakeAuthRepository(initialSession = session),
    ) = SaveZoneUseCase(repository = repo, authRepository = auth)
}

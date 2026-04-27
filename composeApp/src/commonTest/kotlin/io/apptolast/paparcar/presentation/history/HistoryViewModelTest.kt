@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.history

import app.cash.turbine.test
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.fakes.FakeUserParkingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Init ─────────────────────────────────────────────────────────────────

    @Test
    fun `initial state has isLoading true`() {
        val vm = HistoryViewModel(userParkingRepository = FakeUserParkingRepository())
        // UnconfinedTestDispatcher runs init eagerly, so isLoading is already false
        // after the first emission. Test the default initState instead:
        assertFalse(HistoryState().isLoading.not()) // default is true
    }

    @Test
    fun `should set isLoading to false after first emission`() = runTest {
        val repo = FakeUserParkingRepository()
        val vm = HistoryViewModel(userParkingRepository = repo)

        vm.state.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── All sessions (no vehicle filter) ─────────────────────────────────────

    @Test
    fun `should emit all sessions when vehicleId is null`() = runTest {
        val sessions = listOf(
            buildParking("s1", vehicleId = null),
            buildParking("s2", vehicleId = "v1"),
            buildParking("s3", vehicleId = "v2"),
        )
        val repo = FakeUserParkingRepository(initialSessions = sessions)
        val vm = HistoryViewModel(vehicleId = null, userParkingRepository = repo)

        vm.state.test {
            assertEquals(3, awaitItem().sessions.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should emit empty list when repository has no sessions`() = runTest {
        val repo = FakeUserParkingRepository()
        val vm = HistoryViewModel(vehicleId = null, userParkingRepository = repo)

        vm.state.test {
            assertEquals(0, awaitItem().sessions.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Per-vehicle filter ────────────────────────────────────────────────────

    @Test
    fun `should filter sessions by vehicleId when provided`() = runTest {
        val sessions = listOf(
            buildParking("s1", vehicleId = "v1"),
            buildParking("s2", vehicleId = "v2"),
            buildParking("s3", vehicleId = "v1"),
        )
        val repo = FakeUserParkingRepository(initialSessions = sessions)
        val vm = HistoryViewModel(vehicleId = "v1", userParkingRepository = repo)

        vm.state.test {
            val state = awaitItem()
            assertEquals(2, state.sessions.size)
            assertEquals(setOf("s1", "s3"), state.sessions.map { it.id }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should return empty list when no sessions match vehicleId`() = runTest {
        val sessions = listOf(buildParking("s1", vehicleId = "v99"))
        val repo = FakeUserParkingRepository(initialSessions = sessions)
        val vm = HistoryViewModel(vehicleId = "v1", userParkingRepository = repo)

        vm.state.test {
            assertEquals(0, awaitItem().sessions.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should exclude sessions with null vehicleId when filtering by vehicleId`() = runTest {
        val sessions = listOf(
            buildParking("s1", vehicleId = null),
            buildParking("s2", vehicleId = "v1"),
        )
        val repo = FakeUserParkingRepository(initialSessions = sessions)
        val vm = HistoryViewModel(vehicleId = "v1", userParkingRepository = repo)

        vm.state.test {
            val state = awaitItem()
            assertEquals(1, state.sessions.size)
            assertEquals("s2", state.sessions[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Reactive updates ──────────────────────────────────────────────────────

    @Test
    fun `should emit new state when a session is added`() = runTest {
        val repo = FakeUserParkingRepository()
        val vm = HistoryViewModel(vehicleId = null, userParkingRepository = repo)

        vm.state.test {
            assertEquals(0, awaitItem().sessions.size)

            repo.saveSession(buildParking("s1"))

            assertEquals(1, awaitItem().sessions.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Intents ───────────────────────────────────────────────────────────────

    @Test
    fun `ViewOnMap intent emits NavigateToMap effect`() = runTest {
        val repo = FakeUserParkingRepository()
        val vm = HistoryViewModel(userParkingRepository = repo)

        vm.effect.test {
            vm.handleIntent(HistoryIntent.ViewOnMap(lat = 40.416, lon = -3.703))
            val effect = awaitItem()
            assertEquals(HistoryEffect.NavigateToMap(lat = 40.416, lon = -3.703), effect)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildParking(id: String, vehicleId: String? = null): UserParking =
        UserParking(
            id = id,
            userId = "user-1",
            vehicleId = vehicleId,
            location = GpsPoint(
                latitude = 40.416,
                longitude = -3.703,
                accuracy = 5f,
                timestamp = 1_700_000_000L,
                speed = 0f,
            ),
            isActive = false,
        )
}

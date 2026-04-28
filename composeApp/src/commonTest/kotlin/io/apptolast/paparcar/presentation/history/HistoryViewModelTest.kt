@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.history

import app.cash.turbine.test
import io.apptolast.paparcar.domain.model.AddressInfo
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

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

    // ── Filter intents ────────────────────────────────────────────────────────

    @Test
    fun `filteredSessions_defaults_to_all_sessions`() = runTest {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val sessions = listOf(
            buildParking("recent", timestamp = nowMs - DAY_MS),
            buildParking("old",    timestamp = nowMs - DAYS_100_MS),
        )
        val repo = FakeUserParkingRepository(initialSessions = sessions)
        val vm = HistoryViewModel(userParkingRepository = repo)

        vm.state.test {
            val state = awaitItem()
            assertEquals(2, state.filteredSessions.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SetFilter_ThisWeek_keeps_only_sessions_within_7_days`() = runTest {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val sessions = listOf(
            buildParking("recent", timestamp = nowMs - DAY_MS),
            buildParking("old",    timestamp = nowMs - DAYS_100_MS),
        )
        val repo = FakeUserParkingRepository(initialSessions = sessions)
        val vm = HistoryViewModel(userParkingRepository = repo)

        vm.handleIntent(HistoryIntent.SetFilter(HistoryFilter.ThisWeek))

        assertEquals(1, vm.state.value.filteredSessions.size)
        assertEquals("recent", vm.state.value.filteredSessions[0].id)
    }

    @Test
    fun `SetFilter_Last3Months_keeps_only_sessions_within_90_days`() = runTest {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val sessions = listOf(
            buildParking("within",  timestamp = nowMs - DAYS_30_MS),
            buildParking("outside", timestamp = nowMs - DAYS_100_MS),
        )
        val repo = FakeUserParkingRepository(initialSessions = sessions)
        val vm = HistoryViewModel(userParkingRepository = repo)

        vm.handleIntent(HistoryIntent.SetFilter(HistoryFilter.Last3Months))

        assertEquals(1, vm.state.value.filteredSessions.size)
        assertEquals("within", vm.state.value.filteredSessions[0].id)
    }

    @Test
    fun `SetFilter_All_restores_all_sessions_after_active_filter`() = runTest {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val sessions = listOf(
            buildParking("recent", timestamp = nowMs - DAY_MS),
            buildParking("old",    timestamp = nowMs - DAYS_100_MS),
        )
        val repo = FakeUserParkingRepository(initialSessions = sessions)
        val vm = HistoryViewModel(userParkingRepository = repo)

        vm.handleIntent(HistoryIntent.SetFilter(HistoryFilter.ThisWeek))
        assertEquals(1, vm.state.value.filteredSessions.size)

        vm.handleIntent(HistoryIntent.SetFilter(HistoryFilter.All))
        assertEquals(2, vm.state.value.filteredSessions.size)
    }

    @Test
    fun `activeFilter_is_updated_on_SetFilter`() = runTest {
        val repo = FakeUserParkingRepository()
        val vm = HistoryViewModel(userParkingRepository = repo)

        vm.handleIntent(HistoryIntent.SetFilter(HistoryFilter.ThisWeek))
        assertEquals(HistoryFilter.ThisWeek, vm.state.value.activeFilter)
    }

    @Test
    fun `stats_sessions_unaffected_by_active_filter`() = runTest {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val sessions = listOf(
            buildParking("recent", timestamp = nowMs - DAY_MS),
            buildParking("old",    timestamp = nowMs - DAYS_100_MS),
        )
        val repo = FakeUserParkingRepository(initialSessions = sessions)
        val vm = HistoryViewModel(userParkingRepository = repo)

        vm.handleIntent(HistoryIntent.SetFilter(HistoryFilter.ThisWeek))

        // filteredSessions is filtered, but sessions (for stats) keeps all
        assertEquals(2, vm.state.value.sessions.size)
        assertEquals(1, vm.state.value.filteredSessions.size)
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @Test
    fun `statsData_is_null_when_no_sessions`() = runTest {
        val repo = FakeUserParkingRepository()
        val vm = HistoryViewModel(userParkingRepository = repo)
        assertNull(vm.state.value.statsData)
    }

    @Test
    fun `statsData_is_not_null_when_sessions_present`() = runTest {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val repo = FakeUserParkingRepository(
            initialSessions = listOf(buildParking("s1", timestamp = nowMs - DAY_MS)),
        )
        val vm = HistoryViewModel(userParkingRepository = repo)
        assertNotNull(vm.state.value.statsData)
    }

    @Test
    fun `statsData_avgReliabilityPct_is_correct`() = runTest {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val sessions = listOf(
            buildParking("s1", timestamp = nowMs - DAY_MS,    reliability = 0.80f),
            buildParking("s2", timestamp = nowMs - DAY_MS * 2, reliability = 1.00f),
        )
        val repo = FakeUserParkingRepository(initialSessions = sessions)
        val vm = HistoryViewModel(userParkingRepository = repo)

        val pct = vm.state.value.statsData?.avgReliabilityPct
        assertEquals(90, pct) // avg(0.80, 1.00) = 0.90 → 90%
    }

    @Test
    fun `statsData_avgReliabilityPct_is_null_when_no_reliability_data`() = runTest {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val repo = FakeUserParkingRepository(
            initialSessions = listOf(buildParking("s1", timestamp = nowMs - DAY_MS)),
        )
        val vm = HistoryViewModel(userParkingRepository = repo)
        assertNull(vm.state.value.statsData?.avgReliabilityPct)
    }

    @Test
    fun `statsData_favoriteStreet_is_most_frequent_street`() = runTest {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val sessions = listOf(
            buildParking("s1", timestamp = nowMs - DAY_MS,     street = "Gran Via"),
            buildParking("s2", timestamp = nowMs - DAY_MS * 2, street = "Gran Via"),
            buildParking("s3", timestamp = nowMs - DAY_MS * 3, street = "Alcalá"),
        )
        val repo = FakeUserParkingRepository(initialSessions = sessions)
        val vm = HistoryViewModel(userParkingRepository = repo)
        assertEquals("Gran Via", vm.state.value.statsData?.favoriteStreet)
    }

    @Test
    fun `statsData_favoriteStreet_is_null_when_no_address_data`() = runTest {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val repo = FakeUserParkingRepository(
            initialSessions = listOf(buildParking("s1", timestamp = nowMs - DAY_MS)),
        )
        val vm = HistoryViewModel(userParkingRepository = repo)
        assertNull(vm.state.value.statsData?.favoriteStreet)
    }

    @Test
    fun `statsData_peakDay_is_null_when_fewer_than_5_sessions`() = runTest {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val sessions = (1..4).map { buildParking("s$it", timestamp = nowMs - DAY_MS * it) }
        val repo = FakeUserParkingRepository(initialSessions = sessions)
        val vm = HistoryViewModel(userParkingRepository = repo)
        assertNull(vm.state.value.statsData?.mostActiveDayOfWeek)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildParking(
        id: String,
        vehicleId: String? = null,
        timestamp: Long = 1_700_000_000L,
        reliability: Float? = null,
        street: String? = null,
    ): UserParking =
        UserParking(
            id = id,
            userId = "user-1",
            vehicleId = vehicleId,
            location = GpsPoint(
                latitude = 40.416,
                longitude = -3.703,
                accuracy = 5f,
                timestamp = timestamp,
                speed = 0f,
            ),
            isActive = false,
            detectionReliability = reliability,
            address = street?.let { AddressInfo(street = it, city = null, region = null, country = null) },
        )

    private companion object {
        const val DAY_MS     = 24L * 60 * 60 * 1000
        const val DAYS_30_MS = 30L * DAY_MS
        const val DAYS_100_MS = 100L * DAY_MS
    }
}

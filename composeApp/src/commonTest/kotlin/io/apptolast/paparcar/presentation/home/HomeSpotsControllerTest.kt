@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.fakes.FakePermissionManager
import io.apptolast.paparcar.fakes.FakeSpotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [HomeSpotsController] — the nearby-spots subscription + query centre. Covers the
 * permission gate, the error path, and the pan/recenter + resubscribe-threshold logic that previously
 * had no dedicated coverage (only the happy-path spot fetch was exercised through the VM).
 *
 * Uses the eager [UnconfinedTestDispatcher] (no debounce/delay in this pipeline), mirroring
 * HomeViewModelTest, so permission + centre emissions propagate synchronously.
 */
class HomeSpotsControllerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var scope: CoroutineScope

    private lateinit var permissions: FakePermissionManager
    private lateinit var spotRepo: FakeSpotRepository
    private lateinit var observeNearbySpots: ObserveNearbySpotsUseCase

    private var lastSpots: List<Spot>? = null
    private var spotsCallCount = 0
    private var lastError: String? = null
    private var errorCallCount = 0

    private val center = GpsPoint(40.0, -3.0, 0f, 0L, 0f)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        scope = CoroutineScope(testDispatcher)
        permissions = FakePermissionManager()
        spotRepo = FakeSpotRepository()
        observeNearbySpots = ObserveNearbySpotsUseCase(spotRepo)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun buildController(): HomeSpotsController = HomeSpotsController(
        scope = scope,
        permissionManager = permissions,
        observeNearbySpots = observeNearbySpots,
        onSpots = { s -> lastSpots = s; spotsCallCount++ },
        onError = { m -> lastError = m; errorCallCount++ },
    ).also { it.start() }

    private fun spot(id: String) = Spot(id = id, location = center, reportedBy = "u1", address = null, placeInfo = null)

    // ── Permission gate + fetch ─────────────────────────────────────────────────

    @Test
    fun `should_emit_empty_spots_when_permissions_are_denied_even_with_a_centre`() = runTest {
        spotRepo.spots = listOf(spot("s1"))
        val controller = buildController()

        controller.updateQueryCenter(center)

        // No CORE permission → the centre is masked to null → an empty list flows down the same pipe.
        assertEquals(emptyList(), lastSpots)
    }

    @Test
    fun `should_emit_nearby_spots_when_permission_granted_and_centre_set`() = runTest {
        spotRepo.spots = listOf(spot("s1"), spot("s2"))
        val controller = buildController()

        permissions.emit(FakePermissionManager.allGranted())
        controller.updateQueryCenter(center)

        assertEquals(2, lastSpots?.size)
    }

    @Test
    fun `should_report_error_and_empty_spots_when_the_stream_fails`() = runTest {
        spotRepo.observeError = RuntimeException("boom")
        val controller = buildController()

        permissions.emit(FakePermissionManager.allGranted())
        controller.updateQueryCenter(center)

        assertEquals("boom", lastError)
        assertEquals(emptyList(), lastSpots)
    }

    // ── Pan / recenter threshold ────────────────────────────────────────────────

    @Test
    fun `should_not_recenter_on_pan_when_no_centre_is_seeded`() = runTest {
        val controller = buildController()

        assertFalse(controller.maybeRecenterOnPan(41.0, -4.0))
    }

    @Test
    fun `should_not_recenter_on_a_small_pan_within_the_threshold`() = runTest {
        val controller = buildController()
        controller.recenter(center)

        // ~50 m north of the centre — under the 300 m pan threshold.
        assertFalse(controller.maybeRecenterOnPan(40.00045, -3.0))
    }

    @Test
    fun `should_recenter_on_a_large_pan_beyond_the_threshold`() = runTest {
        val controller = buildController()
        controller.recenter(center)

        // ~1.1 km north — well beyond the 300 m pan threshold.
        assertTrue(controller.maybeRecenterOnPan(40.01, -3.0))
    }

    // ── Resubscribe threshold (distinctUntilChanged / closeEnoughTo) ────────────

    @Test
    fun `should_not_resubscribe_on_gps_drift_within_the_threshold_but_resubscribe_on_a_jump`() = runTest {
        spotRepo.spots = listOf(spot("s1"))
        val controller = buildController()
        permissions.emit(FakePermissionManager.allGranted())

        controller.updateQueryCenter(center)
        val callsAfterFirst = spotsCallCount

        // ~50 m drift — under SPOT_RESUBSCRIBE_THRESHOLD_METERS: deduped, no new emission.
        controller.updateQueryCenter(GpsPoint(40.00045, -3.0, 0f, 0L, 0f))
        assertEquals(callsAfterFirst, spotsCallCount)

        // ~1.1 km jump — beyond the threshold: resubscribes → one new emission.
        controller.updateQueryCenter(GpsPoint(40.01, -3.0, 0f, 0L, 0f))
        assertEquals(callsAfterFirst + 1, spotsCallCount)
    }
}

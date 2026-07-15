package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import io.apptolast.paparcar.fakes.FakeAddressAndPlaceRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Dedup contract of [HomeGeocodingController]: re-asking for (effectively) the
 * same point must not cancel its own in-flight answer — GPS jitter re-asks on
 * every fix, and a geocode slower than the fix interval used to restart
 * forever and never emit. [GEOCODE-DEADLINE-001]
 */
class HomeGeocodingControllerTest {

    private val repo = FakeAddressAndPlaceRepository()
    private val controller = HomeGeocodingController(GetAddressAndPlaceUseCase(repo), debounceMs = 600L)

    // ── User geocode ──────────────────────────────────────────────────────────

    @Test
    fun should_keep_the_inflight_geocode_when_the_same_point_is_reasked() = runTest {
        repo.delayMs = 2_000
        controller.attach(this)
        val updates = mutableListOf<GeocodeUpdate>()
        val collector = launch { controller.updates.collect { updates += it } }

        controller.geocodeUserLocation(36.60510, -6.27300)
        advanceTimeBy(1_000) // in flight
        controller.geocodeUserLocation(36.60513, -6.27302) // same ~11m cell — jitter
        advanceTimeBy(1_500) // original job crosses its 2s delay

        assertEquals(1, repo.calls.size)
        assertTrue(updates.any { it is GeocodeUpdate.UserAddress })
        collector.cancel()
    }

    @Test
    fun should_relaunch_when_a_different_point_is_asked() = runTest {
        repo.delayMs = 2_000
        controller.attach(this)

        controller.geocodeUserLocation(36.60510, -6.27300)
        advanceTimeBy(1_000)
        controller.geocodeUserLocation(36.61000, -6.27300) // ~550m away
        advanceUntilIdle()

        assertEquals(2, repo.calls.size)
        assertEquals(36.61000 to -6.27300, repo.calls.last())
    }

    @Test
    fun should_relaunch_same_point_when_previous_job_already_finished() = runTest {
        controller.attach(this)

        controller.geocodeUserLocation(36.60510, -6.27300)
        advanceUntilIdle() // completes (no cache in the fake — a retry must be allowed)
        controller.geocodeUserLocation(36.60510, -6.27300)
        advanceUntilIdle()

        assertEquals(2, repo.calls.size)
    }

    // ── Camera geocode ────────────────────────────────────────────────────────

    @Test
    fun should_dedup_camera_reasks_for_the_same_point_while_pending_or_inflight() = runTest {
        repo.delayMs = 2_000
        controller.attach(this)

        controller.geocodeCameraLocation(36.60510, -6.27300)
        advanceTimeBy(300) // still within debounce
        controller.geocodeCameraLocation(36.60511, -6.27301) // same cell — must not reset the debounce
        advanceTimeBy(400) // debounce (600ms from the FIRST ask) fires
        controller.geocodeCameraLocation(36.60512, -6.27302) // same cell — in flight now
        advanceUntilIdle()

        assertEquals(1, repo.calls.size)
    }

    @Test
    fun should_geocode_the_new_point_when_the_camera_moves_mid_debounce() = runTest {
        controller.attach(this)

        controller.geocodeCameraLocation(36.60510, -6.27300)
        advanceTimeBy(300)
        controller.geocodeCameraLocation(36.61000, -6.27300) // real pan — restarts the debounce
        advanceUntilIdle()

        assertEquals(1, repo.calls.size)
        assertEquals(36.61000 to -6.27300, repo.calls.single())
    }
}

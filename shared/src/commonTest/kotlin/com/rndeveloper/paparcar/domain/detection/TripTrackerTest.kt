@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.fakes.FakeDrivingRouteStore
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TripTrackerTest {

    private val primary = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 16)
    private val passive = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 16)

    /** 0.01° of latitude ≈ 1.1 km — every emission clears the store's 12 m decimation. */
    private fun p(lat: Double, speed: Float = 0f) = GpsPoint(lat, -3.0, 5f, 0L, speed)

    @Test
    fun `should feed the store from both taps`() = runTest {
        val store = FakeDrivingRouteStore()
        val tracker = TripTracker(store) { currentTime }
        val job = launch { tracker.track(primary, passive) }
        runCurrent()

        primary.emit(p(40.00))
        passive.emit(p(40.01))
        runCurrent()

        assertEquals(2, store.points().size, "both the primary and the passive tap must land points")
        job.cancelAndJoin()
    }

    @Test
    fun `should shut off as a null trip when nothing drives inside the window`() = runTest {
        val store = FakeDrivingRouteStore()
        val tracker = TripTracker(store) { currentTime }
        val job = launch { tracker.track(primary, passive) }
        runCurrent()

        primary.emit(p(40.00, speed = 1f)) // a fix, but nobody is driving anywhere
        advanceTimeBy(BtTripShutoff.NULL_TRIP_WINDOW_MS + 61_000L)
        runCurrent()

        assertTrue(job.isCompleted, "the garage-radio session must shut itself off")
    }

    @Test
    fun `should shut off even when no fix at all arrives - an underground garage is not an alibi`() = runTest {
        val tracker = TripTracker(FakeDrivingRouteStore()) { currentTime }
        val job = launch { tracker.track(primary, passive) }
        runCurrent()

        advanceTimeBy(BtTripShutoff.NULL_TRIP_WINDOW_MS + 61_000L)
        runCurrent()

        assertTrue(job.isCompleted, "silence must not keep the recorder alive")
    }

    @Test
    fun `should keep recording past the window when a driving-speed fix was seen`() = runTest {
        val store = FakeDrivingRouteStore()
        val tracker = TripTracker(store) { currentTime }
        val job = launch { tracker.track(primary, passive) }
        runCurrent()

        primary.emit(p(40.00, speed = 6f)) // driving — the null-trip question is settled
        advanceTimeBy(BtTripShutoff.NULL_TRIP_WINDOW_MS * 3)
        runCurrent()

        assertFalse(job.isCompleted, "a real ride records until the disconnect cancels it")
        primary.emit(p(40.01, speed = 6f))
        runCurrent()
        assertEquals(2, store.points().size, "still recording after the window")
        job.cancelAndJoin()
    }

    @Test
    fun `should keep recording when displacement cleared the radius even at crawling speed`() = runTest {
        val tracker = TripTracker(FakeDrivingRouteStore()) { currentTime }
        val job = launch { tracker.track(primary, passive) }
        runCurrent()

        primary.emit(p(40.000, speed = 1f))
        primary.emit(p(40.002, speed = 1f)) // ~220 m from the first fix — movement measured by position
        advanceTimeBy(BtTripShutoff.NULL_TRIP_WINDOW_MS * 2)
        runCurrent()

        assertFalse(job.isCompleted, "a slow crawl that MOVED is a trip, whatever the speed field says")
        job.cancelAndJoin()
    }
}

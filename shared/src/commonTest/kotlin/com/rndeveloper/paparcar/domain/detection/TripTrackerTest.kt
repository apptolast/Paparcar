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

    /** La cadencia del watchdog de shutoff: el veredicto se mira una vez por minuto. */
    private val SHUTOFF_TICK = 60_000L

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
    fun `should announce a proven ride once, and never for a garage radio`() = runTest {
        // [SHARED-A-BT-CONNECT-PROVES-WHO-TOOK-THE-CAR-001] Es lo que le dice al grupo que me llevo
        // el coche. Dispararlo en un null trip pondría en el espejo que un coche aparcado se ha
        // movido — y dejaría a todo el grupo sin la valla del sitio donde SIGUE estando.
        var proven = 0
        val tracker = TripTracker(FakeDrivingRouteStore()) { currentTime }
        val job = launch { tracker.track(primary, passive) { proven++ } }
        runCurrent()

        primary.emit(p(40.00, speed = 1f)) // la radio del garaje: un fix, nadie conduce
        advanceTimeBy(SHUTOFF_TICK * 3)
        runCurrent()
        assertEquals(0, proven, "un null trip no puede anunciar nada")

        primary.emit(p(40.00, speed = 6f))
        advanceTimeBy(SHUTOFF_TICK * 3)
        runCurrent()
        assertEquals(1, proven, "una sola vez, aunque el viaje siga horas")

        job.cancelAndJoin()
    }

    @Test
    fun `should announce a crawling ride the speed field never reported`() = runTest {
        // El campo `speed` miente BAJO: un viaje medido solo por posición es igual de viaje, y sin
        // esto el aviso se perdería justo en los móviles cuyo GPS no declara velocidad.
        var proven = 0
        val tracker = TripTracker(FakeDrivingRouteStore()) { currentTime }
        val job = launch { tracker.track(primary, passive) { proven++ } }
        runCurrent()

        primary.emit(p(40.000, speed = 1f))
        primary.emit(p(40.002, speed = 1f)) // ~220 m: por encima del radio del null trip
        advanceTimeBy(SHUTOFF_TICK * 2)
        runCurrent()

        assertEquals(1, proven, "el desplazamiento medido también prueba el viaje")
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

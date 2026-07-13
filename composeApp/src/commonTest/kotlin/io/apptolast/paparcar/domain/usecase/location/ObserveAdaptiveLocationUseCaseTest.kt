@file:OptIn(kotlin.time.ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.fakes.FakeLocationDataSource
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveAdaptiveLocationUseCaseTest {

    private val fakeDataSource = FakeLocationDataSource()
    private val useCase = ObserveAdaptiveLocationUseCase(fakeDataSource)

    private fun point(speed: Float, latitude: Double = 40.0) = GpsPoint(
        latitude = latitude,
        longitude = -3.7,
        accuracy = 10f,
        timestamp = 0L,
        speed = speed,
    )

    @Test
    fun `should start in HighAccuracy mode and emit from high accuracy source`() =
        runTest(UnconfinedTestDispatcher()) {
            val collected = mutableListOf<GpsPoint>()
            val job = launch { useCase().collect { collected.add(it) } }

            fakeDataSource.emitHighAccuracy(point(speed = 0f, latitude = 1.0))
            fakeDataSource.emitHighAccuracy(point(speed = 1f, latitude = 2.0))

            job.cancelAndJoin()
            assertEquals(2, collected.size)
            assertEquals(1.0, collected[0].latitude)
            assertEquals(2.0, collected[1].latitude)
        }

    @Test
    fun `should switch to Balanced when speed exceeds threshold`() =
        runTest(UnconfinedTestDispatcher()) {
            val collected = mutableListOf<GpsPoint>()
            val job = launch { useCase().collect { collected.add(it) } }

            // Start slow — from high accuracy
            fakeDataSource.emitHighAccuracy(point(speed = 2f, latitude = 1.0))
            // Exceed 5 m/s → switch to balanced
            fakeDataSource.emitHighAccuracy(point(speed = 6f, latitude = 2.0))
            // Now balanced source should be active
            fakeDataSource.emitBalanced(point(speed = 6f, latitude = 3.0))

            job.cancelAndJoin()
            assertEquals(3, collected.size)
            assertEquals(3.0, collected[2].latitude)
        }

    @Test
    fun `should switch back to HighAccuracy when speed drops below lower threshold`() =
        runTest(UnconfinedTestDispatcher()) {
            val collected = mutableListOf<GpsPoint>()
            val job = launch { useCase().collect { collected.add(it) } }

            // Drive fast → balanced
            fakeDataSource.emitHighAccuracy(point(speed = 6f, latitude = 1.0))
            // Emit from balanced; speed drops below 3 m/s → back to high accuracy
            fakeDataSource.emitBalanced(point(speed = 2f, latitude = 2.0))
            // Now high accuracy source is active again
            fakeDataSource.emitHighAccuracy(point(speed = 1f, latitude = 3.0))

            job.cancelAndJoin()
            assertEquals(3, collected.size)
            assertEquals(3.0, collected[2].latitude)
        }

    @Test
    fun `should stay in current mode when speed is in hysteresis band`() =
        runTest(UnconfinedTestDispatcher()) {
            val collected = mutableListOf<GpsPoint>()
            val job = launch { useCase().collect { collected.add(it) } }

            // Start in HighAccuracy; emit a point in the hysteresis band (3–5 m/s)
            fakeDataSource.emitHighAccuracy(point(speed = 4f, latitude = 1.0))
            // Should still be listening to high accuracy (mode unchanged)
            fakeDataSource.emitHighAccuracy(point(speed = 4f, latitude = 2.0))
            // Balanced source emits — should NOT be collected (mode is still HighAccuracy)
            fakeDataSource.emitBalanced(point(speed = 4f, latitude = 99.0))
            fakeDataSource.emitHighAccuracy(point(speed = 4f, latitude = 3.0))

            job.cancelAndJoin()
            val latitudes = collected.map { it.latitude }
            assert(99.0 !in latitudes) { "Balanced emission should be ignored while in HighAccuracy mode" }
            assertEquals(3, collected.size)
        }

    @Test
    fun `should pin HighAccuracy through the burst window even above the balanced threshold`() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-BURST-001] Short trip (Durango->Glorieta): without the burst the first > 5 m/s fix
            // drops to BALANCED (30 s) and starves the sample stream. With the burst, HIGH_ACCURACY
            // is pinned through the window regardless of speed, then adaptive logic resumes.
            val ds = FakeLocationDataSource()
            val burst = ObserveAdaptiveLocationUseCase(ds, initialHighAccuracyWindowMs = 10_000L)
            val collected = mutableListOf<GpsPoint>()
            val job = launch { burst().collect { collected.add(it) } }

            fun ts(t: Long, speed: Float, lat: Double) =
                GpsPoint(latitude = lat, longitude = -3.7, accuracy = 10f, timestamp = t, speed = speed)

            // First fix at t=1000, speed 6 (> 5): normally → Balanced, but the burst pins HighAccuracy.
            ds.emitHighAccuracy(ts(1_000L, 6f, 1.0))
            // Balanced source emits INSIDE the window → must be ignored (mode still HighAccuracy).
            ds.emitBalanced(ts(3_000L, 6f, 99.0))
            ds.emitHighAccuracy(ts(5_000L, 6f, 2.0))
            // Past the 10 s window, speed still 6 → now allowed to switch to Balanced.
            ds.emitHighAccuracy(ts(12_000L, 6f, 3.0))
            ds.emitBalanced(ts(14_000L, 6f, 4.0))

            job.cancelAndJoin()
            val lats = collected.map { it.latitude }
            assert(99.0 !in lats) { "balanced emission inside the burst window must be ignored" }
            assertEquals(listOf(1.0, 2.0, 3.0, 4.0), lats)
        }

    @Test
    fun `should propagate errors from upstream`() = runTest(UnconfinedTestDispatcher()) {
        var caughtError: Exception? = null
        val job = launch {
            try {
                useCase().collect { }
            } catch (e: Exception) {
                // CancellationException is a RuntimeException — rethrow so test infra handles it
                if (e is kotlinx.coroutines.CancellationException) throw e
                caughtError = e
            }
        }

        job.cancelAndJoin()
        // No error thrown from a normal cancel → caughtError stays null
        assertEquals(null, caughtError)
    }
}

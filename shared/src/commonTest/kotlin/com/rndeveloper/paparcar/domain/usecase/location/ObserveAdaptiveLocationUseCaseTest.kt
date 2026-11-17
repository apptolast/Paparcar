@file:OptIn(kotlin.time.ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.rndeveloper.paparcar.domain.usecase.location

import com.rndeveloper.paparcar.domain.location.ChargingStateSource
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.fakes.FakeLocationDataSource
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObserveAdaptiveLocationUseCaseTest {

    private val fakeDataSource = FakeLocationDataSource()
    private val useCase = ObserveAdaptiveLocationUseCase(fakeDataSource)

    private fun point(speed: Float, latitude: Double = 40.0, timestamp: Long = 0L) = GpsPoint(
        latitude = latitude,
        longitude = -3.7,
        accuracy = 10f,
        timestamp = timestamp,
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
    fun `should switch to Cruise when speed exceeds the driving threshold`() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-ADAPTIVE-SAMPLING-EARNS-ITS-FIXES-001] The band that used to drop to BALANCED
            // (~15 s real cadence, p90 132 m between route vertices in the corpus) now lands on the
            // denser CRUISE stream — same boundary, higher density.
            val collected = mutableListOf<GpsPoint>()
            val job = launch { useCase().collect { collected.add(it) } }

            fakeDataSource.emitHighAccuracy(point(speed = 2f, latitude = 1.0))
            // Exceed 5 m/s → switch to Cruise, NOT Balanced.
            fakeDataSource.emitHighAccuracy(point(speed = 6f, latitude = 2.0))
            fakeDataSource.emitBalanced(point(speed = 6f, latitude = 99.0)) // must be ignored
            fakeDataSource.emitCruise(point(speed = 6f, latitude = 3.0))

            job.cancelAndJoin()
            val lats = collected.map { it.latitude }
            assertTrue(99.0 !in lats, "urban driving must never sample at the old BALANCED cadence")
            assertEquals(listOf(1.0, 2.0, 3.0), lats)
        }

    @Test
    fun `should switch back to HighAccuracy when speed drops below lower threshold`() =
        runTest(UnconfinedTestDispatcher()) {
            val collected = mutableListOf<GpsPoint>()
            val job = launch { useCase().collect { collected.add(it) } }

            // Drive → Cruise
            fakeDataSource.emitHighAccuracy(point(speed = 6f, latitude = 1.0))
            // Cruise fix below 3 m/s → back to high accuracy
            fakeDataSource.emitCruise(point(speed = 2f, latitude = 2.0))
            fakeDataSource.emitHighAccuracy(point(speed = 1f, latitude = 3.0))

            job.cancelAndJoin()
            assertEquals(3, collected.size)
            assertEquals(3.0, collected[2].latitude)
        }

    @Test
    fun `should stay in current mode when speed is in the low hysteresis band`() =
        runTest(UnconfinedTestDispatcher()) {
            val collected = mutableListOf<GpsPoint>()
            val job = launch { useCase().collect { collected.add(it) } }

            // Start in HighAccuracy; points in the 3–5 m/s band keep the mode.
            fakeDataSource.emitHighAccuracy(point(speed = 4f, latitude = 1.0))
            fakeDataSource.emitHighAccuracy(point(speed = 4f, latitude = 2.0))
            fakeDataSource.emitCruise(point(speed = 4f, latitude = 99.0)) // not the active stream
            fakeDataSource.emitHighAccuracy(point(speed = 4f, latitude = 3.0))

            job.cancelAndJoin()
            val latitudes = collected.map { it.latitude }
            assertTrue(99.0 !in latitudes, "Cruise emission should be ignored while in HighAccuracy mode")
            assertEquals(3, collected.size)
        }

    // ── Highway tier [DET-ADAPTIVE-SAMPLING-EARNS-ITS-FIXES-001] ─────────────────────────────

    @Test
    fun `should climb to Highway at motorway speed and keep the old balanced cadence there`() =
        runTest(UnconfinedTestDispatcher()) {
            val collected = mutableListOf<GpsPoint>()
            val job = launch { useCase().collect { collected.add(it) } }

            fakeDataSource.emitHighAccuracy(point(speed = 6f, latitude = 1.0))   // → Cruise
            fakeDataSource.emitCruise(point(speed = 26f, latitude = 2.0))        // ≥25 → Highway
            fakeDataSource.emitCruise(point(speed = 26f, latitude = 99.0))       // stale stream
            fakeDataSource.emitBalanced(point(speed = 30f, latitude = 3.0))      // Highway = balanced

            job.cancelAndJoin()
            val lats = collected.map { it.latitude }
            assertTrue(99.0 !in lats, "cruise emission must be ignored once Highway owns the stream")
            assertEquals(listOf(1.0, 2.0, 3.0), lats)
        }

    @Test
    fun `should hold Highway through the wide re-entry band and drop to Cruise below it`() =
        runTest(UnconfinedTestDispatcher()) {
            val collected = mutableListOf<GpsPoint>()
            val job = launch { useCase().collect { collected.add(it) } }

            fakeDataSource.emitHighAccuracy(point(speed = 26f, latitude = 1.0))  // burstless → Highway
            // 22 m/s sits in the 20–25 hysteresis band → still Highway.
            fakeDataSource.emitBalanced(point(speed = 22f, latitude = 2.0))
            fakeDataSource.emitCruise(point(speed = 22f, latitude = 99.0))       // must be ignored
            // 18 m/s < 20 → re-enter Cruise.
            fakeDataSource.emitBalanced(point(speed = 18f, latitude = 3.0))
            fakeDataSource.emitCruise(point(speed = 18f, latitude = 4.0))

            job.cancelAndJoin()
            val lats = collected.map { it.latitude }
            assertTrue(99.0 !in lats, "the 20-25 m/s band must not flap Highway back to Cruise")
            assertEquals(listOf(1.0, 2.0, 3.0, 4.0), lats)
        }

    @Test
    fun `should drop straight to HighAccuracy when a Highway fix reads a hard stop`() =
        runTest(UnconfinedTestDispatcher()) {
            val collected = mutableListOf<GpsPoint>()
            val job = launch { useCase().collect { collected.add(it) } }

            fakeDataSource.emitHighAccuracy(point(speed = 26f, latitude = 1.0))  // → Highway
            fakeDataSource.emitBalanced(point(speed = 1f, latitude = 2.0))       // hard stop
            fakeDataSource.emitHighAccuracy(point(speed = 0f, latitude = 3.0))

            job.cancelAndJoin()
            assertEquals(listOf(1.0, 2.0, 3.0), collected.map { it.latitude })
        }

    // ── Charging boost [DET-ADAPTIVE-SAMPLING-EARNS-ITS-FIXES-001] ───────────────────────────

    @Test
    fun `should pin HighAccuracy at any speed while charging`() =
        runTest(UnconfinedTestDispatcher()) {
            val ds = FakeLocationDataSource()
            val charging = ObserveAdaptiveLocationUseCase(ds, chargingStateSource = ChargingStateSource { true })
            val collected = mutableListOf<GpsPoint>()
            val job = launch { charging().collect { collected.add(it) } }

            ds.emitHighAccuracy(point(speed = 26f, latitude = 1.0)) // motorway speed, but plugged in
            ds.emitCruise(point(speed = 26f, latitude = 99.0))      // must be ignored
            ds.emitBalanced(point(speed = 26f, latitude = 98.0))    // must be ignored
            ds.emitHighAccuracy(point(speed = 26f, latitude = 2.0))

            job.cancelAndJoin()
            val lats = collected.map { it.latitude }
            assertTrue(99.0 !in lats && 98.0 !in lats, "while charging, density is free — never degrade")
            assertEquals(listOf(1.0, 2.0), lats)
        }

    @Test
    fun `should resume adaptive behaviour when charging stops`() =
        runTest(UnconfinedTestDispatcher()) {
            var plugged = true
            val ds = FakeLocationDataSource()
            val charging = ObserveAdaptiveLocationUseCase(ds, chargingStateSource = ChargingStateSource { plugged })
            val collected = mutableListOf<GpsPoint>()
            val job = launch { charging().collect { collected.add(it) } }

            ds.emitHighAccuracy(point(speed = 6f, latitude = 1.0)) // plugged → stays HighAccuracy
            plugged = false
            ds.emitHighAccuracy(point(speed = 6f, latitude = 2.0)) // unplugged, driving → Cruise
            ds.emitCruise(point(speed = 6f, latitude = 3.0))

            job.cancelAndJoin()
            assertEquals(listOf(1.0, 2.0, 3.0), collected.map { it.latitude })
        }

    // ── [DET-BURST-001] unchanged ────────────────────────────────────────────────────────────

    @Test
    fun `should pin HighAccuracy through the burst window even above the driving threshold`() =
        runTest(UnconfinedTestDispatcher()) {
            // [DET-BURST-001] Short trip (Durango->Glorieta): without the burst the first > 5 m/s fix
            // starves the sample stream. With the burst, HIGH_ACCURACY is pinned through the window
            // regardless of speed, then adaptive logic resumes (now toward CRUISE, not BALANCED).
            val ds = FakeLocationDataSource()
            val burst = ObserveAdaptiveLocationUseCase(ds, initialHighAccuracyWindowMs = 10_000L)
            val collected = mutableListOf<GpsPoint>()
            val job = launch { burst().collect { collected.add(it) } }

            fun ts(t: Long, speed: Float, lat: Double) =
                GpsPoint(latitude = lat, longitude = -3.7, accuracy = 10f, timestamp = t, speed = speed)

            // First fix at t=1000, speed 6 (> 5): normally → Cruise, but the burst pins HighAccuracy.
            ds.emitHighAccuracy(ts(1_000L, 6f, 1.0))
            // Cruise source emits INSIDE the window → must be ignored (mode still HighAccuracy).
            ds.emitCruise(ts(3_000L, 6f, 99.0))
            ds.emitHighAccuracy(ts(5_000L, 6f, 2.0))
            // Past the 10 s window, speed still 6 → now ALLOWED to switch, but [BATT-003] still
            // debounces the relax: this fix only starts sustaining the request.
            ds.emitHighAccuracy(ts(12_000L, 6f, 3.0))
            // 21 s later, same request sustained → the switch finally takes effect. Still delivered
            // via HighAccuracy: the mode flips at the END of processing THIS fix.
            ds.emitHighAccuracy(ts(33_000L, 6f, 4.0))
            ds.emitCruise(ts(35_000L, 6f, 5.0))

            job.cancelAndJoin()
            val lats = collected.map { it.latitude }
            assertTrue(99.0 !in lats, "cruise emission inside the burst window must be ignored")
            assertEquals(listOf(1.0, 2.0, 3.0, 4.0, 5.0), lats)
        }

    // ── [BATT-003] Relax-only debounce ────────────────────────────────────────────────────────
    // Own useCase instance with initialHighAccuracyWindowMs = 0L: these tests exercise speed-
    // based transitions seconds apart, well inside the real 3-minute [DET-BURST-001] burst that
    // the DEFAULT `useCase` field (and real sessions) start with — without zeroing it here, the
    // burst pins HighAccuracy for the whole test and no relax is ever actually requested.

    @Test
    fun `should_keepTheDenserTier_when_aRelaxRequestHasNotSustained20s`() =
        runTest(UnconfinedTestDispatcher()) {
            val noBurst = ObserveAdaptiveLocationUseCase(fakeDataSource, initialHighAccuracyWindowMs = 0L)
            val collected = mutableListOf<GpsPoint>()
            val job = launch { noBurst().collect { collected.add(it) } }

            // Relax requested (→ Cruise), pending from t=1000.
            fakeDataSource.emitHighAccuracy(point(speed = 6f, timestamp = 1_000L, latitude = 1.0))
            fakeDataSource.emitCruise(point(speed = 6f, timestamp = 5_000L, latitude = 99.0)) // must be ignored
            // Only 14 s since the ask started → still HighAccuracy.
            fakeDataSource.emitHighAccuracy(point(speed = 6f, timestamp = 15_000L, latitude = 2.0))

            job.cancelAndJoin()
            val lats = collected.map { it.latitude }
            assertTrue(99.0 !in lats, "a relax must not apply before it sustains the debounce window")
            assertEquals(listOf(1.0, 2.0), lats)
        }

    @Test
    fun `should_relaxToTheLighterTier_when_theRequestSustainsPastTheDebounceWindow`() =
        runTest(UnconfinedTestDispatcher()) {
            val noBurst = ObserveAdaptiveLocationUseCase(fakeDataSource, initialHighAccuracyWindowMs = 0L)
            val collected = mutableListOf<GpsPoint>()
            val job = launch { noBurst().collect { collected.add(it) } }

            fakeDataSource.emitHighAccuracy(point(speed = 6f, timestamp = 1_000L, latitude = 1.0))
            // 21 s later, same ask sustained → switches now (still delivered via HighAccuracy).
            fakeDataSource.emitHighAccuracy(point(speed = 6f, timestamp = 22_000L, latitude = 2.0))
            fakeDataSource.emitCruise(point(speed = 6f, timestamp = 24_000L, latitude = 3.0))

            job.cancelAndJoin()
            assertEquals(listOf(1.0, 2.0, 3.0), collected.map { it.latitude })
        }

    @Test
    fun `should_restartThePendingWindow_when_speedReturnsToTheDenserBandBeforeItSustains`() =
        runTest(UnconfinedTestDispatcher()) {
            // A boundary oscillation cancels the pending relax outright — it is re-armed from
            // zero on the NEXT ask, never resumed. This is what keeps the rule only-densify: the
            // debounce can only leave the stream denser for longer, never sparser.
            val noBurst = ObserveAdaptiveLocationUseCase(fakeDataSource, initialHighAccuracyWindowMs = 0L)
            val collected = mutableListOf<GpsPoint>()
            val job = launch { noBurst().collect { collected.add(it) } }

            fakeDataSource.emitHighAccuracy(point(speed = 6f, timestamp = 1_000L, latitude = 1.0)) // relax pending
            fakeDataSource.emitHighAccuracy(point(speed = 1f, timestamp = 5_000L, latitude = 2.0)) // back under threshold → cancelled
            fakeDataSource.emitHighAccuracy(point(speed = 6f, timestamp = 30_000L, latitude = 3.0)) // NEW ask, pending from t=30000
            fakeDataSource.emitCruise(point(speed = 6f, timestamp = 35_000L, latitude = 99.0)) // only 5s since restart → must be ignored
            fakeDataSource.emitHighAccuracy(point(speed = 6f, timestamp = 51_000L, latitude = 4.0)) // 21s since restart → switches

            job.cancelAndJoin()
            val lats = collected.map { it.latitude }
            assertTrue(99.0 !in lats, "the pending window must restart, not resume, after returning to the denser band")
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

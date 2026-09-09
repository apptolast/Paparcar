package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CachingFuelStationsSourceTest {

    private class CountingSource(
        private var answer: Result<FuelStationsResult> =
            Result.success(FuelStationsResult(listOf(station("A")))),
    ) : FuelStationsSource {
        var calls = 0
        override fun covers(countryCode: String) = true
        override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> {
            calls++
            return answer
        }
        fun failWith(error: Throwable) { answer = Result.failure(error) }
        fun succeed() { answer = Result.success(FuelStationsResult(listOf(station("A")))) }

        companion object {
            fun station(id: String) =
                FuelStation(id, id, null, null, 40.4168, -3.7038, emptyMap())
        }
    }

    private var now = 0L
    private fun cached(delegate: FuelStationsSource, ttlMs: Long = 15L * 60 * 1_000) =
        CachingFuelStationsSource(delegate, ttlMs) { now }

    private fun query(lat: Double = 40.41680, lon: Double = -3.70380, radius: Int = 5_000) =
        FuelStationsQuery(lat, lon, countryCode = "ES", regionName = "Madrid", radiusMeters = radius)

    @Test
    fun `should_answerFromMemory_withinTheTtl`() = runTest {
        val delegate = CountingSource()
        val source = cached(delegate)

        source.stationsNear(query())
        now += 5 * 60 * 1_000
        val second = source.stationsNear(query()).getOrThrow()

        assertEquals(1, delegate.calls)
        assertEquals(listOf("A"), second.stations.map { it.id })
    }

    @Test
    fun `should_askAgain_onceTheTtlHasPassed`() = runTest {
        val delegate = CountingSource()
        val source = cached(delegate)

        source.stationsNear(query())
        now += 16 * 60 * 1_000
        source.stationsNear(query())

        assertEquals(2, delegate.calls)
    }

    @Test
    fun `should_hit_whenTheUserDriftedAFewMetres`() = runTest {
        // Standing still, GPS wanders by tens of metres. An exact-coordinate key would miss on every
        // reading — a cache that never hits, which is worse than none.
        val delegate = CountingSource()
        val source = cached(delegate)

        source.stationsNear(query(lat = 40.41680, lon = -3.70380))
        source.stationsNear(query(lat = 40.41684, lon = -3.70377))

        assertEquals(1, delegate.calls)
    }

    @Test
    fun `should_missTheCache_whenTheRadiusChanges`() = runTest {
        // ⛔ Serving a 1 km answer to a 5 km question would silently shrink the list.
        val delegate = CountingSource()
        val source = cached(delegate)

        source.stationsNear(query(radius = 1_000))
        source.stationsNear(query(radius = 5_000))

        assertEquals(2, delegate.calls)
    }

    @Test
    fun `should_neverCacheAFailure_becauseTheNetworkComingBackIsThePoint`() = runTest {
        val delegate = CountingSource()
        delegate.failWith(IllegalStateException("offline"))
        val source = cached(delegate)

        assertTrue(source.stationsNear(query()).isFailure)
        delegate.succeed()
        val retry = source.stationsNear(query())

        assertEquals(2, delegate.calls)
        assertTrue(retry.isSuccess)
    }
}

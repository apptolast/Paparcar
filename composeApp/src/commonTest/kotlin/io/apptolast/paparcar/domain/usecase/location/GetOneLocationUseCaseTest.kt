@file:OptIn(kotlin.time.ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.domain.usecase.location

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.fakes.FakeLocationDataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetOneLocationUseCaseTest {

    private val fakeDataSource = FakeLocationDataSource()
    private val useCase = GetOneLocationUseCase(fakeDataSource)

    private val point = GpsPoint(
        latitude = 40.416775,
        longitude = -3.703790,
        accuracy = 10f,
        timestamp = 1_000L,
        speed = 0f,
    )

    @Test
    fun `should return location when emitted within timeout`() = runTest {
        // Start the use case coroutine first so it subscribes before the emission
        val deferred = async { useCase() }
        runCurrent()
        fakeDataSource.emitBalanced(point)
        assertEquals(point, deferred.await())
    }

    @Test
    fun `should return null when timeout elapses before emission`() = runTest {
        val result = useCase()
        assertNull(result)
    }

    @Test
    fun `should return first emission only`() = runTest {
        val second = point.copy(latitude = 41.0)
        val deferred = async { useCase() }
        runCurrent()
        fakeDataSource.emitBalanced(point)
        fakeDataSource.emitBalanced(second)
        assertEquals(point, deferred.await())
    }

    // ── Freshness gate [DET-RECONCILE-001] — the fused provider's first emission is often a
    // cached last-known fix; with maxAgeMs set, a detection decision must never reason over it.

    @Test
    fun `should skip stale cached fix and return the next fresh one when freshness required`() = runTest {
        val nowMs = 1_000_000L
        val freshnessGated = GetOneLocationUseCase(fakeDataSource) { nowMs }
        val staleCached = point.copy(timestamp = nowMs - 4 * 60_000L) // the 4-min-old mid-drive cache (field 2026-07-07)
        val fresh = point.copy(latitude = 41.0, timestamp = nowMs - 5_000L)

        val deferred = async { freshnessGated(maxAgeMs = 30_000L) }
        runCurrent()
        fakeDataSource.emitBalanced(staleCached)
        fakeDataSource.emitBalanced(fresh)

        assertEquals(fresh, deferred.await())
    }

    @Test
    fun `should return null when only stale fixes arrive within the timeout`() = runTest {
        val nowMs = 1_000_000L
        val freshnessGated = GetOneLocationUseCase(fakeDataSource) { nowMs }
        val staleCached = point.copy(timestamp = nowMs - 2 * 60 * 60_000L)

        val deferred = async { freshnessGated(maxAgeMs = 30_000L) }
        runCurrent()
        fakeDataSource.emitBalanced(staleCached)

        assertNull(deferred.await())
    }

    @Test
    fun `should accept a stale fix when no freshness is required`() = runTest {
        val nowMs = 1_000_000L
        val ungated = GetOneLocationUseCase(fakeDataSource) { nowMs }
        val staleCached = point.copy(timestamp = nowMs - 2 * 60 * 60_000L)

        val deferred = async { ungated() }
        runCurrent()
        fakeDataSource.emitBalanced(staleCached)

        assertEquals(staleCached, deferred.await())
    }
}

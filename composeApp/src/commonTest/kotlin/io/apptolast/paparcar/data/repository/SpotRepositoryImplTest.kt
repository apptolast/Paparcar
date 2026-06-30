@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.apptolast.paparcar.data.repository

import app.cash.turbine.test
import io.apptolast.paparcar.data.datasource.local.room.SpotEntity
import io.apptolast.paparcar.data.datasource.remote.dto.SpotDto
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.fakes.FakeFirebaseDataSource
import io.apptolast.paparcar.fakes.FakeSpotDao
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests the offline-first contract of [SpotRepositoryImpl].
 *
 * Invariants verified:
 *  - Local Room cache is the primary read surface — emissions start before Firestore.
 *  - Firestore writes flow into Room and re-emit via the cache Flow.
 *  - Firestore errors are isolated — they never tear down the Room observation.
 *  - [reportSpotReleased] hits Firestore AND removes the spot from local cache.
 */
class SpotRepositoryImplTest {

    private val origin = GpsPoint(latitude = 40.0, longitude = -3.7, accuracy = 5f, timestamp = 0L, speed = 0f)
    private val radiusMeters = 500.0

    private fun spotEntity(id: String, lat: Double = 40.0, lon: Double = -3.7) = SpotEntity(
        id = id,
        latitude = lat,
        longitude = lon,
        accuracy = 8f,
        reportedAt = 1_000L,
        reportedBy = "user-x",
    )

    private fun spotDto(id: String, lat: Double = 40.0, lon: Double = -3.7) = SpotDto(
        id = id,
        latitude = lat,
        longitude = lon,
        accuracy = 8f,
        reportedAt = 1_000L,
        reportedBy = "user-x",
    )

    // ─────────────────────────────────────────────────────────────────────────
    // observeNearbySpots — offline-first
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun observeNearbySpots_emits_cached_spots_immediately_without_waiting_for_firestore() = runTest {
        val dao = FakeSpotDao().apply { seed(listOf(spotEntity("cached-1"))) }
        val firebase = FakeFirebaseDataSource()
        val repo = SpotRepositoryImpl(firebase, dao)

        repo.observeNearbySpots(origin, radiusMeters).test {
            val first = awaitItem()
            assertEquals(listOf("cached-1"), first.map(Spot::id))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeNearbySpots_writes_firestore_emission_into_room_and_re_emits() = runTest {
        val dao = FakeSpotDao()
        val firebase = FakeFirebaseDataSource()
        val repo = SpotRepositoryImpl(firebase, dao)

        repo.observeNearbySpots(origin, radiusMeters).test {
            // Initial — empty cache.
            assertEquals(emptyList(), awaitItem())

            // Firestore pushes a new spot — Room is updated and the Flow re-emits.
            firebase.observeSpotsFlow.emit(mapOf("remote-1" to spotDto("remote-1")))

            val updated = awaitItem()
            assertEquals(listOf("remote-1"), updated.map(Spot::id))
            // Cache reflects the Firestore content.
            assertEquals(listOf("remote-1"), dao.snapshot().map(SpotEntity::id))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeNearbySpots_keeps_streaming_cache_when_firestore_listener_fails() = runTest {
        val dao = FakeSpotDao().apply { seed(listOf(spotEntity("cached-1"))) }
        val firebase = FakeFirebaseDataSource().apply {
            observeNearbyThrows = IllegalStateException("listener boom")
        }
        val repo = SpotRepositoryImpl(firebase, dao)

        repo.observeNearbySpots(origin, radiusMeters).test {
            val first = awaitItem()
            assertEquals(listOf("cached-1"), first.map(Spot::id))
            // Firestore error is swallowed by .catch{} — the Room Flow remains live.
            // Mutate the cache directly to prove the Flow is still emitting.
            dao.upsert(spotEntity("cached-2"))
            val next = awaitItem()
            assertEquals(setOf("cached-1", "cached-2"), next.map(Spot::id).toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // reportSpotReleased — publish-only (cache left to the Firestore listener)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun reportSpotReleased_calls_firestore_and_leaves_local_cache_untouched() = runTest {
        val dao = FakeSpotDao().apply { seed(listOf(spotEntity("released-1"))) }
        val firebase = FakeFirebaseDataSource()
        val repo = SpotRepositoryImpl(firebase, dao)

        val spot = Spot(
            id = "released-1",
            location = origin,
            reportedBy = "me",
            type = SpotType.AUTO_DETECTED,
        )

        val result = repo.reportSpotReleased(spot)

        assertTrue(result.isSuccess)
        assertEquals(1, firebase.reportSpotReleasedCallCount)
        assertNotNull(firebase.lastReportedSpot)
        assertEquals("released-1", firebase.lastReportedSpot!!.id)
        // Publish-only: the method deliberately does NOT touch the local cache — a local delete
        // would race the Firestore real-time listener's echo (insert → delete → re-insert) and
        // flash the marker. Room is pruned reactively by that listener, not here. [SPOT-FLICKER-001]
        assertEquals(listOf(spotEntity("released-1")), dao.snapshot())
    }
}

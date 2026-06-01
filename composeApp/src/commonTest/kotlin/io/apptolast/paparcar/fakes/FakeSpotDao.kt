package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.data.datasource.local.room.SpotDao
import io.apptolast.paparcar.data.datasource.local.room.SpotEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake [SpotDao] backed by a [MutableStateFlow] so [observeNearby]
 * reacts to writes the same way Room's real `Flow` query would. Bounded queries
 * filter the snapshot on each emission.
 */
class FakeSpotDao : SpotDao {

    private val store = MutableStateFlow<List<SpotEntity>>(emptyList())

    var upsertAllCallCount = 0
        private set
    var deleteCallCount = 0
        private set
    var deleteAllCallCount = 0
        private set

    /** Test helper — replace the whole cache without going through upsert side-effect counters. */
    fun seed(spots: List<SpotEntity>) {
        store.value = spots
    }

    /** Test helper — current snapshot. */
    fun snapshot(): List<SpotEntity> = store.value

    override suspend fun upsertAll(spots: List<SpotEntity>) {
        upsertAllCallCount++
        val byId = store.value.associateBy { it.id }.toMutableMap()
        spots.forEach { byId[it.id] = it }
        store.value = byId.values.toList()
    }

    override suspend fun upsert(spot: SpotEntity) {
        upsertAll(listOf(spot))
    }

    override fun observeNearby(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): Flow<List<SpotEntity>> = store.map { list ->
        list.filter {
            it.latitude in minLat..maxLat && it.longitude in minLon..maxLon
        }
    }

    override suspend fun getNearby(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): List<SpotEntity> = store.value.filter {
        it.latitude in minLat..maxLat && it.longitude in minLon..maxLon
    }

    override suspend fun delete(id: String) {
        deleteCallCount++
        store.value = store.value.filterNot { it.id == id }
    }

    override suspend fun deleteExpired(nowMillis: Long) {
        store.value = store.value.filterNot { it.expiresAt != 0L && it.expiresAt < nowMillis }
    }

    override suspend fun deleteAll() {
        deleteAllCallCount++
        store.value = emptyList()
    }

    override suspend fun getIdsInBbox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): List<String> = store.value
        .filter { it.latitude in minLat..maxLat && it.longitude in minLon..maxLon }
        .map { it.id }
}

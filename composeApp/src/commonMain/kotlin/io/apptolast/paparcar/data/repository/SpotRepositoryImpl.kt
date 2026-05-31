package io.apptolast.paparcar.data.repository

import io.apptolast.paparcar.data.datasource.local.room.SpotDao
import io.apptolast.paparcar.data.datasource.local.room.SpotEntity
import io.apptolast.paparcar.data.datasource.remote.FirebaseDataSource
import io.apptolast.paparcar.data.datasource.remote.dto.SpotDto
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toDto
import io.apptolast.paparcar.data.mapper.toEntity
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.repository.SpotRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.time.Clock

/**
 * Offline-first implementation of [SpotRepository].
 *
 * **Read strategy:**
 * - [observeNearbySpots] emits from the local Room cache first (instant, no network wait),
 *   then opens a Firestore listener in parallel. Every Firestore update is written to Room,
 *   which triggers a new emission from the Room Flow.
 *
 * **Write strategy:**
 * - [reportSpotReleased] calls Firestore and removes the spot from the local cache.
 */
class SpotRepositoryImpl(
    private val firebaseDataSource: FirebaseDataSource,
    private val spotDao: SpotDao,
) : SpotRepository {

    override fun observeNearbySpots(
        location: GpsPoint,
        radiusMeters: Double,
    ): Flow<List<Spot>> {
        val bbox = boundingBox(location.latitude, location.longitude, radiusMeters)
        return channelFlow {
            // 1. Stream Room cache immediately — no network required.
            //    distinctUntilChanged() skips re-emission when Firestore writes back identical
            //    data, avoiding unnecessary recomposition in the UI.
            launch {
                spotDao.observeNearby(bbox.minLat, bbox.maxLat, bbox.minLon, bbox.maxLon)
                    .map { entities ->
                        val now = Clock.System.now().toEpochMilliseconds()
                        entities
                            .filter { it.expiresAt == 0L || it.expiresAt > now }
                            .map(SpotEntity::toDomain)
                    }
                    .distinctUntilChanged()
                    .collect { send(it) }
            }
            // 2. Subscribe to Firestore — real-time listener, fires on every remote change.
            //    Firestore may return a larger geo area (geohash cell) than the exact bbox,
            //    so we pre-filter to bbox before writing to Room. smartReplaceForBoundingBox
            //    then does a diff: only removes spots that disappeared, only upserts new/changed
            //    ones. Room dispatches ONE invalidation after the transaction commits, so the
            //    UI Flow never sees an intermediate empty-list state.
            //    .catch{} isolates Firestore errors — the UI keeps showing cached spots.
            firebaseDataSource.observeNearbySpots(location.latitude, location.longitude, radiusMeters)
                .catch { e -> PaparcarLogger.w(TAG, "Firestore spots listener error — using cache", e) }
                .collect { dtoMap ->
                    val now = Clock.System.now().toEpochMilliseconds()
                    val bboxDtos = dtoMap.values.filter { dto ->
                        dto.latitude in bbox.minLat..bbox.maxLat &&
                        dto.longitude in bbox.minLon..bbox.maxLon
                    }
                    // Passive cleanup: delete expired spots from Firestore so all clients
                    // benefit without waiting for server-side TTL.
                    bboxDtos
                        .filter { it.expiresAt != 0L && it.expiresAt < now }
                        .forEach { expired ->
                            launch {
                                runCatching { firebaseDataSource.deleteSpot(expired.id) }
                                    .onFailure { e -> PaparcarLogger.w(TAG, "Failed to delete expired spot ${expired.id}", e) }
                            }
                        }
                    val validEntities = bboxDtos
                        .filter { it.expiresAt == 0L || it.expiresAt >= now }
                        .map(SpotDto::toEntity)
                    spotDao.smartReplaceForBoundingBox(
                        bbox.minLat, bbox.maxLat, bbox.minLon, bbox.maxLon,
                        validEntities,
                    )
                }
        }
    }

    override suspend fun reportSpotReleased(spot: Spot): Result<Unit> = runCatching {
        firebaseDataSource.reportSpotReleased(spot.toDto())
        spotDao.delete(spot.id)
    }

    override suspend fun sendSpotSignal(spotId: String, accepted: Boolean): Result<Unit> =
        runCatching { firebaseDataSource.sendSpotSignal(spotId, accepted) }
        // Room cache updates automatically via the Firestore real-time listener in observeNearbySpots.

    override suspend fun clearCache(): Result<Unit> = runCatching { spotDao.deleteAll() }

    private fun boundingBox(lat: Double, lon: Double, radiusMeters: Double): BoundingBox {
        val deltaLat = radiusMeters / METERS_PER_DEGREE_LAT
        val deltaLon = radiusMeters / (METERS_PER_DEGREE_LAT * cos(lat * PI / 180.0))
        return BoundingBox(
            minLat = lat - deltaLat,
            maxLat = lat + deltaLat,
            minLon = lon - deltaLon,
            maxLon = lon + deltaLon,
        )
    }

    private data class BoundingBox(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double,
    )

    private companion object {
        const val TAG = "SpotRepositoryImpl"
        /** Approximate metres per degree of latitude (constant). */
        const val METERS_PER_DEGREE_LAT = 111_111.0
    }
}

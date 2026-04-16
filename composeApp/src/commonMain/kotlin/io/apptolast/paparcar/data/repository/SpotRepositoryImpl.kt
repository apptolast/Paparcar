package io.apptolast.paparcar.data.repository

import io.apptolast.paparcar.core.logging.PaparcarLogger
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos

/**
 * Offline-first implementation of [SpotRepository].
 *
 * **Read strategy:**
 * - [observeNearbySpots] emits from the local Room cache first (instant, no network wait),
 *   then opens a Firestore listener in parallel. Every Firestore update is written to Room,
 *   which triggers a new emission from the Room Flow.
 * - [getNearbySpots] tries Firestore first and writes the result to Room; if the network
 *   call fails it falls back to the Room cache for the same bounding box.
 *
 * **Write strategy:**
 * - [reportSpotReleased] calls Firestore and removes the spot from the local cache.
 */
class SpotRepositoryImpl(
    private val firebaseDataSource: FirebaseDataSource,
    private val spotDao: SpotDao,
) : SpotRepository {

    override suspend fun getNearbySpots(
        location: GpsPoint,
        radiusMeters: Double,
    ): Result<List<Spot>> = runCatching {
        firebaseDataSource.getNearbySpots(location.latitude, location.longitude, radiusMeters)
            .also { dtoMap -> spotDao.upsertAll(dtoMap.values.map { it.toEntity() }) }
            .map { (_, dto) -> dto.toDomain() }
    }.recoverCatching {
        // Network failed — serve stale cache so the UI stays populated.
        val bbox = boundingBox(location.latitude, location.longitude, radiusMeters)
        spotDao.getNearby(bbox.minLat, bbox.maxLat, bbox.minLon, bbox.maxLon)
            .map { it.toDomain() }
    }

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
                    .map { it.map(SpotEntity::toDomain) }
                    .distinctUntilChanged()
                    .collect { send(it) }
            }
            // 2. Subscribe to Firestore and atomically replace the bbox slice in Room.
            //    replaceForBoundingBox() deletes stale entries first so spots that were
            //    removed or expired in Firestore are not kept alive in the local cache.
            //    Room's Flow picks up every write and re-emits above.
            //    .catch{} isolates Firestore errors so they do NOT cancel the Room
            //    observation — the UI continues showing cached spots while Firestore
            //    is temporarily unavailable.
            firebaseDataSource.observeNearbySpots(location.latitude, location.longitude, radiusMeters)
                .catch { e -> PaparcarLogger.w(TAG, "Firestore spots listener error — using cache", e) }
                .collect { dtoMap ->
                    spotDao.replaceForBoundingBox(
                        bbox.minLat, bbox.maxLat, bbox.minLon, bbox.maxLon,
                        dtoMap.values.map(SpotDto::toEntity),
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

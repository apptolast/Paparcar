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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
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
 * - [reportSpotReleased] publishes to Firestore only; the real-time listener
 *   echoes the new spot back into Room so it appears (and stays) reactively.
 *   No local-cache mutation — that would race the echo and flicker the marker.
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
                // Self-heal a listener that actually errors out (not normal offline —
                // the native SDK queues those and resyncs on reconnect by itself). Room
                // keeps serving cache during the backoff, so markers never blink. Gives
                // up after MAX_LISTENER_RETRIES for a persistent error (e.g. permission),
                // falling through to .catch which leaves the cache showing. [SPOT-FLICKER-001]
                .retryWhen { cause, attempt ->
                    if (attempt >= MAX_LISTENER_RETRIES) {
                        false
                    } else {
                        PaparcarLogger.w(TAG, "Firestore spots listener error — retry #${attempt + 1}", cause)
                        delay(LISTENER_RETRY_DELAY_MS * (attempt + 1))
                        true
                    }
                }
                .catch { e -> PaparcarLogger.w(TAG, "Firestore spots listener gave up — using cache", e) }
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
        // Publish only. The Firestore .set() makes the real-time listener in
        // observeNearbySpots fire, which writes the spot into Room — so the new
        // marker appears reactively and stays. We deliberately do NOT touch the
        // local cache here: a local spotDao.delete() would race that echo
        // (insert → delete → re-insert on the next snapshot) and flash the
        // marker. To HIDE a spot for everyone, delete it from Firestore
        // (firebaseDataSource.deleteSpot) and let the listener prune Room. [SPOT-FLICKER-001]
        firebaseDataSource.reportSpotReleased(spot.toDto())
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
        /** Max self-heal attempts before the listener gives up and leaves cache showing. */
        const val MAX_LISTENER_RETRIES = 5L
        /** Base backoff between listener retries (multiplied by attempt number). */
        const val LISTENER_RETRY_DELAY_MS = 2_000L
    }
}

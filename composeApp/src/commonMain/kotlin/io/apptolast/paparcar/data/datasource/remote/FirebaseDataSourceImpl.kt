package io.apptolast.paparcar.data.datasource.remote

import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import io.apptolast.paparcar.data.datasource.remote.dto.AddressDto
import io.apptolast.paparcar.data.datasource.remote.dto.PlaceInfoDto
import io.apptolast.paparcar.data.datasource.remote.dto.SpotDto
import io.apptolast.paparcar.data.datasource.remote.dto.ZoneDto
import io.apptolast.paparcar.data.geohash.geohashQueryRange
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirebaseDataSourceImpl(private val firestore: FirebaseFirestore) : FirebaseDataSource {

    private val spotsCollection = firestore.collection("spots")

    override fun observeNearbySpots(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    ): Flow<Map<String, SpotDto>> {
        // Geohash precision 4 (~39km × 20km cell) guarantees the entire radius is
        // within one cell regardless of where the center falls inside it. A smaller
        // precision (e.g. 5 = ~4.9km) would miss spots at cell boundaries for a
        // 2km radius. Room's bbox filter in SpotRepositoryImpl clips the result to
        // the actual radius after the write.
        val (startHash, endHash) = geohashQueryRange(latitude, longitude, queryPrecision = GEOHASH_QUERY_PRECISION)
        return spotsCollection
            .where { "geohash" greaterThanOrEqualTo startHash }
            .where { "geohash" lessThan endHash }
            .snapshots
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { doc -> doc.toSpotDto()?.let { doc.id to it } }
                    .toMap()
            }
    }

    override suspend fun reportSpotReleased(spotDto: SpotDto) {
        spotsCollection.document(spotDto.id).set(spotDto)
    }

    override suspend fun deleteSpot(spotId: String) {
        spotsCollection.document(spotId).delete()
    }

    @Suppress("DEPRECATION")
    override suspend fun sendSpotSignal(spotId: String, accepted: Boolean) {
        val field = if (accepted) FIELD_ACCEPT_COUNT else FIELD_REJECT_COUNT
        spotsCollection.document(spotId).update(field to FieldValue.increment(1))
    }

    // ─── Zones ────────────────────────────────────────────────────────────────

    private fun zonesCollection(userId: String) =
        firestore.collection("users").document(userId).collection("zones")

    override suspend fun getZones(userId: String): List<ZoneDto> =
        zonesCollection(userId).get().documents.mapNotNull { doc ->
            doc.toZoneDto()
        }

    override suspend fun saveZone(userId: String, zone: ZoneDto) {
        zonesCollection(userId).document(zone.id).set(zone)
    }

    override suspend fun deleteZone(userId: String, zoneId: String) {
        zonesCollection(userId).document(zoneId).delete()
    }

    override suspend fun deleteAllZones(userId: String) {
        runCatching {
            zonesCollection(userId).get().documents.forEach { it.reference.delete() }
        }
    }

    // ─── Typed deserialization using GitLive SDK 2.x get<T?>() API ────────────
    // [IMPORTANT] get<T?>(field) uses the KSerialization serializer for T — Any
    // has none, so each field must use its concrete type. Avoid .data<T>() as it
    // may trip on unknown fields or generic mapping. [SYNC-001]

    private fun dev.gitlive.firebase.firestore.DocumentSnapshot.toZoneDto(): ZoneDto? =
        runCatching {
            ZoneDto(
                id = id,
                userId = get<String?>("userId") ?: return@runCatching null,
                name = get<String?>("name") ?: "",
                lat = get<Double?>("lat") ?: 0.0,
                lon = get<Double?>("lon") ?: 0.0,
                iconKey = get<String?>("iconKey") ?: "",
                createdAt = getLongCompat("createdAt")
            )
        }.getOrElse { e ->
            PaparcarLogger.e(TAG, "toZoneDto failed for doc=$id", e)
            null
        }

    private fun dev.gitlive.firebase.firestore.DocumentSnapshot.toSpotDto(): SpotDto? =
        runCatching {
            val lat = get<Double?>("latitude") ?: return@runCatching null
            val lon = get<Double?>("longitude") ?: return@runCatching null
            SpotDto(
                id = id,
                latitude = lat,
                longitude = lon,
                accuracy = get<Double?>("accuracy")?.toFloat() ?: 0f,
                reportedAt = getLongCompat("reportedAt"),
                reportedBy = get<String?>("reportedBy") ?: "",
                speed = get<Double?>("speed")?.toFloat() ?: 0f,
                address = runCatching { get<AddressDto?>("address") }.getOrNull(),
                placeInfo = runCatching { get<PlaceInfoDto?>("placeInfo") }.getOrNull(),
                acceptCount = runCatching { get<Long?>(FIELD_ACCEPT_COUNT)?.toInt() }.getOrNull() ?: 0,
                rejectCount = runCatching { get<Long?>(FIELD_REJECT_COUNT)?.toInt() }.getOrNull() ?: 0,
                countryCode = get<String?>("countryCode"),
                citySlug = get<String?>("citySlug"),
                geohash = get<String?>("geohash"),
            )
        }.getOrElse { e ->
            PaparcarLogger.e(TAG, "toSpotDto failed for doc=$id", e)
            null
        }

    /** Reads a Long field tolerating Firestore's Double representation of integers. */
    private fun dev.gitlive.firebase.firestore.DocumentSnapshot.getLongCompat(field: String): Long =
        runCatching { get<Long?>(field) }.getOrNull()
            ?: runCatching { get<Double?>(field)?.toLong() }.getOrNull()
            ?: 0L

    private companion object {
        const val TAG = "FirebaseDataSourceImpl"
        const val FIELD_ACCEPT_COUNT = "acceptCount"
        const val FIELD_REJECT_COUNT = "rejectCount"
        // Precision 4 cells are ~39km × 20km — large enough that a 2km radius is
        // always fully contained in one cell regardless of position within the cell.
        const val GEOHASH_QUERY_PRECISION = 4
    }
}

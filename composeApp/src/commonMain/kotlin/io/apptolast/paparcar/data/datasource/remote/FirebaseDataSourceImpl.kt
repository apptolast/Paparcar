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

    // Field-by-field reads must cover every property in [ZoneDto]. Defaults here mirror
    // the DTO defaults so legacy docs (pre-zone-radius / pre-isPrivate) deserialize cleanly.
    private fun dev.gitlive.firebase.firestore.DocumentSnapshot.toZoneDto(): ZoneDto? =
        runCatching {
            ZoneDto(
                id = id,
                userId = get<String?>(FIELD_USER_ID) ?: return@runCatching null,
                name = get<String?>(FIELD_NAME).orEmpty(),
                lat = get<Double?>(FIELD_LAT) ?: 0.0,
                lon = get<Double?>(FIELD_LON) ?: 0.0,
                iconKey = get<String?>(FIELD_ICON_KEY).orEmpty(),
                createdAt = getLongCompat(FIELD_CREATED_AT),
                radiusMeters = get<Double?>(FIELD_RADIUS_METERS)?.toFloat() ?: DEFAULT_ZONE_RADIUS_M,
                isPrivate = get<Boolean?>(FIELD_IS_PRIVATE) ?: false,
            )
        }.getOrElse { e ->
            PaparcarLogger.e(TAG, "toZoneDto failed for doc=$id", e)
            null
        }

    // Field-by-field reads must cover every property in [SpotDto]. Missing any here causes
    // the in-memory Spot to silently lose data (e.g. sizeCategory → null → "indefinido" pill).
    private fun dev.gitlive.firebase.firestore.DocumentSnapshot.toSpotDto(): SpotDto? =
        runCatching {
            val lat = get<Double?>(FIELD_LATITUDE) ?: return@runCatching null
            val lon = get<Double?>(FIELD_LONGITUDE) ?: return@runCatching null
            SpotDto(
                id = id,
                latitude = lat,
                longitude = lon,
                accuracy = get<Double?>(FIELD_ACCURACY)?.toFloat() ?: 0f,
                reportedAt = getLongCompat(FIELD_REPORTED_AT),
                reportedBy = get<String?>(FIELD_REPORTED_BY).orEmpty(),
                speed = get<Double?>(FIELD_SPEED)?.toFloat() ?: 0f,
                address = runCatching { get<AddressDto?>(FIELD_ADDRESS) }.getOrNull(),
                placeInfo = runCatching { get<PlaceInfoDto?>(FIELD_PLACE_INFO) }.getOrNull(),
                type = get<String?>(FIELD_TYPE) ?: DEFAULT_SPOT_TYPE,
                confidence = get<Double?>(FIELD_CONFIDENCE)?.toFloat() ?: 1f,
                sizeCategory = get<String?>(FIELD_SIZE_CATEGORY),
                carbodyType = get<String?>(FIELD_CARBODY_TYPE),
                enRouteCount = get<Long?>(FIELD_EN_ROUTE_COUNT)?.toInt() ?: 0,
                expiresAt = getLongCompat(FIELD_EXPIRES_AT),
                acceptCount = runCatching { get<Long?>(FIELD_ACCEPT_COUNT)?.toInt() }.getOrNull() ?: 0,
                rejectCount = runCatching { get<Long?>(FIELD_REJECT_COUNT)?.toInt() }.getOrNull() ?: 0,
                countryCode = get<String?>(FIELD_COUNTRY_CODE),
                citySlug = get<String?>(FIELD_CITY_SLUG),
                geohash = get<String?>(FIELD_GEOHASH),
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

        // Spot fields
        const val FIELD_LATITUDE = "latitude"
        const val FIELD_LONGITUDE = "longitude"
        const val FIELD_ACCURACY = "accuracy"
        const val FIELD_REPORTED_AT = "reportedAt"
        const val FIELD_REPORTED_BY = "reportedBy"
        const val FIELD_SPEED = "speed"
        const val FIELD_ADDRESS = "address"
        const val FIELD_PLACE_INFO = "placeInfo"
        const val FIELD_TYPE = "type"
        const val FIELD_CONFIDENCE = "confidence"
        const val FIELD_SIZE_CATEGORY = "sizeCategory"
        const val FIELD_CARBODY_TYPE = "carbodyType"
        const val FIELD_EN_ROUTE_COUNT = "enRouteCount"
        const val FIELD_EXPIRES_AT = "expiresAt"
        const val FIELD_ACCEPT_COUNT = "acceptCount"
        const val FIELD_REJECT_COUNT = "rejectCount"
        const val FIELD_COUNTRY_CODE = "countryCode"
        const val FIELD_CITY_SLUG = "citySlug"
        const val FIELD_GEOHASH = "geohash"

        // Zone fields
        const val FIELD_USER_ID = "userId"
        const val FIELD_NAME = "name"
        const val FIELD_LAT = "lat"
        const val FIELD_LON = "lon"
        const val FIELD_ICON_KEY = "iconKey"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_RADIUS_METERS = "radiusMeters"
        const val FIELD_IS_PRIVATE = "isPrivate"

        const val DEFAULT_SPOT_TYPE = "AUTO_DETECTED"
        const val DEFAULT_ZONE_RADIUS_M = 250f

        // Precision 4 cells are ~39km × 20km — large enough that a 2km radius is
        // always fully contained in one cell regardless of position within the cell.
        const val GEOHASH_QUERY_PRECISION = 4
    }
}

package io.apptolast.paparcar.data.datasource.remote

import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import io.apptolast.paparcar.data.datasource.remote.dto.AddressDto
import io.apptolast.paparcar.data.datasource.remote.dto.PlaceInfoDto
import io.apptolast.paparcar.data.datasource.remote.dto.SpotDto
import io.apptolast.paparcar.data.datasource.remote.dto.ZoneDto
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirebaseDataSourceImpl(private val firestore: FirebaseFirestore) : FirebaseDataSource {

    private val spotsCollection = firestore.collection("spots")

    override fun observeNearbySpots(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    ): Flow<Map<String, SpotDto>> =
        spotsCollection
            .snapshots
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { doc -> doc.toSpotDto()?.let { doc.id to it } }
                    .toMap()
            }

    override suspend fun reportSpotReleased(spotDto: SpotDto) {
        spotsCollection.document(spotDto.id).set(spotDto)
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
        runCatching {
            zonesCollection(userId).get().documents.map { doc -> doc.data<ZoneDto>() }
        }.getOrElse { e ->
            PaparcarLogger.e(TAG, "getZones failed for userId=$userId", e)
            emptyList()
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
    // get<T?>(field) uses the KSerialization serializer for T — Any has none, so
    // each field must use its concrete type. Nested objects (AddressDto, PlaceInfoDto)
    // are decoded with their own @Serializable serializers via a nested runCatching so
    // that a format change in those sub-objects doesn't drop the whole spot.
    private fun dev.gitlive.firebase.firestore.DocumentSnapshot.toSpotDto(): SpotDto? =
        runCatching {
            val lat = get<Double?>("latitude") ?: return@runCatching null
            val lon = get<Double?>("longitude") ?: return@runCatching null
            SpotDto(
                id = id,
                latitude = lat,
                longitude = lon,
                accuracy = get<Double?>("accuracy")?.toFloat() ?: 0f,
                // reportedAt may arrive as Long or Double depending on client — handle both
                reportedAt = runCatching { get<Long?>("reportedAt") }.getOrNull()
                    ?: runCatching { get<Double?>("reportedAt")?.toLong() }.getOrNull()
                    ?: 0L,
                reportedBy = get<String?>("reportedBy") ?: "",
                speed = get<Double?>("speed")?.toFloat() ?: 0f,
                address = runCatching { get<AddressDto?>("address") }.getOrNull(),
                placeInfo = runCatching { get<PlaceInfoDto?>("placeInfo") }.getOrNull(),
                acceptCount = runCatching { get<Long?>(FIELD_ACCEPT_COUNT)?.toInt() }.getOrNull() ?: 0,
                rejectCount = runCatching { get<Long?>(FIELD_REJECT_COUNT)?.toInt() }.getOrNull() ?: 0,
            )
        }.getOrElse { e ->
            PaparcarLogger.e(TAG, "toSpotDto failed for doc=$id", e)
            null
        }

    private companion object {
        const val TAG = "FirebaseDataSourceImpl"
        const val FIELD_ACCEPT_COUNT = "acceptCount"
        const val FIELD_REJECT_COUNT = "rejectCount"
    }
}

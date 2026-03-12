package io.apptolast.paparcar.data.datasource.remote

import dev.gitlive.firebase.firestore.FirebaseFirestore
import io.apptolast.paparcar.data.datasource.remote.dto.AddressDto
import io.apptolast.paparcar.data.datasource.remote.dto.PlaceInfoDto
import io.apptolast.paparcar.data.datasource.remote.dto.SpotDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FirebaseDataSourceImpl(firestore: FirebaseFirestore) : FirebaseDataSource {

    private val spotsCollection = firestore.collection("spots")

    override suspend fun getNearbySpots(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    ): Map<String, SpotDto> =
        spotsCollection
            .get()
            .documents
            .mapNotNull { doc -> doc.toSpotDto()?.let { doc.id to it } }
            .toMap()

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
            )
        }.getOrElse { e ->
            println("PAPARCAR toSpotDto EXCEPTION doc=$id: ${e.message}")
            null
        }
}

package io.apptolast.paparcar.data.datasource.remote

import dev.gitlive.firebase.firestore.FirebaseFirestore
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

    // ─── Deserialización defensiva: extrae campos individualmente ────────────
    // GitLive SDK 2.x usa Kotlin Serialization internamente en get(). `as? Number`
    // lanza SerializationException porque kotlin.Number es abstracta y no tiene
    // serializer. Usar los tipos concretos que Firestore devuelve:
    //   Double → para latitude, longitude, accuracy, speed
    //   Long   → para reportedAt (enteros en Firestore)
    private fun dev.gitlive.firebase.firestore.DocumentSnapshot.toSpotDto(): SpotDto? =
        runCatching {
            SpotDto(
                id = id,
                latitude = get("latitude") as? Double ?: return@runCatching null,
                longitude = get("longitude") as? Double ?: return@runCatching null,
                accuracy = (get("accuracy") as? Double)?.toFloat() ?: 0f,
                reportedAt = (get("reportedAt") as? Long) ?: 0L,
                reportedBy = get("reportedBy") as? String ?: "",
                isActive = get("isActive") as? Boolean ?: false,
                speed = (get("speed") as? Double)?.toFloat() ?: 0f,
            )
        }.getOrNull()
}

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
    // Firestore almacena todos los números como Double. Usar doc.data<SpotDto>()
    // puede fallar si los campos Float no hacen coerción automática de tipos.
    private fun dev.gitlive.firebase.firestore.DocumentSnapshot.toSpotDto(): SpotDto? =
        runCatching {
            SpotDto(
                id = id,
                latitude = get("latitude") as? Double ?: return@runCatching null,
                longitude = get("longitude") as? Double ?: return@runCatching null,
                accuracy = (get("accuracy") as? Number)?.toFloat() ?: 0f,
                reportedAt = (get("reportedAt") as? Number)?.toLong() ?: 0L,
                reportedBy = get("reportedBy") as? String ?: "",
                isActive = get("isActive") as? Boolean ?: false,
                speed = (get("speed") as? Number)?.toFloat() ?: 0f,
            )
        }.getOrNull()
}

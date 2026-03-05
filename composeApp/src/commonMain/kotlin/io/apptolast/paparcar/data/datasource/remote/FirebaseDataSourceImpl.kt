package io.apptolast.paparcar.data.datasource.remote

import dev.gitlive.firebase.firestore.FirebaseFirestore
import io.apptolast.paparcar.data.datasource.remote.dto.SpotDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

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
            .mapNotNull { doc -> doc.toSpotDto()?.takeIf { it.isActive && haversineMeters(latitude, longitude, it.latitude, it.longitude) <= radiusMeters }
                ?.let { doc.id to it }
            }
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
                    .mapNotNull { doc ->
                        val dto = doc.toSpotDto() ?: return@mapNotNull null
                        if (!dto.isActive) return@mapNotNull null
                        if (haversineMeters(latitude, longitude, dto.latitude, dto.longitude) > radiusMeters) return@mapNotNull null
                        doc.id to dto
                    }
                    .toMap()
            }

    override suspend fun reportSpotReleased(spotDto: SpotDto) {
        spotsCollection.document(spotDto.id).set(spotDto)
    }

    // ─── Deserialización defensiva: extrae campos individualmente ────────────
    // Firestore almacena todos los números como Double. Usar doc.data<SpotDto>()
    // puede fallar si los campos Float no hacen coerción automática de tipos.
    private fun dev.gitlive.firebase.firestore.DocumentSnapshot.toSpotDto(): SpotDto? = runCatching {
        SpotDto(
            id          = id,
            latitude    = get("latitude")  as? Double ?: return@runCatching null,
            longitude   = get("longitude") as? Double ?: return@runCatching null,
            accuracy    = (get("accuracy")  as? Number)?.toFloat() ?: 0f,
            reportedAt  = (get("reportedAt") as? Number)?.toLong()  ?: 0L,
            reportedBy  = get("reportedBy") as? String ?: "",
            isActive    = get("isActive")   as? Boolean ?: false,
            speed       = (get("speed")     as? Number)?.toFloat() ?: 0f,
        )
    }.getOrNull()

    // ─── Haversine (metros) ───────────────────────────────────────────────────
    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
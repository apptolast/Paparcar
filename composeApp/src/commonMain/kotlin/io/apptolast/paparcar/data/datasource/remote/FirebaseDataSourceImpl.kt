package io.apptolast.paparcar.data.datasource.remote

import dev.gitlive.firebase.firestore.FirebaseFirestore
import io.apptolast.paparcar.data.datasource.remote.dto.SpotDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FirebaseDataSourceImpl(firestore: FirebaseFirestore) : FirebaseDataSource {

    private val spotsCollection = firestore.collection("spots")

    override suspend fun getNearbySpots(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double
    ): Map<String, SpotDto> {
        // TODO: Implementar la lógica para obtener spots cercanos
        return emptyMap()
    }

    override fun observeNearbySpots(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double
    ): Flow<Map<String, SpotDto>> {
        // TODO: Implementar la lógica para observar spots cercanos
        return flowOf(emptyMap())
    }

    override suspend fun reportSpotReleased(spotDto: SpotDto) {
        // Usa el ID generado en el cliente para crear el documento con una sola operación.
        spotsCollection.document(spotDto.id).set(spotDto)
    }
}

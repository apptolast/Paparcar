package io.apptolast.paparcar.data.repository

import io.apptolast.paparcar.data.datasource.remote.FirebaseDataSource
import io.apptolast.paparcar.data.mapper.toDomain
import io.apptolast.paparcar.data.mapper.toDto
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotLocation
import io.apptolast.paparcar.domain.repository.SpotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SpotRepositoryImpl(private val firebaseDataSource: FirebaseDataSource) : SpotRepository {

    override suspend fun getNearbySpots(location: SpotLocation, radiusMeters: Double): Result<List<Spot>> = runCatching {
        firebaseDataSource.getNearbySpots(location.latitude, location.longitude, radiusMeters)
            .map { (id, dto) -> dto.toDomain(id) }
    }

    override fun observeNearbySpots(location: SpotLocation, radiusMeters: Double): Flow<List<Spot>> {
        return firebaseDataSource.observeNearbySpots(location.latitude, location.longitude, radiusMeters)
            .map { dtoMap -> dtoMap.map { (id, dto) -> dto.toDomain(id) } }
    }

    override suspend fun reportSpotReleased(spot: Spot): Result<Unit> = runCatching {
        firebaseDataSource.reportSpotReleased(spot.toDto())
    }
}
